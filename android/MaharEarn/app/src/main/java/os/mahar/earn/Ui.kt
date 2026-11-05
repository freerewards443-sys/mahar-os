package os.mahar.earn

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import os.mahar.earn.R

object Ui {
    val GREEN = Color.parseColor("#0E7A44")
    val GREEN_DARK = Color.parseColor("#07301D")
    val GREEN_MID = Color.parseColor("#0B3D27")
    val GOLD = Color.parseColor("#E8A200")
    val GOLD_LIGHT = Color.parseColor("#F5B301")
    val BG = Color.parseColor("#EFF5F0")
    val CARD = Color.WHITE
    val TEXT = Color.parseColor("#16241B")
    val MUTED = Color.parseColor("#5F7166")
    val RED = Color.parseColor("#C62828")
    val SKELETON = Color.parseColor("#DDE7DE")

    fun dp(a: Activity, v: Int): Int = (v * a.resources.displayMetrics.density).toInt()

    fun root(a: Activity): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        l.setBackgroundColor(BG)
        return l
    }

    fun gradient(colorFrom: Int, colorTo: Int, radius: Float, vertical: Boolean = true): GradientDrawable {
        val g = GradientDrawable(
            if (vertical) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(colorFrom, colorTo))
        g.cornerRadius = radius
        return g
    }

    fun header(a: Activity, title: String, sub: String = ""): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.HORIZONTAL
        l.background = gradient(Color.parseColor("#0B3D27"), Color.parseColor("#0E7A44"), 0f)
        l.setPadding(dp(a, 16), dp(a, 20), dp(a, 16), dp(a, 16))
        l.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(a)
        logo.setImageResource(R.drawable.logo)
        logo.layoutParams = LinearLayout.LayoutParams(dp(a, 52), dp(a, 52))
        l.addView(logo)
        val texts = LinearLayout(a)
        texts.orientation = LinearLayout.VERTICAL
        val tlp = LinearLayout.LayoutParams(0, -2, 1f); tlp.leftMargin = dp(a, 12); texts.layoutParams = tlp
        val t = TextView(a); t.text = title; t.setTextColor(Color.WHITE)
        t.textSize = 21f; t.typeface = Typeface.DEFAULT_BOLD
        texts.addView(t)
        if (sub.isNotEmpty()) {
            val s = TextView(a); s.text = sub; s.setTextColor(Color.parseColor("#CDE8D8")); s.textSize = 13f
            texts.addView(s)
        }
        l.addView(texts)
        return l
    }

    fun card(a: Activity): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(); bg.cornerRadius = dp(a, 16).toFloat(); bg.setColor(CARD)
        bg.setStroke(dp(a, 1), Color.parseColor("#DCE7DE"))
        l.background = bg
        l.elevation = dp(a, 3).toFloat()
        l.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(a, 14), dp(a, 7), dp(a, 14), dp(a, 7))
        l.layoutParams = lp
        return l
    }

    /** Staggered entrance: fade + slide up. Call after adding to parent. */
    fun animateIn(v: View, index: Int) {
        v.alpha = 0f; v.translationY = 40f
        v.animate().alpha(1f).translationY(0f).setStartDelay((index * 70).toLong())
            .setDuration(380).setInterpolator(DecelerateInterpolator()).start()
    }

    fun label(a: Activity, txt: String, size: Float = 15f, bold: Boolean = false, color: Int = TEXT): TextView {
        val t = TextView(a); t.text = txt; t.textSize = size; t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun pressable(a: Activity, color: Int, radiusDp: Int): StateListDrawable {
        val normal = GradientDrawable(); normal.cornerRadius = dp(a, radiusDp).toFloat(); normal.setColor(color)
        val pressedColor = Color.rgb((Color.red(color) * 0.75).toInt(), (Color.green(color) * 0.75).toInt(), (Color.blue(color) * 0.75).toInt())
        val pressed = GradientDrawable(); pressed.cornerRadius = dp(a, radiusDp).toFloat(); pressed.setColor(pressedColor)
        val sd = StateListDrawable()
        sd.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        sd.addState(intArrayOf(), normal)
        return sd
    }

    fun button(a: Activity, txt: String, bgColor: Int = GREEN, onClick: () -> Unit): Button {
        val b = Button(a); b.text = txt; b.setTextColor(Color.WHITE); b.textSize = 15f
        b.typeface = Typeface.DEFAULT_BOLD; b.isAllCaps = false
        b.background = pressable(a, bgColor, 12)
        b.elevation = dp(a, 2).toFloat()
        val lp = LinearLayout.LayoutParams(-1, dp(a, 52))
        lp.setMargins(0, dp(a, 6), 0, dp(a, 6)); b.layoutParams = lp
        b.setOnClickListener { v ->
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }.start()
            onClick()
        }
        return b
    }

    fun smallButton(a: Activity, txt: String, bgColor: Int, onClick: () -> Unit): Button {
        val b = Button(a); b.text = txt; b.setTextColor(Color.WHITE); b.textSize = 13f
        b.typeface = Typeface.DEFAULT_BOLD; b.isAllCaps = false
        b.background = pressable(a, bgColor, 9)
        val lp = LinearLayout.LayoutParams(0, dp(a, 44), 1f)
        lp.setMargins(dp(a, 3), dp(a, 4), dp(a, 3), 0); b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    fun input(a: Activity, hint: String, inputType: Int? = null): EditText {
        val e = EditText(a); e.hint = hint; e.textSize = 15f; e.setTextColor(TEXT); e.setHintTextColor(MUTED)
        e.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12))
        val bg = GradientDrawable(); bg.cornerRadius = dp(a, 10).toFloat(); bg.setColor(Color.parseColor("#F7FAF7"))
        bg.setStroke(dp(a, 1), Color.parseColor("#C9D8CC")); e.background = bg
        if (inputType != null) e.inputType = inputType
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(a, 5), 0, dp(a, 5)); e.layoutParams = lp
        return e
    }

    fun toast(a: Activity, msg: String) = Toast.makeText(a, msg, Toast.LENGTH_LONG).show()
    fun center(t: TextView) { t.gravity = Gravity.CENTER }

    /** Animated number count-up (balances feel alive). */
    fun countUp(t: TextView, to: Int, prefix: String = "", suffix: String = "") {
        val anim = ValueAnimator.ofInt(0, to)
        anim.duration = 700; anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { t.text = prefix + (it.animatedValue as Int) + suffix }
        anim.start()
    }

    /** Gray pulsing placeholder card — lazy-loading skeleton. */
    fun skeleton(a: Activity, lines: Int = 3): LinearLayout {
        val c = card(a)
        for (i in 0 until lines) {
            val bar = View(a)
            val bg = GradientDrawable(); bg.cornerRadius = dp(a, 6).toFloat(); bg.setColor(SKELETON)
            bar.background = bg
            val lp = LinearLayout.LayoutParams(if (i == 0) dp(a, 140) else -1, dp(a, 16))
            lp.setMargins(0, dp(a, 5), if (i == lines - 1) dp(a, 90) else 0, dp(a, 5))
            bar.layoutParams = lp
            c.addView(bar)
        }
        val pulse = AlphaAnimation(0.45f, 1f)
        pulse.duration = 650; pulse.repeatMode = AlphaAnimation.REVERSE; pulse.repeatCount = AlphaAnimation.INFINITE
        c.startAnimation(pulse)
        return c
    }

    fun progress(a: Activity, max: Int, value: Int): ProgressBar {
        val p = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal)
        p.max = max; p.progress = value
        p.progressTintList = android.content.res.ColorStateList.valueOf(GOLD_LIGHT)
        p.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DCE7DE"))
        val anim = ObjectAnimator.ofInt(p, "progress", 0, value); anim.duration = 600; anim.start()
        val lp = LinearLayout.LayoutParams(-1, dp(a, 12))
        lp.setMargins(0, dp(a, 8), 0, dp(a, 4)); p.layoutParams = lp
        return p
    }

    /** Page scaffold: returns (contentRoot inside scroll, setContentView target with bottom nav). */
    fun page(a: Activity, currentTab: String): Pair<LinearLayout, LinearLayout> {
        val outer = LinearLayout(a); outer.orientation = LinearLayout.VERTICAL; outer.setBackgroundColor(BG)
        val scroll = ScrollView(a); scroll.setBackgroundColor(BG)
        scroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        val content = root(a)
        scroll.addView(content)
        outer.addView(scroll)
        outer.addView(bottomNav(a, currentTab))
        return Pair(content, outer)
    }

    fun bottomNav(a: Activity, current: String): LinearLayout {
        val nav = LinearLayout(a); nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.WHITE); nav.elevation = dp(a, 8).toFloat()
        nav.setPadding(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 8))
        data class Tab(val key: String, val icon: String, val label: String, val cls: Class<*>)
        val tabs = listOf(
            Tab("home", "🏠", "Home", MainActivity::class.java),
            Tab("missions", "📺", "Missions", MissionsActivity::class.java),
            Tab("spin", "🎡", "Spin", SpinActivity::class.java),
            Tab("withdraw", "💸", "Withdraw", WithdrawActivity::class.java),
            Tab("settings", "⚙️", "Settings", SettingsActivity::class.java)
        )
        for (t in tabs) {
            val item = LinearLayout(a); item.orientation = LinearLayout.VERTICAL
            item.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            item.gravity = Gravity.CENTER
            val active = t.key == current
            val ic = TextView(a); ic.text = t.icon; ic.textSize = if (active) 24f else 21f; ic.gravity = Gravity.CENTER
            val lb = TextView(a); lb.text = t.label; lb.textSize = 11f; lb.gravity = Gravity.CENTER
            lb.setTextColor(if (active) GREEN else MUTED)
            if (active) lb.typeface = Typeface.DEFAULT_BOLD
            item.addView(ic); item.addView(lb)
            if (!active) item.setOnClickListener {
                val i = Intent(a, t.cls); i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                a.startActivity(i); a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            nav.addView(item)
        }
        return nav
    }
}
