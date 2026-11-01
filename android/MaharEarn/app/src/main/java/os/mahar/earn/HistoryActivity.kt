package os.mahar.earn

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val me = Store.me() ?: run { finish(); return }
        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "🧾 My Withdrawals", "Paid = paisay bhej diye gaye • Pending = admin review me hai"))
        val mine = Store.withdrawals.filter { it.accountId == me.id }
        if (mine.isEmpty()) {
            val c = Ui.card(this); c.addView(Ui.label(this, "Abhi koi withdrawal nahi hai.", 14f, false, Ui.MUTED)); root.addView(c)
        }
        for (w in mine) {
            val c = Ui.card(this)
            val color = when (w.status) { "Paid" -> Ui.GREEN; "Rejected" -> Ui.RED; else -> Color.parseColor("#B8860B") }
            c.addView(Ui.label(this, "Rs ${w.amount} — ${w.method}", 16f, true))
            c.addView(Ui.label(this, "${w.number} • ${w.title}\n${Store.fmtDate(w.date)}", 13f, false, Ui.MUTED))
            c.addView(Ui.label(this, "Status: ${w.status.uppercase()}", 15f, true, color))
            root.addView(c)
        }
        val b = Ui.card(this); b.addView(Ui.button(this, "⬅ Back", Ui.GREEN_DARK) { finish() }); root.addView(b)
        scroll.addView(root)
        setContentView(scroll)
    }
}
