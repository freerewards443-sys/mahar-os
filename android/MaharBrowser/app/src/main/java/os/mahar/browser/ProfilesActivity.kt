package os.mahar.browser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Profiles page — every profile on one screen.
 *
 * Each row: name, cookie-site count, bookmark count, and buttons:
 * Switch (use this profile), Open Site (open the website typed above
 * in THIS profile — same site, different account), Cookies (that
 * profile's cookie page), Edit (rename), Delete.
 *
 * Switching is done by returning the profile id to MainActivity, which
 * saves the old profile's cookies, clears them, and loads the new
 * profile's cookies before opening any page.
 */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var siteInput: EditText
    private var currentUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)

        container = findViewById(R.id.profiles_container)
        siteInput = findViewById(R.id.profiles_site)
        currentUrl = intent.getStringExtra("current_url") ?: ""
        if (currentUrl.isNotBlank() && !currentUrl.startsWith("https://mahar.local")) {
            siteInput.setText(currentUrl)
        }

        findViewById<ImageButton>(R.id.profiles_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.profiles_new).setOnClickListener { showNewDialog() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun refresh() {
        container.removeAllViews()
        val current = ProfileManager.current(this)
        for (profile in ProfileManager.allProfiles(this)) {
            val isCurrent = profile.id == current.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val title = TextView(this).apply {
                text = (if (isCurrent) "✓ " else "") + profile.name +
                    (if (isCurrent) "  (current)" else "")
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            var bookmarks = 0
            try {
                val db = BookmarksDb(this, profile.id)
                bookmarks = db.allBookmarks().size
                db.close()
            } catch (_: Exception) { }
            val sites = ProfileManager.domains(this, profile.id).size
            val sub = TextView(this).apply {
                text = "$sites sites • $bookmarks bookmarks"
                textSize = 13f
            }
            card.addView(title)
            card.addView(sub)

            fun btn(label: String, action: () -> Unit): Button {
                return Button(this).apply {
                    text = label
                    textSize = 12f
                    setOnClickListener { action() }
                }
            }
            val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(btn("Switch") { returnProfile(profile.id, null) })
            row1.addView(btn("Open Site") { openSiteIn(profile) })
            row1.addView(btn("Cookies") {
                val i = Intent(this, CookiesActivity::class.java)
                i.putExtra("profile_id", profile.id)
                startActivity(i)
            })
            val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row2.addView(btn("Edit") { showEditDialog(profile) })
            row2.addView(btn("Delete") { confirmDelete(profile) })
            card.addView(row1)
            card.addView(row2)
            container.addView(card)
        }
    }

    private fun returnProfile(id: String, openUrl: String?) {
        val data = Intent().apply {
            putExtra("profile_id", id)
            if (!openUrl.isNullOrBlank()) putExtra("open_url", openUrl)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun openSiteIn(profile: BrowserProfile) {
        var site = siteInput.text.toString().trim()
        if (site.isEmpty()) {
            Toast.makeText(this, "Enter a website first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!site.startsWith("http://") && !site.startsWith("https://")) {
            site = "https://$site"
        }
        returnProfile(profile.id, site)
    }

    private fun showNewDialog() {
        val input = EditText(this).apply {
            hint = "New profile name — e.g. Facebook 2"
        }
        AlertDialog.Builder(this)
            .setTitle("New Profile")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ProfileManager.createProfile(this, name)
                Toast.makeText(this, "Profile created", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun showEditDialog(profile: BrowserProfile) {
        val input = EditText(this).apply { setText(profile.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename Profile")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    ProfileManager.renameProfile(this, profile.id, name)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(profile: BrowserProfile) {
        val current = ProfileManager.current(this)
        if (profile.id == current.id) {
            Toast.makeText(this, "Switch to another profile first", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete Profile?")
            .setMessage("Delete '${profile.name}' and its saved cookies?")
            .setPositiveButton("Delete") { _, _ ->
                if (ProfileManager.deleteProfile(this, profile.id)) {
                    Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
