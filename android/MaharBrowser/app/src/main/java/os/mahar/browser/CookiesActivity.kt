package os.mahar.browser

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Cookies page — one profile's cookies, with a big Upload button.
 *
 * Upload: pick any cookies file (Netscape cookies.txt, JSON, or a Mahar
 * export). Cookies go into THIS profile only.
 * Export: save this profile's cookies to Downloads (Netscape format).
 * Clear: wipe this profile's cookies (you will need to log in again).
 */
class CookiesActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var profile: BrowserProfile

    private val uploadLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val cookies = ProfileManager.parseCookiesText(text)
                if (cookies.isEmpty()) {
                    Toast.makeText(this, "No cookies found in this file", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                val n = ProfileManager.applyCookies(this, profile.id, cookies)
                markChanged()
                Toast.makeText(this, "$n cookies uploaded to ${profile.name}", Toast.LENGTH_SHORT).show()
                refresh()
            } catch (e: Exception) {
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    private val uploadNewLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val cookies = ProfileManager.parseCookiesText(text)
                if (cookies.isEmpty()) {
                    Toast.makeText(this, "No cookies found in this file", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                askNameAndCreate(cookies, displayNameOf(uri))
            } catch (e: Exception) {
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    private fun displayNameOf(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name.substringBeforeLast('.')
                }
            }
        } catch (_: Exception) { }
        return "Account"
    }

    private fun askNameAndCreate(cookies: List<ImportedCookie>, suggested: String) {
        val input = EditText(this).apply { setText(suggested) }
        AlertDialog.Builder(this)
            .setTitle("New Profile")
            .setMessage("Name this account's profile:")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { suggested }
                val created = ProfileManager.createProfile(this, name)
                ProfileManager.saveCookiesToFile(this, created.id, cookies)
                Toast.makeText(this,
                    "Profile '$name' created with ${cookies.size} cookies. Switch to it from Profiles.",
                    Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cookies)

        val profileId = intent.getStringExtra("profile_id")
        profile = ProfileManager.allProfiles(this).firstOrNull { it.id == profileId }
            ?: ProfileManager.current(this)

        container = findViewById(R.id.cookies_container)
        findViewById<TextView>(R.id.cookies_title).text = "🍪 Cookies — ${profile.name}"
        findViewById<ImageButton>(R.id.cookies_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.cookies_upload).setOnClickListener {
            uploadLauncher.launch("*/*")
        }
        findViewById<Button>(R.id.cookies_upload_new).setOnClickListener {
            uploadNewLauncher.launch("*/*")
        }
        findViewById<Button>(R.id.cookies_export).setOnClickListener {
            val domains = ProfileManager.domains(this, profile.id)
            val text = ProfileManager.exportNetscapeText(domains)
            val where = ProfileManager.exportToDownloads(this, profile.id, profile.name, text)
            Toast.makeText(this, "Saved to $where", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.cookies_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Cookies?")
                .setMessage("You will need to log in again on all sites in this profile.")
                .setPositiveButton("Clear") { _, _ ->
                    ProfileManager.clearCookies {
                        runOnUiThread {
                            markChanged()
                            Toast.makeText(this, "Cookies cleared", Toast.LENGTH_SHORT).show()
                            refresh()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun markChanged() {
        getSharedPreferences("mahar_browser_main", Context.MODE_PRIVATE)
            .edit().putBoolean("cookies_changed", true).apply()
    }

    private fun refresh() {
        container.removeAllViews()
        val cm = CookieManager.getInstance()
        val domains = ProfileManager.domains(this, profile.id).sorted()
        if (domains.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No cookies for this profile yet.\n\nTap UPLOAD COOKIES above, or browse and log in — cookies are saved automatically."
                textSize = 14f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            return
        }
        for (domain in domains) {
            val raw = try { cm.getCookie("https://$domain") } catch (_: Exception) { null }
            val count = if (raw.isNullOrBlank()) 0 else raw.split("; ").size
            val row = TextView(this).apply {
                text = "$domain  •  $count cookie" + (if (count == 1) "" else "s")
                textSize = 14f
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }
            container.addView(row)
        }
    }
}
