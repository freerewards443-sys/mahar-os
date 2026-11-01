package os.mahar.earn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Store.sessionId.isEmpty() || Store.isAdmin()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        draw()
    }
    override fun onResume() { super.onResume(); if (Store.sessionId.isNotEmpty() && !Store.isAdmin()) draw() }

    private fun draw() {
        val me = Store.me() ?: return
        Store.missionsForToday(me)
        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "💰 Mahar Earn", "Assalam-o-Alaikum, ${me.name}"))

        // Balances
        val bal = Ui.card(this)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        val t1 = LinearLayout(this); t1.orientation = LinearLayout.VERTICAL
        val lp1 = LinearLayout.LayoutParams(0, -2, 1f); t1.layoutParams = lp1
        t1.addView(Ui.label(this, "🪙 Tokens", 13f, false, Ui.MUTED))
        t1.addView(Ui.label(this, "${me.tokens}", 26f, true, Ui.GREEN))
        val t2 = LinearLayout(this); t2.orientation = LinearLayout.VERTICAL
        t2.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        t2.addView(Ui.label(this, "💵 Cash Balance", 13f, false, Ui.MUTED))
        t2.addView(Ui.label(this, "Rs ${me.cash}", 26f, true, Ui.GREEN))
        row.addView(t1); row.addView(t2)
        bal.addView(row)
        root.addView(bal)

        // Streak
        val st = Ui.card(this)
        val next = Store.streakState(me)
        val claimed = me.lastStreakDate == Store.today()
        st.addView(Ui.label(this, "🔥 Daily Streak — Day $next of 7", 17f, true))
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
        root.addView(st)

        // Menu
        val menu = Ui.card(this)
        menu.addView(Ui.label(this, "Kamao", 17f, true))
        val done = me.missionsDone.size
        menu.addView(Ui.button(this, "📺 Daily Missions — Ads dekho, Tokens kamao ($done/${Store.missions.size} done)") { startActivity(Intent(this, MissionsActivity::class.java)) })
        menu.addView(Ui.button(this, "🎡 Spin — ${Store.SPIN_COST} Tokens = Rs 5 / 10 / 20 jeeto", Ui.GOLD) { startActivity(Intent(this, SpinActivity::class.java)) })
        menu.addView(Ui.label(this, "Paisay", 17f, true))
        menu.addView(Ui.button(this, "💸 Withdraw — JazzCash / Easypaisa (Min Rs ${Store.MIN_WITHDRAW})") { startActivity(Intent(this, WithdrawActivity::class.java)) })
        menu.addView(Ui.button(this, "🧾 My Withdrawals") { startActivity(Intent(this, HistoryActivity::class.java)) })
        menu.addView(Ui.label(this, "Account", 17f, true))
        menu.addView(Ui.button(this, "🎧 Support") { startActivity(Intent(this, SupportActivity::class.java)) })
        menu.addView(Ui.button(this, "⚙️ Settings — Meri App ID") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(menu)

        scroll.addView(root)
        setContentView(scroll)
    }
}
