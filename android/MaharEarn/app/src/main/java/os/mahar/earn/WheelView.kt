package os.mahar.earn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class WheelView(ctx: Context) : View(ctx) {
    companion object {
        val LABELS = listOf("Rs 5", "TRY\nAGAIN", "Rs 10", "TRY\nAGAIN", "Rs 20", "TRY\nAGAIN", "Rs 5", "Rs 10")
        val AMOUNTS = listOf(5, 0, 10, 0, 20, 0, 5, 10)
        val COLORS = listOf("#1E9E57", "#546E7A", "#F5B301", "#546E7A", "#C62828", "#546E7A", "#2E7D32", "#7B1FA2")
    }
    var wheelRotation: Float = 0f
        set(v) { field = v; invalidate() }

    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 4f; style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B3D27"); style = Paint.Style.FILL }
    private val hubText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5B301"); textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val cx = width / 2f; val cy = height / 2f
        val r = size / 2f - 12f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val sweep = 360f / LABELS.size
        for (i in LABELS.indices) {
            segPaint.color = Color.parseColor(COLORS[i])
            canvas.drawArc(rect, wheelRotation + i * sweep, sweep, true, segPaint)
            canvas.drawArc(rect, wheelRotation + i * sweep, sweep, true, linePaint)
        }
        // labels
        for (i in LABELS.indices) {
            canvas.save()
            val ang = wheelRotation + i * sweep + sweep / 2f
            canvas.rotate(ang, cx, cy)
            val lines = LABELS[i].split("\n")
            var ty = cy - (lines.size - 1) * 18f + 12f
            for (ln in lines) { canvas.drawText(ln, cx + r * 0.68f, ty, textPaint); ty += 36f }
            canvas.restore()
        }
        canvas.drawCircle(cx, cy, r * 0.18f, hubPaint)
        canvas.drawText("SPIN", cx, cy + 10f, hubText)
    }
}
