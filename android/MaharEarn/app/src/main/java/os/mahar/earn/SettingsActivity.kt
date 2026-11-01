package os.mahar.earn

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val me = Store.me() ?: run { finish(); return }
        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "⚙️ Settings", "Meri App ID — isay mehfooz rakho!"))
        val c = Ui.card(this)
        c.addView(Ui.label(this, "🔑 Meri App ID", 15f, false, Ui.MUTED))
        c.addView(Ui.label(this, me.id, 24f, true, Ui.GREEN))
        c.addView(Ui.label(this, "⚠️ Ye code kahin likh kar / save kar ke rakho. Doosray phone par app khol kar 'App ID Login' me yehi code lagao ge to tumhara yehi account khul jayega. Code kisi ko mat do — jis ke paas code hai, wohi account khol sakta hai.", 13f, false, Ui.RED))
        c.addView(Ui.button(this, "📋 App ID Copy Karo", Ui.GOLD) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("App ID", me.id))
            Ui.toast(this, "App ID copy ho gayi")
        })
        c.addView(Ui.label(this, "Naam: ${me.name}\nMobile: ${me.phone}\nAccount bana: ${Store.fmtDate(me.createdAt)}", 14f))
        root.addView(c)
        val c2 = Ui.card(this)
        c2.addView(Ui.button(this, "🚪 Logout", Ui.RED) {
            Store.logout()
            val i = Intent(this, LoginActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i); finish()
        })
        c2.addView(Ui.button(this, "⬅ Back", Ui.GREEN_DARK) { finish() })
        root.addView(c2)
        scroll.addView(root)
        setContentView(scroll)
    }
}
