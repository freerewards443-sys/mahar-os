package os.mahar.earn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout

class MissionsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this); draw()
    }
    override fun onResume() { super.onResume(); draw() }
    private fun draw() {
        val me = Store.me() ?: return
        val list = Store.missionsForToday(me)
        val (root, outer) = Ui.page(this, "missions")
        root.addView(Ui.header(this, "Daily Missions",
            "Ad dekho (${Store.AD_WATCH_SECONDS} sec) → Tokens claim karo"))
        var anim = 0
        val prog = Ui.card(this)
        prog.addView(Ui.label(this, "Aaj ki progress: ${me.missionsDone.size}/${list.size} missions", 15f, true))
        prog.addView(Ui.progress(this, list.size, me.missionsDone.size))
        prog.addView(Ui.label(this, "🪙 Tumhare Tokens: ${me.tokens}", 13f, false, Ui.MUTED))
        root.addView(prog); Ui.animateIn(prog, anim++)
        for (m in list) {
            val c = Ui.card(this)
            val done = me.missionsDone.contains(m.id)
            val rowL = LinearLayout(this); rowL.orientation = LinearLayout.HORIZONTAL; rowL.gravity = Gravity.CENTER_VERTICAL
            val left = LinearLayout(this); left.orientation = LinearLayout.VERTICAL
            left.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            left.addView(Ui.label(this, m.title, 16f, true))
            left.addView(Ui.label(this, "Reward: +${m.tokens} Tokens", 13f, false, Ui.MUTED))
            rowL.addView(left)
            val badge = Ui.label(this, if (done) "✅" else "🎬", 26f)
            rowL.addView(badge)
            c.addView(rowL)
            if (done) {
                c.addView(Ui.label(this, "Aaj ye mission complete hai — kal phir milega", 13f, true, Ui.GREEN))
            } else {
                c.addView(Ui.button(this, "▶ Watch Ad") {
                    val i = Intent(this, AdViewActivity::class.java)
                    i.putExtra("missionId", m.id)
                    startActivity(i)
                })
            }
            root.addView(c); Ui.animateIn(c, anim++)
        }
        setContentView(outer)
    }
}
