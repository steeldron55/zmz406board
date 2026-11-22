package ua.zmz406.board

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class GaugeView(ctx: Context) : View(ctx) {
    var minV = 0f
    var maxV = 100f
    var amberFrom = Float.MAX_VALUE
    var redFrom = Float.MAX_VALUE
    var baseColor = 0xFF4C9AFF.toInt()
    var unit = ""
    var suffix = ""
    var labels: List<String> = emptyList()

    private var shown = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        txt.textAlign = Paint.Align.CENTER
        txt.isFakeBoldText = true
    }

    fun setValue(v: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, v).apply {
            duration = 250
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun colorFor(v: Float): Int = when {
        v >= redFrom -> 0xFFFF5A5F.toInt()
        v >= amberFrom -> 0xFFF5A524.toInt()
        else -> baseColor
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = s * 0.065f
        val r = s / 2f - sw
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        val span = maxV - minV

        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF262E36.toInt()
        c.drawArc(rect, 135f, 270f, false, p)

        if (redFrom < maxV) {
            val a = 270f * (redFrom - minV) / span
            p.strokeCap = Paint.Cap.BUTT
            p.color = 0xD9FF5A5F.toInt()
            c.drawArc(rect, 135f + a, 270f - a, false, p)
            p.strokeCap = Paint.Cap.ROUND
        }

        val frac = ((shown - minV) / span).coerceIn(0f, 1f)
        if (frac > 0.002f) {
            p.color = colorFor(shown)
            c.drawArc(rect, 135f, 270f * frac, false, p)
        }

        val n = labels.size
        if (n > 1) {
            p.strokeWidth = sw * 0.16f
            p.strokeCap = Paint.Cap.BUTT
            p.color = 0xFF6C7A88.toInt()
            txt.color = 0xFFA9B3BC.toInt()
            txt.textSize = s * 0.045f
            for (i in 0 until n) {
                val ang = Math.toRadians(135.0 + 270.0 * i / (n - 1))
                val ca = cos(ang).toFloat()
                val sa = sin(ang).toFloat()
                c.drawLine(cx + ca * (r - sw * 1.9f), cy + sa * (r - sw * 1.9f),
                    cx + ca * (r - sw * 1.1f), cy + sa * (r - sw * 1.1f), p)
                c.drawText(labels[i], cx + ca * (r - sw * 3.1f),
                    cy + sa * (r - sw * 3.1f) + txt.textSize * 0.35f, txt)
            }
        }

        txt.color = if (shown >= redFrom) 0xFFFF8A8E.toInt() else 0xFFF2F5F7.toInt()
        txt.textSize = s * 0.19f
        c.drawText(Math.round(shown).toString() + suffix, cx, cy + s * 0.07f, txt)
        txt.color = 0xFFA9B3BC.toInt()
        txt.textSize = s * 0.055f
        c.drawText(unit, cx, cy + s * 0.17f, txt)
    }
}
