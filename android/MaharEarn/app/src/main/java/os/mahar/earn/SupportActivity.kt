package os.mahar.earn

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView

class SupportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this); draw()
    }
    private fun draw() {
        val me = Store.me() ?: return
        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "🎧 Support", "Masla likho — admin ko message jayega, jawab yahin milega"))
        val c = Ui.card(this)
        val msgIn = Ui.input(this, "Apna masla / sawal likho...")
        msgIn.minLines = 3
        c.addView(msgIn)
        c.addView(Ui.button(this, "Message Bhejo") {
            val m = msgIn.text.toString().trim()
            if (m.length < 5) { Ui.toast(this, "Message thora detail me likho"); return@button }
            Store.addTicket(me, m)
            Ui.toast(this, "✅ Message bhej diya gaya")
            draw()
        })
        root.addView(c)
        val mine = Store.tickets.filter { it.accountId == me.id }
        for (t in mine) {
            val tc = Ui.card(this)
            tc.addView(Ui.label(this, t.message, 14f, true))
            tc.addView(Ui.label(this, Store.fmtDate(t.date) + " • " + t.status, 12f, false, Ui.MUTED))
            if (t.reply.isNotEmpty()) tc.addView(Ui.label(this, "Admin: " + t.reply, 13f, false, Ui.GREEN))
            root.addView(tc)
        }
        val b = Ui.card(this); b.addView(Ui.button(this, "⬅ Back", Ui.GREEN_DARK) { finish() }); root.addView(b)
        scroll.addView(root)
        setContentView(scroll)
    }
}
