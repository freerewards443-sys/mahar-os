package os.mahar.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates.
 *
 * How it works: the app asks GitHub for the newest "browser-android-*"
 * release, reads the versionCode from that release's notes, and compares
 * it with the installed version. If a newer build exists it downloads
 * the APK to Downloads and opens the installer — no uninstall, no
 * re-download from a browser needed.
 *
 * This only works seamlessly from v6 onward: v6 is the first build
 * signed with the permanent Mahar release key. Earlier debug-signed
 * builds (v5 and below) use a different, throwaway signature, so moving
 * to v6 needs one last uninstall + install. After that, updates install
 * over the existing app.
 */
object UpdateManager {
    private const val API =
        "https://api.github.com/repos/freerewards443-sys/mahar-os/releases?per_page=30"
    private const val PREF = "mahar_browser_main"
    private const val APK_NAME = "MaharBrowser-update.apk"

    data class UpdateInfo(val versionCode: Int, val tag: String, val apkUrl: String)

    private fun flagPrefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun check(activity: AppCompatActivity, manual: Boolean) {
        Thread {
            var info: UpdateInfo? = null
            var error = false
            try {
                val conn = URL(API).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "MaharBrowser")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val rel = arr.getJSONObject(i)
                    val tag = rel.optString("tag_name", "")
                    if (!tag.startsWith("browser-android-")) continue
                    val notes = rel.optString("body", "")
                    val m = Regex("versionCode:\\s*(\\d+)").find(notes)
                    val code = m?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    val assets = rel.optJSONArray("assets") ?: continue
                    var url: String? = null
                    for (j in 0 until assets.length()) {
                        val a = assets.getJSONObject(j)
                        if (a.optString("name") == "MaharBrowser.apk") {
                            url = a.optString("browser_download_url", "")
                        }
                    }
                    if (!url.isNullOrBlank()) {
                        info = UpdateInfo(code, tag, url)
                    }
                    break
                }
            } catch (_: Exception) {
                error = true
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                flagPrefs(activity).edit()
                    .putLong("last_update_check", System.currentTimeMillis()).apply()
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                    val notified = flagPrefs(activity).getString("notified_tag", "")
                    if (manual || notified != info.tag) {
                        flagPrefs(activity).edit().putString("notified_tag", info.tag).apply()
                        showUpdateDialog(activity, info)
                    }
                } else if (manual) {
                    Toast.makeText(
                        activity,
                        if (error) "Update check failed. Check your connection."
                        else "You're on the latest version.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    /** Silent check on launch, at most once a day. */
    fun autoCheck(activity: AppCompatActivity) {
        val last = flagPrefs(activity).getLong("last_update_check", 0)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return
        check(activity, manual = false)
    }

    private fun showUpdateDialog(activity: AppCompatActivity, info: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage("A new Mahar Browser version is ready. Download and install it over this app — your profiles and cookies stay.")
            .setPositiveButton("Download") { _, _ -> download(activity, info) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun download(ctx: Context, info: UpdateInfo) {
        try {
            val req = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("Mahar Browser update")
                .setDescription("Downloading new version…")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_NAME)
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            flagPrefs(ctx).edit().putLong("update_download_id", id).apply()
            Toast.makeText(ctx, "Downloading update…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Receiver for MainActivity: when the update APK finishes, open the installer. */
    fun downloadReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != flagPrefs(ctx).getLong("update_download_id", -1)) return
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APK_NAME)
            if (!file.exists()) return
            try {
                val uri = FileProvider.getUriForFile(
                    ctx, "os.mahar.browser.fileprovider", file)
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(install)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Open Downloads and tap $APK_NAME to install.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    fun receiverFilter(): IntentFilter =
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
}
