package com.healthwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.health.connect.client.records.SleepSessionRecord

class SleepChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var sleepStages: List<SleepStageData> = emptyList()
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawChartOnCanvas(canvas, sleepStages, width.toFloat(), height.toFloat())
    }

    companion object {

        private fun stageLevel(type: Int): Int = when (type) {
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> 0
            SleepSessionRecord.STAGE_TYPE_REM         -> 1
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_SLEEPING    -> 2
            SleepSessionRecord.STAGE_TYPE_DEEP        -> 3
            else                                      -> 2
        }

        private fun stageColor(type: Int): Int = when (type) {
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> Color.parseColor("#78909C")
            SleepSessionRecord.STAGE_TYPE_REM         -> Color.parseColor("#26C6DA")
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_SLEEPING    -> Color.parseColor("#9575CD")
            SleepSessionRecord.STAGE_TYPE_DEEP        -> Color.parseColor("#3949AB")
            else                                      -> Color.parseColor("#9575CD")
        }

        private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FFFFFF")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99FFFFFF")
            textSize = 22f
        }

        fun drawChartOnCanvas(
            canvas: Canvas,
            stages: List<SleepStageData>,
            w: Float,
            h: Float
        ) {
            if (stages.isEmpty()) {
                canvas.drawText("No sleep data", w / 2f, h / 2f, emptyPaint)
                return
            }

            val padLeft   = 48f   // room for labels
            val padRight  = 8f
            val padTop    = 4f
            val padBottom = 24f   // room for time labels

            val chartW = w - padLeft - padRight
            val chartH = h - padTop - padBottom
            val levels  = 4f
            val levelH  = chartH / levels

            val totalMs = stages.maxOf { it.endMs }.toFloat().coerceAtLeast(1f)

            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            // Draw each stage as a bar occupying only its own row
            stages.forEach { stage ->
                val x1    = padLeft + (stage.startMs / totalMs) * chartW
                val x2    = padLeft + (stage.endMs   / totalMs) * chartW
                val level = stageLevel(stage.type)

                // ✅ y2 = bottom of this level's row only, not the full chart bottom
                val y1 = padTop + level * levelH
                val y2 = padTop + (level + 1) * levelH

                barPaint.color = stageColor(stage.type)
                val rect = RectF(x1, y1 + 1f, x2.coerceAtLeast(x1 + 2f), y2 - 1f)
                canvas.drawRoundRect(rect, 3f, 3f, barPaint)
            }

            // Divider lines between rows
            val gridPaint = Paint().apply {
                color = Color.parseColor("#22FFFFFF")
                strokeWidth = 1f
            }
            for (i in 0..4) {
                val y = padTop + i * levelH
                canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
            }

            // Y-axis labels
            listOf("Awake", "REM", "Light", "Deep").forEachIndexed { i, label ->
                val y = padTop + i * levelH + levelH * 0.65f
                canvas.drawText(label, 2f, y, labelPaint)
            }

            // X-axis duration labels
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#66FFFFFF")
                textSize = 20f
            }
            val totalMins  = (totalMs / 60_000).toLong()
            val durationTxt = "${totalMins / 60}h ${totalMins % 60}m"
            canvas.drawText("0h", padLeft, h - 4f, timePaint)
            timePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(durationTxt, padLeft + chartW, h - 4f, timePaint)
        }
    }
}
