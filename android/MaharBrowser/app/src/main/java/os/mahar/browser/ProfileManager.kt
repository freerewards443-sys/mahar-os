package os.mahar.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Multi-profile system + cookies upload/export.
 *
 * Har profile ka apna account hota hai:
 *  - Apni cookies (is liye ek hi site par 2 alag accounts chal sakte hain,
 *    jaise Facebook account A ek profile me, account B doosri me)
 *  - Apne bookmarks aur apni history (alag database file per profile)
 *
 * Profile switch karte waqt current profile ki cookies snapshot me save
 * hoti hain, phir sab cookies clear karke nayi profile ki cookies load
 * hoti hain. Is liye profiles ke sessions aapas me mix nahi hote.
 *
 * Cookies upload: user koi cookies file (Netscape cookies.txt, JSON, ya
 * Mahar snapshot format) pick karke current profile me import kar sakta
 * hai. Export: current profile ki cookies Downloads me save hoti hain.
 */
data class BrowserProfile(val id: String, val name: String)

data class ImportedCookie(val domain: String, val name: String, val value: String)

object ProfileManager {
    private const val PREF = "profile_prefs"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------------------------------------------------------- profiles
    fun allProfiles(ctx: Context): MutableList<BrowserProfile> {
        val out = mutableListOf<BrowserProfile>()
        try {
            val arr = JSONArray(prefs(ctx).getString("profiles", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += BrowserProfile(o.getString("id"), o.getString("name"))
            }
        } catch (_: Exception) { }
        if (out.isEmpty()) {
            out += BrowserProfile("main", "Main")
            saveProfiles(ctx, out)
        }
        return out
    }

    fun saveProfiles(ctx: Context, list: List<BrowserProfile>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply { put("id", it.id); put("name", it.name) })
        }
        prefs(ctx).edit().putString("profiles", arr.toString()).apply()
    }

    fun current(ctx: Context): BrowserProfile {
        val list = allProfiles(ctx)
        val id = prefs(ctx).getString("current_profile", null)
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    fun setCurrent(ctx: Context, id: String) {
        prefs(ctx).edit().putString("current_profile", id).apply()
    }

    fun createProfile(ctx: Context, name: String): BrowserProfile {
        val list = allProfiles(ctx)
        val p = BrowserProfile("p" + System.currentTimeMillis(), name.ifBlank { "Profile" })
        list += p
        saveProfiles(ctx, list)
        return p
    }

    /** Returns false if it was the last profile (cannot delete the only one). */
    fun deleteProfile(ctx: Context, id: String): Boolean {
        val list = allProfiles(ctx)
        if (list.size <= 1) return false
        list.removeAll { it.id == id }
        saveProfiles(ctx, list)
        try { cookieFile(ctx, id).delete() } catch (_: Exception) { }
        prefs(ctx).edit().remove("domains_$id").apply()
        return true
    }

    fun renameProfile(ctx: Context, id: String, newName: String) {
        val list = allProfiles(ctx)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = BrowserProfile(id, newName.ifBlank { "Profile" })
            saveProfiles(ctx, list)
        }
    }

    // ---------------------------------------------------------- domains
    fun domains(ctx: Context, profileId: String): MutableSet<String> {
        return prefs(ctx).getStringSet("domains_$profileId", emptySet())
            ?.toMutableSet() ?: mutableSetOf()
    }

    fun trackDomain(ctx: Context, profileId: String, host: String?) {
        if (host.isNullOrBlank() || host == "mahar.local") return
        val set = domains(ctx, profileId)
        if (set.add(host)) {
            prefs(ctx).edit().putStringSet("domains_$profileId", set).apply()
        }
    }

    // ---------------------------------------------------------- cookies
    fun cookieFile(ctx: Context, profileId: String): File =
        File(ctx.filesDir, "cookies_$profileId.txt")

    private fun cm(): CookieManager = CookieManager.getInstance()

    /** Save current WebView cookies for this profile's domains to its file. */
    fun snapshotCookies(ctx: Context, profileId: String, domains: Set<String>) {
        try {
            val lines = StringBuilder()
            for (domain in domains) {
                val cookies = cm().getCookie("https://$domain") ?: continue
                if (cookies.isNotBlank()) lines.append(domain).append('\t').append(cookies).append('\n')
            }
            cookieFile(ctx, profileId).writeText(lines.toString())
        } catch (_: Exception) { }
    }

    /** Load a profile's saved cookies into the WebView. */
    fun restoreCookies(ctx: Context, profileId: String) {
        try {
            val file = cookieFile(ctx, profileId)
            if (!file.exists()) return
            file.readLines().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size < 2) return@forEach
                val domain = parts[0]
                parts[1].split("; ").forEach { pair ->
                    if (pair.contains("=")) cm().setCookie("https://$domain/", "$pair; path=/")
                }
            }
            cm().flush()
        } catch (_: Exception) { }
    }

    fun clearCookies(onDone: (() -> Unit)?) {
        try {
            cm().removeAllCookies { onDone?.invoke() }
            cm().flush()
        } catch (_: Exception) { onDone?.invoke() }
    }

    /** Parse cookies.txt (Netscape), JSON, or Mahar snapshot text. */
    fun parseCookiesText(text: String): List<ImportedCookie> {
        val out = mutableListOf<ImportedCookie>()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return out
        try {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val domain = o.optString("domain", "").trim().removePrefix(".")
                    if (domain.isEmpty()) continue
                    if (o.has("name")) {
                        out += ImportedCookie(domain, o.getString("name"), o.optString("value", ""))
                    } else if (o.has("cookies")) {
                        o.getString("cookies").split("; ").forEach { pair ->
                            val eq = pair.indexOf('=')
                            if (eq > 0) out += ImportedCookie(domain, pair.substring(0, eq), pair.substring(eq + 1))
                        }
                    }
                }
                return out
            }
        } catch (_: Exception) { }
        trimmed.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val fields = line.split('\t')
            if (fields.size >= 7) {
                // Netscape cookies.txt: domain, flag, path, secure, expiry, name, value
                val domain = fields[0].trim().removePrefix(".")
                if (domain.isNotEmpty() && fields[5].isNotEmpty()) {
                    out += ImportedCookie(domain, fields[5], fields[6])
                }
            } else if (fields.size == 2 && fields[1].contains("=")) {
                // Mahar snapshot: domain \t name=value; name2=value2
                val domain = fields[0].trim()
                fields[1].split("; ").forEach { pair ->
                    val eq = pair.indexOf('=')
                    if (eq > 0) out += ImportedCookie(domain, pair.substring(0, eq), pair.substring(eq + 1))
                }
            }
        }
        return out
    }

    /** Apply parsed cookies to the WebView; returns how many were set. */
    fun applyCookies(ctx: Context, profileId: String, cookies: List<ImportedCookie>): Int {
        var count = 0
        cookies.forEach { c ->
            try {
                cm().setCookie("https://$c.domain/", "${c.name}=${c.value}; path=/")
                trackDomain(ctx, profileId, c.domain)
                count++
            } catch (_: Exception) { }
        }
        cm().flush()
        return count
    }

    /** Export current cookies for this profile's domains as Netscape text. */
    fun exportNetscapeText(domains: Set<String>): String {
        val sb = StringBuilder()
        sb.append("# Netscape HTTP Cookie File — Mahar Browser export\n")
        sb.append("# Isko Mahar Browser me Cookies > Import se wapas load kiya ja sakta hai\n")
        for (domain in domains.sorted()) {
            val cookies = cm().getCookie("https://$domain") ?: continue
            cookies.split("; ").forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    sb.append(domain).append("\tTRUE\t/\tFALSE\t0\t")
                        .append(pair.substring(0, eq)).append('\t')
                        .append(pair.substring(eq + 1)).append('\n')
                }
            }
        }
        return sb.toString()
    }

    /** Save exported text to Downloads; returns a human-readable location. */
    fun exportToDownloads(ctx: Context, profileId: String, profileName: String, text: String): String {
        val fileName = "MaharBrowser-cookies-${profileName.replace(" ", "_")}.txt"
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                    return "Downloads/$fileName"
                }
            }
        } catch (_: Exception) { }
        return try {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val f = File(dir, fileName)
            f.writeText(text)
            f.absolutePath
        } catch (e: Exception) {
            "Export fail: ${e.message}"
        }
    }
}
