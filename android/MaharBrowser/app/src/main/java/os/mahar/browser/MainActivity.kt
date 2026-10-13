package os.mahar.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Mahar Browser for Android 1.0.
 * Tabbed WebView browser with bookmarks, history, ad-block, dark mode,
 * downloads, find-in-page and desktop-site toggle.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnTabs: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var prefs: SharedPreferences
    private lateinit var db: BookmarksDb

    private val tabs = mutableListOf<WebView>()
    private var current = -1

    private var darkMode = false
    private var desktopSite = false
    private var mobileUa: String? = null

    companion object {
        const val HOME = "https://duckduckgo.com"
        const val SEARCH = "https://duckduckgo.com/?q="
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    // ---------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getPreferences(Context.MODE_PRIVATE)
        db = BookmarksDb(this)

        darkMode = prefs.getBoolean("dark_mode", false)
        AdBlocker.enabled = prefs.getBoolean("block_ads", true)
        desktopSite = prefs.getBoolean("desktop_site", false)

        container = findViewById(R.id.web_container)
        addressBar = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        btnTabs = findViewById(R.id.btn_tabs)
        btnBookmark = findViewById(R.id.btn_bookmark)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { currentView()?.goBack() }
        findViewById<ImageButton>(R.id.btn_forward).setOnClickListener { currentView()?.goForward() }
        findViewById<ImageButton>(R.id.btn_reload).setOnClickListener { currentView()?.reload() }
        btnBookmark.setOnClickListener { toggleBookmark() }
        btnTabs.setOnClickListener { showTabSwitcher() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu(it) }

        addressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromAddressBar()
                true
            } else false
        }
        addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) addressBar.selectAll()
        }

        applyAppNightMode()
        newTab(HOME)
        updateTabsButton()
    }

    override fun onBackPressed() {
        val v = currentView()
        if (v != null && v.canGoBack()) v.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        tabs.forEach { it.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    // ---------------------------------------------------------- tabs
    private fun makeWebView(): WebView {
        val wv = WebView(this)
        val s: WebSettings = wv.settings
        wv.webViewClient = MaharWebViewClient(this)
        wv.webChromeClient = MaharWebChromeClient(this)
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.databaseEnabled = true
        mobileUa = s.userAgentString
        if (desktopSite) s.userAgentString = DESKTOP_UA
        applyWebDarkMode(wv)
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
        return wv
    }

    fun newTab(url: String) {
        val wv = makeWebView()
        tabs.add(wv)
        switchTo(tabs.size - 1)
        wv.loadUrl(url)
        updateTabsButton()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val wv = tabs.removeAt(index)
        if (index == current) container.removeView(wv)
        wv.destroy()
        if (tabs.isEmpty()) {
            newTab(HOME)
        } else {
            if (current >= tabs.size) current = tabs.size - 1
            showCurrent()
        }
        updateTabsButton()
    }

    private fun switchTo(index: Int) {
        if (index !in tabs.indices) return
        current = index
        showCurrent()
    }

    private fun showCurrent() {
        container.removeAllViews()
        val wv = currentView() ?: return
        container.addView(wv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        refreshChrome(wv)
    }

    private fun currentView(): WebView? =
        if (current in tabs.indices) tabs[current] else null

    private fun updateTabsButton() {
        btnTabs.setImageDrawable(null)
        // draw the count as text on the button via content description + text overlay:
        // simplest reliable approach: use a TextView-like badge by setting text on the button
        // (ImageButton has no text, so overlay via tooltip + a redraw trick)
        btnTabs.contentDescription = getString(R.string.tab_count, tabs.size)
        // draw count manually
        btnTabs.post {
            val b = android.graphics.Bitmap.createBitmap(
                btnTabs.width.coerceAtLeast(1), btnTabs.height.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            val p = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 34f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            // tab outline
            val rp = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 4f
            }
            val w = b.width.toFloat(); val h = b.height.toFloat()
            c.drawRoundRect(8f, 4f, w - 8, h - 4, 8f, 8f, rp)
            c.drawText(tabs.size.toString(), w / 2, h / 2 + 12, p)
            btnTabs.setImageBitmap(b)
        }
    }

    // ---------------------------------------------------------- loading
    private fun smartUrl(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return HOME
        if (t.contains(" ") || !t.contains(".")) {
            return SEARCH + Uri.encode(t)
        }
        return if (t.startsWith("http://") || t.startsWith("https://")) t
               else "https://$t"
    }

    private fun loadFromAddressBar() {
        val v = currentView() ?: return
        v.loadUrl(smartUrl(addressBar.text.toString()))
        hideKeyboard()
        addressBar.clearFocus()
    }

    fun onPageStarted(view: WebView, url: String) {
        if (view == currentView()) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            if (!addressBar.hasFocus()) addressBar.setText(url)
        }
    }

    fun onProgressChanged(view: WebView, progress: Int) {
        if (view == currentView()) {
            progressBar.progress = progress
            if (progress >= 100) progressBar.visibility = View.GONE
        }
    }

    fun onPageFinished(view: WebView, url: String) {
        if (view == currentView()) {
            progressBar.visibility = View.GONE
            refreshChrome(view)
        }
        db.addHistory(view.title ?: "", url)
    }

    fun onTitleReceived(view: WebView, title: String?) {
        if (view == currentView() && !title.isNullOrBlank()) {
            // title shown in tab switcher; address bar keeps URL
        }
    }

    private fun refreshChrome(view: WebView) {
        val url = view.url ?: ""
        if (!addressBar.hasFocus()) addressBar.setText(url)
        updateBookmarkStar(url)
        findViewById<ImageButton>(R.id.btn_back).isEnabled = view.canGoBack()
        findViewById<ImageButton>(R.id.btn_forward).isEnabled = view.canGoForward()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(addressBar.windowToken, 0)
    }

    // ---------------------------------------------------------- bookmarks
    private fun toggleBookmark() {
        val v = currentView() ?: return
        val url = v.url ?: return
        if (url == HOME || url == "about:blank") return
        if (db.isBookmarked(url)) {
            db.removeBookmark(url)
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
        } else {
            db.addBookmark(v.title ?: url, url)
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        }
        updateBookmarkStar(url)
    }

    private fun updateBookmarkStar(url: String) {
        btnBookmark.setImageResource(
            if (db.isBookmarked(url)) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off)
    }

    // ---------------------------------------------------------- downloads
    private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        try {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription(getString(R.string.app_name))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            req.addRequestHeader("User-Agent", userAgent)
            req.addRequestHeader("Cookie", android.webkit.CookieManager.getInstance().getCookie(url) ?: "")
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Downloading: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------- menu
    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.menu.findItem(R.id.menu_desktop_site).isChecked = desktopSite
        popup.menu.findItem(R.id.menu_dark_mode).isChecked = darkMode
        popup.menu.findItem(R.id.menu_block_ads).isChecked = AdBlocker.enabled
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_tab -> { newTab(HOME); true }
                R.id.menu_close_tab -> { closeTab(current); true }
                R.id.menu_bookmarks -> { showBookmarks(); true }
                R.id.menu_history -> { showHistory(); true }
                R.id.menu_find -> { showFindDialog(); true }
                R.id.menu_share -> { shareCurrent(); true }
                R.id.menu_desktop_site -> {
                    desktopSite = !desktopSite
                    prefs.edit().putBoolean("desktop_site", desktopSite).apply()
                    tabs.forEach {
                        it.settings.userAgentString =
                            if (desktopSite) DESKTOP_UA else mobileUa
                        it.reload()
                    }
                    true
                }
                R.id.menu_dark_mode -> {
                    darkMode = !darkMode
                    prefs.edit().putBoolean("dark_mode", darkMode).apply()
                    applyAppNightMode()
                    tabs.forEach { applyWebDarkMode(it) }
                    true
                }
                R.id.menu_block_ads -> {
                    AdBlocker.enabled = !AdBlocker.enabled
                    prefs.edit().putBoolean("block_ads", AdBlocker.enabled).apply()
                    currentView()?.reload()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun applyAppNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun applyWebDarkMode(wv: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, darkMode)
        }
    }

    private fun shareCurrent() {
        val v = currentView() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, v.url ?: "")
        }
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    private fun showFindDialog() {
        val v = currentView() ?: return
        val input = EditText(this).apply { hint = getString(R.string.find_in_page) }
        AlertDialog.Builder(this)
            .setTitle(R.string.find_in_page)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                v.findAllAsync(input.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    // ---------------------------------------------------------- dialogs
    private fun showTabSwitcher() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        lateinit var dialog: AlertDialog
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabs.forEachIndexed { i, wv ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 12)
            }
            val title = TextView(this).apply {
                text = (wv.title ?: wv.url ?: "New Tab").take(42)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            if (i == current) title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            val close = TextView(this).apply {
                text = "  ✕  "
                textSize = 18f
            }
            row.addView(title)
            row.addView(close)
            row.setOnClickListener { switchTo(i); dialog.dismiss() }
            close.setOnClickListener { closeTab(i); dialog.dismiss(); showTabSwitcher() }
            list.addView(row)
        }
        scroll.addView(list)
        layout.addView(scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val dialogBuilt = AlertDialog.Builder(this)
            .setTitle(getString(R.string.tab_count, tabs.size))
            .setView(layout)
            .setPositiveButton(R.string.new_tab) { _, _ -> newTab(HOME) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog = dialogBuilt
        dialog.show()
    }

    private fun showBookmarks() {
        val items = db.allBookmarks()
        showLinkList(getString(R.string.bookmarks),
            if (items.isEmpty()) getString(R.string.no_bookmarks) else null,
            items,
            onOpen = { b -> currentView()?.loadUrl(b.url) },
            onDelete = { b -> db.removeBookmark(b.url); currentView()?.let { updateBookmarkStar(it.url ?: "") } })
    }

    private fun showHistory() {
        val items = db.allHistory()
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setTitle(R.string.history)
                .setMessage(R.string.no_history)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val names = items.map { it.title.take(50) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setItems(names) { _, which -> currentView()?.loadUrl(items[which].url) }
            .setNeutralButton(R.string.clear_history) { _, _ ->
                db.clearHistory()
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLinkList(
        title: String, emptyMsg: String?,
        items: List<Bookmark>,
        onOpen: (Bookmark) -> Unit,
        onDelete: ((Bookmark) -> Unit)?
    ) {
        if (items.isEmpty() && emptyMsg != null) {
            AlertDialog.Builder(this).setTitle(title).setMessage(emptyMsg)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val names = items.map { it.title.take(50) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names) { _, which -> onOpen(items[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        // note: long-press delete omitted for simplicity in v1; bookmarks
        // can be removed via the ☆ toggle on the page itself
    }
}
