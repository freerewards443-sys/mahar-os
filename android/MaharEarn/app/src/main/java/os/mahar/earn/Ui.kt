package os.mahar.earn

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object Ui {
    val GREEN = Color.parseColor("#0E5E3A")
    val GREEN_DARK = Color.parseColor("#0B3D27")
    val GOLD = Color.parseColor("#F5B301")
    val BG = Color.parseColor("#F2F7F3")
    val CARD = Color.WHITE
    val TEXT = Color.parseColor("#1B2A21")
    val MUTED = Color.parseColor("#5F7166")
    val RED = Color.parseColor("#C62828")

    fun dp(a: Activity, v: Int): Int = (v * a.resources.displayMetrics.density).toInt()

    fun root(a: Activity): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        l.setBackgroundColor(BG)
        return l
    }
    fun header(a: Activity, title: String, sub: String = ""): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        l.setBackgroundColor(GREEN)
        l.setPadding(dp(a, 20), dp(a, 22), dp(a, 20), dp(a, 18))
        val t = TextView(a); t.text = title; t.setTextColor(Color.WHITE)
        t.textSize = 22f; t.typeface = Typeface.DEFAULT_BOLD
        l.addView(t)
        if (sub.isNotEmpty()) {
            val s = TextView(a); s.text = sub; s.setTextColor(Color.parseColor("#CDE8D8")); s.textSize = 13f
            val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(a, 4); s.layoutParams = lp
            l.addView(s)
        }
        return l
    }
    fun card(a: Activity): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(); bg.cornerRadius = dp(a, 14).toFloat(); bg.setColor(CARD)
        bg.setStroke(dp(a, 1), Color.parseColor("#DCE7DE"))
        l.background = bg
        l.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(a, 14), dp(a, 7), dp(a, 14), dp(a, 7))
        l.layoutParams = lp
        return l
    }
    fun label(a: Activity, txt: String, size: Float = 15f, bold: Boolean = false, color: Int = TEXT): TextView {
        val t = TextView(a); t.text = txt; t.textSize = size; t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }
    fun button(a: Activity, txt: String, bgColor: Int = GREEN, onClick: () -> Unit): Button {
        val b = Button(a); b.text = txt; b.setTextColor(Color.WHITE); b.textSize = 15f
        b.typeface = Typeface.DEFAULT_BOLD; b.isAllCaps = false
        val bg = GradientDrawable(); bg.cornerRadius = dp(a, 10).toFloat(); bg.setColor(bgColor)
        b.background = bg
        val lp = LinearLayout.LayoutParams(-1, dp(a, 50))
        lp.setMargins(0, dp(a, 6), 0, dp(a, 6)); b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }
    fun smallButton(a: Activity, txt: String, bgColor: Int, onClick: () -> Unit): Button {
        val b = Button(a); b.text = txt; b.setTextColor(Color.WHITE); b.textSize = 13f
        b.typeface = Typeface.DEFAULT_BOLD; b.isAllCaps = false
        val bg = GradientDrawable(); bg.cornerRadius = dp(a, 8).toFloat(); bg.setColor(bgColor)
        b.background = bg
        val lp = LinearLayout.LayoutParams(0, dp(a, 42), 1f)
        lp.setMargins(dp(a, 3), dp(a, 4), dp(a, 3), 0); b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }
    fun input(a: Activity, hint: String, inputType: Int? = null): EditText {
        val e = EditText(a); e.hint = hint; e.textSize = 15f; e.setTextColor(TEXT); e.setHintTextColor(MUTED)
        e.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10))
        val bg = GradientDrawable(); bg.cornerRadius = dp(a, 10).toFloat(); bg.setColor(Color.WHITE)
        bg.setStroke(dp(a, 1), Color.parseColor("#C9D8CC")); e.background = bg
        if (inputType != null) e.inputType = inputType
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(a, 5), 0, dp(a, 5)); e.layoutParams = lp
        return e
    }
    fun toast(a: Activity, msg: String) = Toast.makeText(a, msg, Toast.LENGTH_LONG).show()
    fun center(t: TextView) { t.gravity = Gravity.CENTER }
}
