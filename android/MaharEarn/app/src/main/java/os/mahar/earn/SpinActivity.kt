package os.mahar.earn

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

class SpinActivity : Activity() {
    private lateinit var wheel: WheelView
    private var spinning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val me = Store.me() ?: run { finish(); return }

        val (root, outer) = Ui.page(this, "spin")
        root.addView(Ui.header(this, "Spin & Win",
            "1 Spin = ${Store.SPIN_COST} Tokens • Jeeto Rs 5 / 10 / 20"))

        val balCard = Ui.card(this)
        val balLabel = Ui.label(this, "🪙 Tokens: ${me.tokens}    💵 Cash: Rs ${me.cash}", 16f, true, Ui.GREEN)
        balCard.addView(balLabel)
        root.addView(balCard); Ui.animateIn(balCard, 0)

        // Wheel + confetti overlay
        val frame = FrameLayout(this)
        frame.layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this, 350))
        wheel = WheelView(this)
        wheel.layoutParams = FrameLayout.LayoutParams(-1, -1)
        frame.addView(wheel)
        val confetti = ConfettiView(this)
        confetti.layoutParams = FrameLayout.LayoutParams(-1, -1)
        frame.addView(confetti)
        val pointer = TextView(this)
        pointer.text = "▼"; pointer.textSize = 32f; pointer.setTextColor(Color.parseColor("#C62828"))
        pointer.layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        }
        frame.addView(pointer)
        root.addView(frame)

        val result = Ui.label(this, "", 19f, true, Ui.GREEN); Ui.center(result)
        root.addView(result)

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
            result.text = "Wheel ghoom raha hai... 🍀"
            pointer.animate().rotationBy(12f).setDuration(120).withEndAction {
                pointer.animate().rotation(0f).setDuration(120).start()
            }.start()
            val seg = 360f / WheelView.LABELS.size
            val targetIndex = Random.nextInt(WheelView.LABELS.size)
            val targetCenter = targetIndex * seg + seg / 2f
            val current = wheel.wheelRotation % 360f
            var delta = (270f - targetCenter - current) % 360f
            if (delta < 0) delta += 360f
            val total = 360f * 6 + delta
            val startRot = wheel.wheelRotation
            var lastSeg = -1
            val anim = ValueAnimator.ofFloat(0f, total)
            anim.duration = 5200
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener {
                val v = it.animatedValue as Float
                wheel.wheelRotation = startRot + v
                val segNow = ((wheel.wheelRotation / seg).toInt())
                if (segNow != lastSeg) {
                    lastSeg = segNow
                    try { vibrator.vibrate(12) } catch (e: Exception) { }
                }
            }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    spinning = false
                    val amt = WheelView.AMOUNTS[targetIndex]
                    if (amt > 0) {
                        acc.cash += amt; Store.save()
                        result.text = "🎉 Mubarak! Rs $amt jeetay!"
                        result.scaleX = 0.6f; result.scaleY = 0.6f
                        result.animate().scaleX(1.15f).scaleY(1.15f).setDuration(160).withEndAction {
                            result.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        }.start()
                        confetti.burst(frame.width, frame.height)
                        val ca = ValueAnimator.ofFloat(0f, 1f)
                        ca.duration = 2400
                        ca.addUpdateListener { confetti.progress = it.animatedValue as Float }
                        ca.start()
                        try { vibrator.vibrate(longArrayOf(0, 60, 60, 60), -1) } catch (e: Exception) { }
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
        prizes.addView(Ui.label(this, "🏆 Prizes: Rs 5 • Rs 10 • Rs 20 • Try Again (x3 slices)", 14f, false, Ui.MUTED))
        prizes.addView(Ui.label(this, "Tokens khatam? Missions aur daily streak se roz tokens kamao.", 12f, false, Ui.MUTED))
        root.addView(prizes); Ui.animateIn(prizes, 1)

        setContentView(outer)
    }
}
