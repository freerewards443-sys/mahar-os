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
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Mahar Browser for Android 4.0.
 * Desktop v1.5 ke saare features: Mahar start page, Google default search
 * (engine choice ke saath), Mahar AI, Translate, My Links, private tabs,
 * theme choice, local profile, Mahar Jump game — plus tabs, bookmarks,
 * history, ad-block, dark mode, downloads, find-in-page, desktop-site
 * toggle aur IP Changer (BYO proxy).
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
    private val tabPrivate = mutableListOf<Boolean>()
    private var current = -1

    private var darkMode = false
    private var desktopSite = false
    private var mobileUa: String? = null
    private var searchEngine = "Google"
    private var themeMode = "system"   // light | dark | system
    private var profileName = ""
    private lateinit var currentProfile: BrowserProfile
    private lateinit var profileLabel: TextView

    // Cookies file upload: user koi cookies file pick kare, current profile me import ho
    private val cookieImportLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val cookies = ProfileManager.parseCookiesText(text)
                if (cookies.isEmpty()) {
                    Toast.makeText(this, "Is file me koi cookie nahi mili (cookies.txt ya JSON chahiye)",
                        Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                val n = ProfileManager.applyCookies(this, currentProfile.id, cookies)
                Toast.makeText(this, "✅ $n cookies import ho gayi — ${currentProfile.name} profile me",
                    Toast.LENGTH_LONG).show()
                currentView()?.reload()
            } catch (e: Exception) {
                Toast.makeText(this, "Import fail: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    companion object {
        const val HOME = "https://mahar.local/home.html"
        const val GAME = "https://mahar.local/game.html"
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        val SEARCH_ENGINES = linkedMapOf(
            "Google" to "https://www.google.com/search?q={}",
            "DuckDuckGo" to "https://duckduckgo.com/?q={}",
            "Bing" to "https://www.bing.com/search?q={}"
        )
        const val MAHAR_AI_URL = "https://text.pollinations.ai/"
        val MY_LINKS = listOf(
            "Mahar OS (Latest ISO)" to
                "https://github.com/freerewards443-sys/mahar-os/releases",
            "Mahar Browser (Releases)" to
                "https://github.com/freerewards443-sys/mahar-os/releases",
            "Deals Pakistan" to "https://dealspakistan.blogspot.com",
            "Slow Edits (YouTube)" to "https://www.youtube.com/@SlowEdits_34"
        )
    }

    private fun searchTemplate(): String =
        SEARCH_ENGINES[searchEngine] ?: SEARCH_ENGINES.getValue("Google")

    private fun effectiveDark(): Boolean = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Internal Mahar pages, served locally by MaharWebViewClient. */
    fun buildInternalPage(asset: String): ByteArray {
        val raw = assets.open(asset).bufferedReader().readText()
        val engine = searchTemplate()
        return raw
            .replace("{{THEME_CLASS}}", if (effectiveDark()) "dark" else "")
            .replace("{{ENGINE_URL}}", engine)
            .replace("{{ENGINE_NAME}}", searchEngine)
            .replace("{{PROFILE_NAME}}", profileName.ifEmpty { "Mahar User" })
            .toByteArray(Charsets.UTF_8)
    }

    // ---------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getPreferences(Context.MODE_PRIVATE)
        // Multi-profile: pehla profile v4 wale display-name se (agar set tha)
        val savedDisplayName = prefs.getString("profile_name", "") ?: ""
        val existing = ProfileManager.allProfiles(this)
        if (existing.size == 1 && existing[0].name == "Main" && savedDisplayName.isNotBlank()) {
            ProfileManager.renameProfile(this, existing[0].id, savedDisplayName)
        }
        currentProfile = ProfileManager.current(this)
        db = BookmarksDb(this, currentProfile.id)

        darkMode = prefs.getBoolean("dark_mode", false)
        AdBlocker.enabled = prefs.getBoolean("block_ads", true)
        desktopSite = prefs.getBoolean("desktop_site", false)
        searchEngine = prefs.getString("search_engine", "Google") ?: "Google"
        if (!SEARCH_ENGINES.containsKey(searchEngine)) searchEngine = "Google"
        themeMode = prefs.getString("theme_mode", null)
            ?: if (darkMode) "dark" else "system"
        profileName = prefs.getString("profile_name", "") ?: ""
        if (profileName.isBlank()) profileName = currentProfile.name

        container = findViewById(R.id.web_container)
        addressBar = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        btnTabs = findViewById(R.id.btn_tabs)
        btnBookmark = findViewById(R.id.btn_bookmark)
        profileLabel = findViewById(R.id.profile_label)
        updateProfileBar()
        findViewById<View>(R.id.profile_bar).setOnClickListener { showProfilesDialog() }

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
        ProxyManager.loadState(this)
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

    fun newTab(url: String) = newTab(url, false)

    fun newTab(url: String, private: Boolean) {
        val wv = makeWebView()
        tabs.add(wv)
        tabPrivate.add(private)
        switchTo(tabs.size - 1)
        wv.loadUrl(url)
        updateTabsButton()
        if (private) Toast.makeText(
            this, "🔒 Private tab — is tab ki history save nahi hogi",
            Toast.LENGTH_SHORT).show()
    }

    fun isPrivate(view: WebView): Boolean {
        val i = tabs.indexOf(view)
        return i in tabPrivate.indices && tabPrivate[i]
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val wv = tabs.removeAt(index)
        if (index in tabPrivate.indices) tabPrivate.removeAt(index)
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
            return searchTemplate().replace("{}", Uri.encode(t))
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
        // Private tabs aur internal Mahar pages history me save nahi hote
        if (!isPrivate(view) && !url.startsWith("https://mahar.local/")) {
            db.addHistory(view.title ?: "", url)
            // Cookie snapshot ke liye domain track karo (profile switch ke kaam aayega)
            try {
                ProfileManager.trackDomain(this, currentProfile.id, Uri.parse(url).host)
            } catch (_: Exception) { }
        }
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
        if (url == HOME || url == GAME || url == "about:blank") return
        if (isPrivate(v)) {
            Toast.makeText(this, "🔒 Private tab me bookmark save nahi hota",
                Toast.LENGTH_SHORT).show()
            return
        }
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
        popup.menu.findItem(R.id.menu_block_ads).isChecked = AdBlocker.enabled
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_tab -> { newTab(HOME); true }
                R.id.menu_private_tab -> { newTab(HOME, true); true }
                R.id.menu_home -> { currentView()?.loadUrl(HOME); true }
                R.id.menu_close_tab -> { closeTab(current); true }
                R.id.menu_bookmarks -> { showBookmarks(); true }
                R.id.menu_history -> { showHistory(); true }
                R.id.menu_find -> { showFindDialog(); true }
                R.id.menu_share -> { shareCurrent(); true }
                R.id.menu_mahar_ai -> {
                    startActivity(Intent(this, MaharAiActivity::class.java)); true }
                R.id.menu_profiles -> { showProfilesDialog(); true }
                R.id.menu_cookies -> { showCookiesDialog(); true }
                R.id.menu_translate -> { translateCurrent(); true }
                R.id.menu_my_links -> { showMyLinks(); true }
                R.id.menu_game -> { currentView()?.loadUrl(GAME); true }
                R.id.menu_search_engine -> { showSearchEngineDialog(); true }
                R.id.menu_theme -> { showThemeDialog(); true }
                R.id.menu_profile -> { showProfileDialog(); true }
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
                R.id.menu_block_ads -> {
                    AdBlocker.enabled = !AdBlocker.enabled
                    prefs.edit().putBoolean("block_ads", AdBlocker.enabled).apply()
                    currentView()?.reload()
                    true
                }
                R.id.menu_ip_changer -> { showProxyDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun applyAppNightMode() {
        AppCompatDelegate.setDefaultNightMode(when (themeMode) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        darkMode = effectiveDark()
    }

    private fun applyWebDarkMode(wv: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, effectiveDark())
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

    // ------------------------------------------------- v4: desktop parity
    private fun translateCurrent() {
        val v = currentView() ?: return
        val url = v.url ?: return
        if (url.startsWith("https://mahar.local/") || url == "about:blank") {
            Toast.makeText(this, "Pehle koi website kholo, phir Translate dabao",
                Toast.LENGTH_SHORT).show()
            return
        }
        v.loadUrl("https://translate.google.com/translate?sl=auto&tl=ur&u=" +
            Uri.encode(url))
    }

    private fun showMyLinks() {
        val names = MY_LINKS.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.my_links)
            .setItems(names) { _, which ->
                currentView()?.loadUrl(MY_LINKS[which].second)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSearchEngineDialog() {
        val names = SEARCH_ENGINES.keys.toTypedArray()
        val selected = names.indexOf(searchEngine).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.search_engine)
            .setSingleChoiceItems(names, selected) { d, which ->
                searchEngine = names[which]
                prefs.edit().putString("search_engine", searchEngine).apply()
                Toast.makeText(this, "Search: $searchEngine", Toast.LENGTH_SHORT).show()
                if (currentView()?.url == HOME) currentView()?.loadUrl(HOME)
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val labels = arrayOf("Light", "Dark", "System (phone jaisa)")
        val values = arrayOf("light", "dark", "system")
        val selected = values.indexOf(themeMode).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(labels, selected) { d, which ->
                themeMode = values[which]
                prefs.edit().putString("theme_mode", themeMode)
                    .putBoolean("dark_mode", themeMode == "dark").apply()
                applyAppNightMode()
                tabs.forEach { applyWebDarkMode(it) }
                if (currentView()?.url == HOME) currentView()?.loadUrl(HOME)
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showProfileDialog() {
        val input = EditText(this).apply {
            hint = "Is profile ka naam likho (sirf is phone par save hoga)"
            setText(currentProfile.name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    ProfileManager.renameProfile(this, currentProfile.id, name)
                    currentProfile = BrowserProfile(currentProfile.id, name)
                    profileName = name
                    prefs.edit().putString("profile_name", name).apply()
                    updateProfileBar()
                    Toast.makeText(this, "Profile save ho gaya", Toast.LENGTH_SHORT).show()
                    if (currentView()?.url == HOME) currentView()?.loadUrl(HOME)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------- v5: multi-profile
    private fun updateProfileBar() {
        profileLabel.text = "👤 ${currentProfile.name}  •  Profile badalne ke liye tap karo"
    }

    private fun allTrackedDomains(): Set<String> {
        val out = ProfileManager.domains(this, currentProfile.id).toMutableSet()
        try {
            db.allHistory().forEach { b ->
                Uri.parse(b.url).host?.let { out += it }
            }
        } catch (_: Exception) { }
        return out
    }

    private fun showProfilesDialog() {
        val profiles = ProfileManager.allProfiles(this)
        val names = profiles.map {
            (if (it.id == currentProfile.id) "✓ " else "") + it.name
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.profiles)
            .setMessage("Har profile ka apna account hota hai — alag cookies, alag bookmarks, alag history.")
            .setItems(names) { _, which ->
                val target = profiles[which]
                if (target.id != currentProfile.id) switchProfile(target)
            }
            .setPositiveButton(R.string.new_profile) { _, _ -> showNewProfileDialog() }
            .setNeutralButton(R.string.delete_profile) { _, _ -> showDeleteProfileDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewProfileDialog() {
        val input = EditText(this).apply {
            hint = "Naye profile ka naam — jaise 'Facebook 2' ya 'Office'"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_profile)
            .setView(input)
            .setPositiveButton("Banayo") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Naam likho", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val created = ProfileManager.createProfile(this, name)
                Toast.makeText(this, "Profile '$name' ban gaya — ab switch karo",
                    Toast.LENGTH_SHORT).show()
                switchProfile(created)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun showDeleteProfileDialog() {
        val profiles = ProfileManager.allProfiles(this)
        val names = profiles.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setItems(names) { _, which ->
                val target = profiles[which]
                if (target.id == currentProfile.id) {
                    Toast.makeText(this, "Pehle kisi doosri profile par switch karo",
                        Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                if (ProfileManager.deleteProfile(this, target.id)) {
                    Toast.makeText(this, "'${target.name}' delete ho gaya", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Aakhri profile delete nahi ho sakti",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun switchProfile(target: BrowserProfile) {
        // 1. Current profile ki cookies save karo
        ProfileManager.snapshotCookies(this, currentProfile.id, allTrackedDomains())
        // 2. Saare tabs band karo (purani profile ka session screen par na rahe)
        tabs.forEach { it.destroy() }
        tabs.clear()
        tabPrivate.clear()
        current = -1
        try { db.close() } catch (_: Exception) { }
        // 3. Cookies clear, phir nayi profile ki cookies load karo
        ProfileManager.clearCookies {
            runOnUiThread {
                ProfileManager.setCurrent(this, target.id)
                currentProfile = target
                profileName = target.name
                db = BookmarksDb(this, target.id)
                ProfileManager.restoreCookies(this, target.id)
                updateProfileBar()
                newTab(HOME)
                updateTabsButton()
                Toast.makeText(this, "Profile: ${target.name} — is profile ke accounts alag hain",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------- v5: cookies
    private fun showCookiesDialog() {
        val options = arrayOf(
            "📤 Cookies Import (file upload karo)",
            "📥 Cookies Export (Downloads me save karo)",
            "🗑 Is profile ki cookies clear karo"
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.cookies)
            .setMessage("Profile: ${currentProfile.name} — cookies sirf is profile ki hongi.")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cookieImportLauncher.launch("*/*")
                    1 -> {
                        val text = ProfileManager.exportNetscapeText(allTrackedDomains())
                        val where = ProfileManager.exportToDownloads(
                            this, currentProfile.id, currentProfile.name, text)
                        Toast.makeText(this, "Cookies export: $where", Toast.LENGTH_LONG).show()
                    }
                    2 -> {
                        ProfileManager.clearCookies {
                            runOnUiThread {
                                Toast.makeText(this, "Cookies clear — dobara login karna parega",
                                    Toast.LENGTH_SHORT).show()
                                currentView()?.loadUrl(HOME)
                            }
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                val base = (wv.title ?: wv.url ?: "New Tab").take(42)
                text = if (i in tabPrivate.indices && tabPrivate[i]) "🔒 $base" else base
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

    // ---------------------------------------------------------- IP changer
    private fun rotateLabel(): String {
        val m = ProxyManager.getRotateMinutes(this)
        return if (m <= 0) "Auto-change: Off" else "Auto-change: ${m}min"
    }

    private fun showProxyDialog() {
        val ctx = this
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        root.addView(TextView(ctx).apply {
            text = getString(R.string.proxy_info)
            textSize = 13f
            setPadding(0, 0, 0, 8)
        })

        val toggle = androidx.appcompat.widget.SwitchCompat(ctx).apply {
            text = getString(R.string.ip_changer)
            isChecked = ProxyManager.enabled
        }
        root.addView(toggle)

        val status = TextView(ctx).apply {
            textSize = 13f
            setPadding(0, 8, 0, 8)
            text = ProxyManager.statusText()
        }
        root.addView(status)

        val listView = ListView(ctx)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (220 * density).toInt()
            )
        )

        fun refreshList() {
            val proxies = ProxyManager.getProxies(ctx)
            val adapter = object : ArrayAdapter<String>(
                ctx,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                proxies.map { it.label() }
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val v = super.getView(position, convertView, parent)
                    val p = proxies[position]
                    v.findViewById<TextView>(android.R.id.text1).text =
                        (if (p == ProxyManager.active && ProxyManager.enabled) "● " else "") + p.country
                    v.findViewById<TextView>(android.R.id.text2).text =
                        "${p.host}:${p.port}"
                    return v
                }
            }
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, pos, _ ->
                val p = proxies[pos]
                ProxyManager.applyProxy(p)
                currentView()?.reload()
                status.text = ProxyManager.statusText()
                refreshList()
                Toast.makeText(ctx, "IP: ${p.label()}", Toast.LENGTH_SHORT).show()
            }
            listView.setOnItemLongClickListener { _, _, pos, _ ->
                val p = proxies[pos]
                AlertDialog.Builder(ctx)
                    .setMessage("Delete ${p.label()}?")
                    .setPositiveButton("Delete") { _, _ ->
                        val l = ProxyManager.getProxies(ctx).toMutableList()
                        l.removeAt(pos)
                        ProxyManager.saveProxies(ctx, l)
                        if (ProxyManager.active == p) {
                            ProxyManager.applyProxy(l.firstOrNull())
                            currentView()?.reload()
                        }
                        status.text = ProxyManager.statusText()
                        refreshList()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }
        refreshList()

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }
        val addBtn = Button(ctx).apply { text = getString(R.string.add_proxy) }
        val rotateBtn = Button(ctx).apply { text = rotateLabel() }
        addBtn.setOnClickListener {
            showAddProxyDialog {
                refreshList()
                status.text = ProxyManager.statusText()
            }
        }
        rotateBtn.setOnClickListener {
            showRotateDialog { rotateBtn.text = rotateLabel() }
        }
        btnRow.addView(addBtn)
        btnRow.addView(rotateBtn)
        root.addView(btnRow)

        toggle.setOnCheckedChangeListener { _, on ->
            if (!ProxyManager.setEnabled(ctx, on)) {
                toggle.isChecked = false
                Toast.makeText(ctx, R.string.add_proxy_first, Toast.LENGTH_SHORT).show()
            } else {
                currentView()?.reload()
            }
            status.text = ProxyManager.statusText()
            refreshList()
        }

        AlertDialog.Builder(ctx)
            .setTitle(R.string.ip_changer)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAddProxyDialog(onDone: () -> Unit) {
        val ctx = this
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }
        val country = EditText(ctx).apply { hint = "Country (e.g. Germany)" }
        val host = EditText(ctx).apply { hint = "Host (e.g. 1.2.3.4)" }
        val port = EditText(ctx).apply {
            hint = "Port (e.g. 8080)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(country)
        layout.addView(host)
        layout.addView(port)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.add_proxy)
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val h = host.text.toString().trim()
                val p = port.text.toString().trim().toIntOrNull() ?: 0
                if (h.isEmpty() || p <= 0) {
                    Toast.makeText(ctx, "Host aur port sahi likho", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val list = ProxyManager.getProxies(ctx).toMutableList()
                list += ProxyServer(
                    country.text.toString().trim().ifEmpty { "Proxy" }, h, p
                )
                ProxyManager.saveProxies(ctx, list)
                Toast.makeText(ctx, "Proxy saved", Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRotateDialog(onDone: () -> Unit) {
        val options = arrayOf("Off", "5 minutes", "15 minutes", "30 minutes", "60 minutes")
        val values = intArrayOf(0, 5, 15, 30, 60)
        val current = values.indexOf(ProxyManager.getRotateMinutes(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Auto-change IP")
            .setSingleChoiceItems(options, current) { d, which ->
                ProxyManager.setRotateMinutes(this, values[which])
                onDone()
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
