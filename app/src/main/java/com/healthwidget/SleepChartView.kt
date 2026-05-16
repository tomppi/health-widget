package com.healthwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.health.connect.client.records.SleepSessionRecord

/**
 * Draws a hypnogram — a step chart showing sleep stage depth over time.
 * Deep sleep at the bottom, awake at the top, same as standard sleep charts.
 *
 * The companion [drawChartOnCanvas] function is shared with the widget bitmap renderer.
 */
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

        // Y level for each stage type (0 = top/awake, 3 = bottom/deep)
        private fun stageLevel(type: Int): Int = when (type) {
            SleepSessionRecord.STAGE_TYPE_AWAKE       -> 0
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED  -> 0
            SleepSessionRecord.STAGE_TYPE_REM         -> 1
            SleepSessionRecord.STAGE_TYPE_LIGHT       -> 2
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
            textAlign = Paint.Align.LEFT
        }

        private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
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

            val padLeft   = 8f
            val padRight  = 8f
            val padTop    = 8f
            val padBottom = 28f  // room for time labels

            val chartW = w - padLeft - padRight
            val chartH = h - padTop - padBottom

            val totalMs  = stages.maxOf { it.endMs }.toFloat().coerceAtLeast(1f)
            val levels   = 4f // awake, REM, light, deep
            val levelH   = chartH / levels

            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            // Draw each stage as a rounded bar at its depth level
            stages.forEach { stage ->
                val x1 = padLeft + (stage.startMs / totalMs) * chartW
                val x2 = padLeft + (stage.endMs   / totalMs) * chartW
                val level = stageLevel(stage.type)
                val y1 = padTop + level * levelH
                val y2 = padTop + chartH  // bars always extend to the bottom

                barPaint.color = stageColor(stage.type)
                barPaint.alpha = 220

                val rect = RectF(x1, y1, x2.coerceAtLeast(x1 + 2f), y2)
                canvas.drawRoundRect(rect, 3f, 3f, barPaint)
            }

            // Divider lines between levels
            val gridPaint = Paint().apply {
                color = Color.parseColor("#22FFFFFF")
                strokeWidth = 1f
            }
            for (i in 1..3) {
                val y = padTop + i * levelH
                canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
            }

            // Y-axis labels
            val yLabels = listOf("Awake", "REM", "Light", "Deep")
            yLabels.forEachIndexed { i, label ->
                val y = padTop + i * levelH + labelPaint.textSize + 2f
                canvas.drawText(label, padLeft + 2f, y, labelPaint)
            }

            // X-axis: start and end time labels
            val totalHours  = (totalMs / 60_000 / 60).toInt()
            val totalMins   = (totalMs / 60_000 % 60).toInt()
            val durationTxt = "${totalHours}h ${totalMins}m"

            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#66FFFFFF")
                textSize = 20f
            }
            canvas.drawText("0h", padLeft, h - 4f, timePaint)
            timePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(durationTxt, padLeft + chartW, h - 4f, timePaint)
        }
    }
}
