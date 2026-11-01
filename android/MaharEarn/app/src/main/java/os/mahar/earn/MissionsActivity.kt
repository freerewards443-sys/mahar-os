package os.mahar.earn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class MissionsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this); draw()
    }
    override fun onResume() { super.onResume(); draw() }
    private fun draw() {
        val me = Store.me() ?: return
        val list = Store.missionsForToday(me)
        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "📺 Daily Missions",
            "Har mission = ad dekho (${Store.AD_WATCH_SECONDS} sec) phir Tokens claim karo • Tokens: ${me.tokens}"))
        for (m in list) {
            val c = Ui.card(this)
            val done = me.missionsDone.contains(m.id)
            c.addView(Ui.label(this, m.title, 16f, true))
            c.addView(Ui.label(this, "Reward: +${m.tokens} Tokens", 13f, false, Ui.MUTED))
            if (done) {
                c.addView(Ui.label(this, "✅ Aaj ye mission complete hai", 14f, true, Ui.GREEN))
            } else {
                c.addView(Ui.button(this, "▶ Watch Ad") {
                    val i = Intent(this, AdViewActivity::class.java)
                    i.putExtra("missionId", m.id)
                    startActivity(i)
                })
            }
            root.addView(c)
        }
        val back = Ui.card(this)
        back.addView(Ui.button(this, "⬅ Back", Ui.GREEN_DARK) { finish() })
        root.addView(back)
        scroll.addView(root)
        setContentView(scroll)
    }
}
