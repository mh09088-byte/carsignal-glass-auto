package io.github.glassauto.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import io.github.glassauto.core.EmergencyStage
import kotlin.math.min

/**
 * 긴급차량 기능의 차로 화면. 유리 그림 없이 내 차·구급차·양보 방향만 보여 준다.
 * [gapMeters] 양수 = 구급차가 뒤쪽, 음수 = 구급차가 앞쪽(통과).
 */
class LaneView(context: Context) : View(context) {
    var stage: EmergencyStage = EmergencyStage.NONE; set(v) { field = v; invalidate() }
    var gapMeters: Double? = null; set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = min(width / 500f, height / 760f); c.save()
        c.translate((width - 500f * s) / 2f, (height - 760f * s) / 2f); c.scale(s, s)

        p.style = Paint.Style.FILL; p.color = 0xFF151E28.toInt(); c.drawRect(50f, 40f, 450f, 740f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFFC9D4DE.toInt()
        c.drawLine(52f, 40f, 52f, 740f, p); c.drawLine(448f, 40f, 448f, 740f, p)
        p.color = Palette.MUTED; p.strokeWidth = 3f; p.pathEffect = DashPathEffect(floatArrayOf(26f, 22f), 0f)
        c.drawLine(183f, 40f, 183f, 740f, p); c.drawLine(317f, 40f, 317f, 740f, p); p.pathEffect = null

        val passed = stage == EmergencyStage.PASSED
        val myX = if (passed) 383f else 250f
        drawCar(c, myX, 400f)

        val gap = gapMeters
        if (gap != null) {
            // 300m = 화면 260px 비율, 화면 밖이면 가장자리에 화살표로 표시
            val y = 400f + (gap / 300.0 * 260.0).toFloat()
            if (y in 60f..720f) drawAmbulance(c, 250f, y) else edgeHint(c, y > 720f, gap)
        }
        if (stage == EmergencyStage.ALERT) yieldArrow(c)
        if (gap == null) text(c, "주변에 출동 중인 긴급차량이 없습니다", 250f, 690f, 19f, Palette.MUTED)
        c.restore()
    }

    private fun drawCar(c: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL; p.color = Palette.TEXT; c.drawRoundRect(RectF(x - 38f, y - 70f, x + 38f, y + 70f), 18f, 18f, p)
        p.color = 0xFF5B7083.toInt()
        c.drawRoundRect(RectF(x - 28f, y - 48f, x + 28f, y - 18f), 8f, 8f, p)
        c.drawRoundRect(RectF(x - 28f, y + 30f, x + 28f, y + 52f), 6f, 6f, p)
        text(c, "내 차", x, y + 100f, 19f, Palette.TEXT, true)
    }

    private fun drawAmbulance(c: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL
        p.color = Palette.RED; p.alpha = 45; c.drawCircle(x, y - 60f, 46f, p); p.alpha = 255
        p.color = 0xFFF8FAFC.toInt(); c.drawRoundRect(RectF(x - 40f, y - 72f, x + 40f, y + 78f), 14f, 14f, p)
        p.color = 0xFFE11D48.toInt(); c.drawRect(x - 40f, y - 8f, x + 40f, y + 10f, p)
        p.color = Palette.RED; c.drawRect(x - 32f, y - 68f, x - 2f, y - 56f, p)
        p.color = 0xFF3B82F6.toInt(); c.drawRect(x + 2f, y - 68f, x + 32f, y - 56f, p)
        text(c, "119", x, y + 46f, 18f, 0xFFB91C1C.toInt(), true)
        val gap = gapMeters ?: 0.0
        val tag = if (gap >= 0) "구급차 · ${gap.toInt()}m 뒤" else "통과 완료"
        text(c, tag, x, y + 104f, 19f, Palette.RED, true)
    }

    private fun edgeHint(c: Canvas, behind: Boolean, gap: Double) {
        if (!behind) return
        text(c, "︾", 250f, 712f, 34f, Palette.AMBER, true)
        text(c, "뒤쪽 ${"%.1f".format(gap / 1000)}km · 구급차", 250f, 740f, 19f, Palette.AMBER, true)
    }

    private fun yieldArrow(c: Canvas) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = Palette.ACCENT; p.strokeCap = Paint.Cap.ROUND
        val path = Path().apply { moveTo(296f, 380f); cubicTo(350f, 360f, 360f, 300f, 383f, 250f) }
        c.drawPath(path, p)
        c.drawLine(383f, 250f, 366f, 270f, p); c.drawLine(383f, 250f, 396f, 274f, p)
        p.style = Paint.Style.FILL; c.drawRoundRect(RectF(325f, 195f, 441f, 229f), 17f, 17f, p)
        text(c, "이쪽으로", 383f, 219f, 18f, Palette.INK, true)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = bold; c.drawText(s, x, y, p); p.isFakeBoldText = false
    }
}
