package com.healthwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * A simple custom View that draws a line chart of heart rate readings.
 * Used in MainActivity. The widget uses the companion [drawChartOnCanvas]
 * function to render the same chart into a Bitmap.
 */
class HeartRateChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var heartRates: List<Int> = emptyList()
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawChartOnCanvas(canvas, heartRates, width.toFloat(), height.toFloat())
    }

    companion object {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6B81")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FF6B81")
            style = Paint.Style.FILL
        }

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6B81")
            style = Paint.Style.FILL
        }

        private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D1525")
            style = Paint.Style.FILL
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1AFFFFFF")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FFFFFF")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        /**
         * Draws the chart onto any Canvas — used by both the View and the widget bitmap.
         */
        fun drawChartOnCanvas(
            canvas: Canvas,
            heartRates: List<Int>,
            w: Float,
            h: Float
        ) {
            if (heartRates.isEmpty()) {
                canvas.drawText("No data", w / 2f, h / 2f + 10f, emptyPaint)
                return
            }

            val padLeft = 16f
            val padRight = 16f
            val padTop = 36f    // room for labels above dots
            val padBottom = 16f

            val chartW = w - padLeft - padRight
            val chartH = h - padTop - padBottom

            val min = (heartRates.min() - 5).coerceAtLeast(40)
            val max = (heartRates.max() + 5)
            val range = (max - min).toFloat().coerceAtLeast(1f)

            fun xOf(i: Int) = padLeft + i * (chartW / (heartRates.size - 1).coerceAtLeast(1))
            fun yOf(bpm: Int) = padTop + chartH * (1f - (bpm - min) / range)

            // Grid lines (3 horizontal)
            for (step in 0..2) {
                val y = padTop + chartH * step / 2f
                canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
            }

            if (heartRates.size == 1) {
                // Single point — just draw a dot and label
                val x = w / 2f
                val y = h / 2f
                canvas.drawCircle(x, y, 8f, dotBorderPaint.apply { this.strokeWidth = 4f })
                canvas.drawCircle(x, y, 6f, dotPaint)
                canvas.drawText("${heartRates[0]}", x, y - 16f, labelPaint)
                return
            }

            // Fill path
            val fillPath = Path()
            fillPath.moveTo(xOf(0), padTop + chartH)
            heartRates.forEachIndexed { i, bpm -> fillPath.lineTo(xOf(i), yOf(bpm)) }
            fillPath.lineTo(xOf(heartRates.lastIndex), padTop + chartH)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)

            // Line path
            val linePath = Path()
            heartRates.forEachIndexed { i, bpm ->
                if (i == 0) linePath.moveTo(xOf(i), yOf(bpm))
                else linePath.lineTo(xOf(i), yOf(bpm))
            }
            canvas.drawPath(linePath, linePaint)

            // Dots and labels
            heartRates.forEachIndexed { i, bpm ->
                val x = xOf(i)
                val y = yOf(bpm)
                canvas.drawCircle(x, y, 10f, dotBorderPaint)
                canvas.drawCircle(x, y, 7f, dotPaint)
                canvas.drawText("$bpm", x, y - 14f, labelPaint)
            }
        }
    }
}
