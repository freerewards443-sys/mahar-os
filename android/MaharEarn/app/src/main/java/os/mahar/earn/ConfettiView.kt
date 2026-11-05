package os.mahar.earn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.random.Random

/** Lightweight confetti burst overlay — particles fall + spin for ~2.4s. */
class ConfettiView(ctx: Context) : View(ctx) {
    private data class P(var x: Float, var y: Float, var vx: Float, var vy: Float,
                         var size: Float, var color: Int, var rot: Float, var vr: Float)
    private val parts = mutableListOf<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var progress: Float = 0f
        set(v) { field = v; invalidate() }

    fun burst(w: Int, h: Int) {
        parts.clear()
        val colors = listOf("#F5B301", "#1E9E57", "#C62828", "#7B1FA2", "#1565C0", "#FF7043")
        for (i in 0 until 90) {
            parts.add(P(
                x = w / 2f + Random.nextFloat() * 160 - 80,
                y = h * 0.35f,
                vx = (Random.nextFloat() - 0.5f) * 9f,
                vy = -(6f + Random.nextFloat() * 8f),
                size = 8f + Random.nextFloat() * 10f,
                color = Color.parseColor(colors[Random.nextInt(colors.size)]),
                rot = Random.nextFloat() * 360f,
                vr = (Random.nextFloat() - 0.5f) * 18f
            ))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (parts.isEmpty()) return
        val steps = (progress * 60).toInt()
        // simulate deterministically from burst state using progress steps
        for (p in parts) {
            var x = p.x; var y = p.y; var vy = p.vy
            var px = x; var py = y
            for (s in 0 until steps) {
                px += p.vx; py += vy; vy += 0.35f; px += kotlin.math.sin((s + p.rot) * 0.2).toFloat() * 1.2f
            }
            paint.color = p.color
            paint.alpha = (255 * (1f - progress * 0.7f)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(px, py)
            canvas.rotate(p.rot + p.vr * steps)
            canvas.drawRect(-p.size / 2, -p.size / 4, p.size / 2, p.size / 4, paint)
            canvas.restore()
        }
    }
}
