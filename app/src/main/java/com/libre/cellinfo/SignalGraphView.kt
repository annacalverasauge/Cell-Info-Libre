package com.libre.cellinfo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Gráfica simple del histórico de señal de la celda principal (dBm). */
class SignalGraphView(ctx: Context) : View(ctx) {

    private val data = ArrayList<Int>()
    private val maxPoints = 150
    private val top = -40f
    private val bottom = -140f
    private val d = resources.displayMetrics.density

    private val bgPaint = Paint().apply { color = 0xFF1A2027.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF; strokeWidth = 1f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt(); textSize = 10 * d
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22 * d; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.5f * d; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    fun add(v: Int?) {
        if (v == null || v < -160 || v > -20) return
        data.add(v)
        if (data.size > maxPoints) data.removeAt(0)
        invalidate()
    }

    private fun y(v: Int, h: Float): Float {
        val c = v.toFloat().coerceIn(bottom, top)
        return (top - c) / (top - bottom) * h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val left = 34 * d
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        var g = -40
        while (g >= -140) {
            val yy = y(g, h)
            canvas.drawLine(left, yy, w, yy, gridPaint)
            canvas.drawText("$g", 4 * d, (yy + 4 * d).coerceIn(11 * d, h - 3 * d), labelPaint)
            g -= 20
        }

        if (data.isEmpty()) {
            canvas.drawText("Esperando datos…", left + 8 * d, h / 2, labelPaint)
            return
        }

        val step = (w - left) / (maxPoints - 1)
        for (i in 1 until data.size) {
            linePaint.color = colorFor(data[i])
            canvas.drawLine(
                left + (i - 1) * step, y(data[i - 1], h),
                left + i * step, y(data[i], h), linePaint
            )
        }
        if (data.size == 1) {
            linePaint.color = colorFor(data[0])
            canvas.drawPoint(left, y(data[0], h), linePaint)
        }

        val last = data.last()
        bigPaint.color = colorFor(last)
        canvas.drawText("$last dBm", w - 8 * d, 26 * d, bigPaint)
    }

    companion object {
        fun colorFor(v: Int): Int = when {
            v >= -90 -> 0xFF4CAF50.toInt()   // verde
            v >= -105 -> 0xFFFFEB3B.toInt()  // amarillo
            v >= -115 -> 0xFFFF9800.toInt()  // naranja
            else -> 0xFFF44336.toInt()       // rojo
        }
    }
}
