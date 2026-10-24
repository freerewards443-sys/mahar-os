package os.mahar.browser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Bookmark(val id: Long, val title: String, val url: String)

class BookmarksDb(ctx: Context, profileId: String = "main") :
    SQLiteOpenHelper(ctx, "mahar_browser_${profileId.replace(Regex("[^A-Za-z0-9_]"), "_")}.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT UNIQUE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT, ts INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    // ---- bookmarks ----
    fun addBookmark(title: String, url: String): Boolean {
        val cv = ContentValues().apply {
            put("title", title.ifBlank { url })
            put("url", url)
        }
        return writableDatabase.insertWithOnConflict("bookmarks", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun removeBookmark(url: String) {
        writableDatabase.delete("bookmarks", "url=?", arrayOf(url))
    }

    fun isBookmarked(url: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", arrayOf(url)).use {
            return it.moveToFirst()
        }
    }

    fun allBookmarks(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        readableDatabase.rawQuery("SELECT id, title, url FROM bookmarks ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) out += Bookmark(c.getLong(0), c.getString(1), c.getString(2))
        }
        return out
    }

    // ---- history ----
    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        // avoid spamming duplicates of the same page in a row
        readableDatabase.rawQuery("SELECT url FROM history ORDER BY id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst() && c.getString(0) == url) return
        }
        val cv = ContentValues().apply {
            put("title", title.ifBlank { url })
            put("url", url)
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insert("history", null, cv)
        // keep the last 500 entries
        writableDatabase.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 500)")
    }

    fun allHistory(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        readableDatabase.rawQuery("SELECT id, title, url FROM history ORDER BY id DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) out += Bookmark(c.getLong(0), c.getString(1), c.getString(2))
        }
        return out
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }
}
