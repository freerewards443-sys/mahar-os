package os.mahar.earn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout

class MainActivity : Activity() {
    private var firstLoad = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Store.sessionId.isEmpty() || Store.isAdmin()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        if (firstLoad) { showSkeleton(); Handler(Looper.getMainLooper()).postDelayed({ firstLoad = false; draw() }, 650) }
        else draw()
    }
    override fun onResume() { super.onResume(); if (!firstLoad && Store.sessionId.isNotEmpty() && !Store.isAdmin()) draw() }

    private fun showSkeleton() {
        val (content, outer) = Ui.page(this, "home")
        content.addView(Ui.header(this, "💰 Mahar Earn", "Loading..."))
        content.addView(Ui.skeleton(this, 3))
        content.addView(Ui.skeleton(this, 4))
        content.addView(Ui.skeleton(this, 2))
        setContentView(outer)
    }

    private fun draw() {
        val me = Store.me() ?: return
        Store.missionsForToday(me)
        val (root, outer) = Ui.page(this, "home")
        var anim = 0
        root.addView(Ui.header(this, "Mahar Earn", "Assalam-o-Alaikum, ${me.name} 👋"))

        // Gradient balance card with count-up
        val bal = Ui.card(this)
        bal.background = Ui.gradient(Color.parseColor("#0B3D27"), Color.parseColor("#128A4C"), Ui.dp(this, 16).toFloat())
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        val t1 = LinearLayout(this); t1.orientation = LinearLayout.VERTICAL
        t1.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        t1.addView(Ui.label(this, "🪙 TOKENS", 12f, true, Color.parseColor("#BFE6CE")))
        val tokVal = Ui.label(this, "0", 30f, true, Color.WHITE)
        t1.addView(tokVal)
        val spinHint = Ui.label(this, "1 Spin = ${Store.SPIN_COST} Tokens", 11f, false, Color.parseColor("#9CC7AC"))
        t1.addView(spinHint)
        val t2 = LinearLayout(this); t2.orientation = LinearLayout.VERTICAL
        t2.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        t2.addView(Ui.label(this, "💵 CASH", 12f, true, Color.parseColor("#BFE6CE")))
        val cashVal = Ui.label(this, "Rs 0", 30f, true, Ui.GOLD_LIGHT)
        t2.addView(cashVal)
        val wHint = Ui.label(this, "Min withdraw Rs ${Store.MIN_WITHDRAW}", 11f, false, Color.parseColor("#9CC7AC"))
        t2.addView(wHint)
        row.addView(t1); row.addView(t2)
        bal.addView(row)
        root.addView(bal); Ui.animateIn(bal, anim++)
        Ui.countUp(tokVal, me.tokens)
        val cashAnim = android.animation.ValueAnimator.ofInt(0, me.cash)
        cashAnim.duration = 700
        cashAnim.addUpdateListener { cashVal.text = "Rs " + (it.animatedValue as Int) }
        cashAnim.start()

        // Streak
        val st = Ui.card(this)
        val next = Store.streakState(me)
        val claimed = me.lastStreakDate == Store.today()
        st.addView(Ui.label(this, "🔥 Daily Streak — Day $next of 7", 17f, true))
        st.addView(Ui.progress(this, 7, if (claimed) me.streak else next - 1))
        val dots = StringBuilder()
        for (d in 1..7) dots.append(if (claimed && d <= me.streak || !claimed && d < next) "● " else if (d == next && !claimed) "◉ " else "○ ")
        st.addView(Ui.label(this, dots.toString().trim() + "   (Day $next = $next Tokens)", 14f, false, Ui.MUTED))
        st.addView(Ui.label(this, "Roz claim karo: Day1=1, Day2=2 ... Day7=7 Tokens. Ek din miss = wapas Day 1.", 12f, false, Ui.MUTED))
        if (claimed) {
            st.addView(Ui.label(this, "✅ Aaj ka streak claim ho gaya hai", 14f, true, Ui.GREEN))
        } else {
            st.addView(Ui.button(this, "Claim Day $next Streak (+$next Tokens)", Ui.GOLD) {
                val got = Store.claimStreak(me)
                if (got > 0) Ui.toast(this, "🔥 Day $got streak! +$got Tokens milay")
                draw()
            })
        }
        root.addView(st); Ui.animateIn(st, anim++)

        // Missions progress
        val mp = Ui.card(this)
        val done = me.missionsDone.size
        mp.addView(Ui.label(this, "📺 Aaj ke Missions — $done/${Store.missions.size} complete", 16f, true))
        mp.addView(Ui.progress(this, Store.missions.size, done))
        mp.addView(Ui.button(this, "Missions Kholo — Ads dekho, Tokens kamao") { startActivity(Intent(this, MissionsActivity::class.java)) })
        root.addView(mp); Ui.animateIn(mp, anim++)

        // Menu
        val menu = Ui.card(this)
        menu.addView(Ui.label(this, "Quick Menu", 17f, true))
        menu.addView(Ui.button(this, "🎡 Spin — ${Store.SPIN_COST} Tokens = Rs 5 / 10 / 20 jeeto", Ui.GOLD) { startActivity(Intent(this, SpinActivity::class.java)) })
        menu.addView(Ui.button(this, "💸 Withdraw — JazzCash / Easypaisa") { startActivity(Intent(this, WithdrawActivity::class.java)) })
        menu.addView(Ui.button(this, "🧾 My Withdrawals") { startActivity(Intent(this, HistoryActivity::class.java)) })
        menu.addView(Ui.button(this, "🎧 Support") { startActivity(Intent(this, SupportActivity::class.java)) })
        root.addView(menu); Ui.animateIn(menu, anim++)

        setContentView(outer)
    }
}
