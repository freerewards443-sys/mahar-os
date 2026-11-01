package os.mahar.earn

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.random.Random

class SpinActivity : Activity() {
    private lateinit var wheel: WheelView
    private var spinning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val me = Store.me() ?: run { finish(); return }

        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "🎡 Spin & Win",
            "1 Spin = ${Store.SPIN_COST} Tokens • Jeeto Rs 5 / 10 / 20"))

        val balCard = Ui.card(this)
        val balLabel = Ui.label(this, "🪙 Tokens: ${me.tokens}    💵 Cash: Rs ${me.cash}", 16f, true, Ui.GREEN)
        balCard.addView(balLabel)
        root.addView(balCard)

        // Wheel with pointer
        val frame = FrameLayout(this)
        frame.layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this, 340))
        wheel = WheelView(this)
        wheel.layoutParams = FrameLayout.LayoutParams(-1, -1)
        frame.addView(wheel)
        val pointer = TextView(this)
        pointer.text = "▼"; pointer.textSize = 30f; pointer.setTextColor(Color.parseColor("#C62828"))
        pointer.layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        }
        frame.addView(pointer)
        root.addView(frame)

        val result = Ui.label(this, "", 18f, true, Ui.GREEN); Ui.center(result)
        root.addView(result)

        val spinBtn = Ui.button(this, "🎡 SPIN NOW (${Store.SPIN_COST} Tokens)", Ui.GOLD) {
            if (spinning) return@button
            val acc = Store.me() ?: return@button
            if (acc.tokens < Store.SPIN_COST) {
                Ui.toast(this, "Tokens kam hain! Pehle missions/streak se tokens kamao")
                return@button
            }
            acc.tokens -= Store.SPIN_COST; Store.save()
            balLabel.text = "🪙 Tokens: ${acc.tokens}    💵 Cash: Rs ${acc.cash}"
            spinning = true
            result.text = "Wheel ghoom raha hai..."
            val seg = 360f / WheelView.LABELS.size
            val targetIndex = Random.nextInt(WheelView.LABELS.size)
            // pointer at top (-90 deg). Segment i center must land at top:
            val targetCenter = targetIndex * seg + seg / 2f
            val current = wheel.wheelRotation % 360f
            var delta = (270f - targetCenter - current) % 360f
            if (delta < 0) delta += 360f
            val total = 360f * 6 + delta
            val startRot = wheel.wheelRotation
            val anim = ValueAnimator.ofFloat(0f, total)
            anim.duration = 5000
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener { wheel.wheelRotation = startRot + (it.animatedValue as Float) }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    spinning = false
                    val amt = WheelView.AMOUNTS[targetIndex]
                    if (amt > 0) {
                        acc.cash += amt; Store.save()
                        result.text = "🎉 Mubarak! Rs $amt jeetay!"
                    } else {
                        result.text = "😅 Try Again! Is baar kuch nahi mila"
                    }
                    balLabel.text = "🪙 Tokens: ${acc.tokens}    💵 Cash: Rs ${acc.cash}"
                }
            })
            anim.start()
        }
        val wrap = LinearLayout(this); wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0)
        wrap.addView(spinBtn)
        root.addView(wrap)

        val prizes = Ui.card(this)
        prizes.addView(Ui.label(this, "Prizes: Rs 5 • Rs 10 • Rs 20 • Try Again (x3)", 14f, false, Ui.MUTED))
        prizes.addView(Ui.button(this, "⬅ Back", Ui.GREEN_DARK) { finish() })
        root.addView(prizes)

        scroll.addView(root)
        setContentView(scroll)
    }
}
