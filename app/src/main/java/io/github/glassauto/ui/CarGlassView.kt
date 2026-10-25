package io.github.glassauto.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import io.github.glassauto.core.GlassDecision
import io.github.glassauto.core.GlassId
import io.github.glassauto.core.Reason
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 위에서 본 차량과 유리별 투과율. 유리를 누르면 선택된다.
 * 스마트글라스가 없는 차량에서는 이 '가상 유리'로 정책 결과를 보여 준다.
 */
class CarGlassView(context: Context) : View(context) {

    var decisions: Map<GlassId, GlassDecision> = emptyMap(); set(v) { field = v; invalidate() }
    var selected: GlassId? = null; set(v) { field = v; invalidate() }
    /** 차량 기준 태양 상대각(도, 0=정면, +=오른쪽)과 고도. 고도 0 이하면 표시하지 않는다. */
    var sun: Pair<Double, Double>? = null; set(v) { field = v; invalidate() }
    var onSelect: ((GlassId) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hit = mutableMapOf<GlassId, RectF>()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // 기준 좌표계 600x700 을 화면에 맞춰 축소
        val s = min(w / 600f, h / 700f); c.save()
        c.translate((w - 600f * s) / 2f, (h - 700f * s) / 2f); c.scale(s, s)
        hit.clear()

        val cx = 300f; val cy = 360f
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Palette.LINE
        c.drawCircle(cx, cy, 270f, p)

        // 태양
        sun?.let { (rel, elev) ->
            if (elev > 0) {
                val r = Math.toRadians(rel); val sx = cx + 270f * sin(r).toFloat(); val sy = cy - 270f * cos(r).toFloat()
                p.style = Paint.Style.FILL; p.color = Palette.AMBER; p.alpha = 50; c.drawCircle(sx, sy, 38f, p)
                p.alpha = 255; c.drawCircle(sx, sy, 24f, p)
                text(c, "고도 ${elev.toInt()}°", sx, sy + 52f, 20f, Palette.AMBER)
            }
        }

        // 차체
        p.style = Paint.Style.FILL; p.color = 0xFF2A3643.toInt(); c.drawRoundRect(RectF(170f, 90f, 430f, 640f), 100f, 100f, p)
        p.color = 0xFF334252.toInt(); c.drawRoundRect(RectF(182f, 104f, 418f, 626f), 90f, 90f, p)

        glass(c, GlassId.WINDSHIELD, trapezoid(195f, 225f, 405f, 225f, 382f, 160f, 218f, 160f), RectF(195f, 160f, 405f, 225f))
        glass(c, GlassId.ROOF, roundRect(RectF(225f, 265f, 375f, 395f), 16f), RectF(225f, 265f, 375f, 395f))
        glass(c, GlassId.REAR, trapezoid(210f, 545f, 390f, 545f, 375f, 595f, 225f, 595f), RectF(210f, 545f, 390f, 595f))
        glass(c, GlassId.ROW1_LEFT, roundRect(RectF(156f, 250f, 184f, 380f), 8f), RectF(120f, 250f, 190f, 380f))
        glass(c, GlassId.ROW2_LEFT, roundRect(RectF(156f, 395f, 184f, 515f), 8f), RectF(120f, 395f, 190f, 515f))
        glass(c, GlassId.ROW1_RIGHT, roundRect(RectF(416f, 250f, 444f, 380f), 8f), RectF(410f, 250f, 480f, 380f))
        glass(c, GlassId.ROW2_RIGHT, roundRect(RectF(416f, 395f, 444f, 515f), 8f), RectF(410f, 395f, 480f, 515f))

        // 라벨
        label(c, GlassId.WINDSHIELD, cx, 192f); label(c, GlassId.ROOF, cx, 330f); label(c, GlassId.REAR, cx, 572f)
        label(c, GlassId.ROW1_LEFT, 90f, 310f); label(c, GlassId.ROW2_LEFT, 90f, 455f)
        label(c, GlassId.ROW1_RIGHT, 510f, 310f); label(c, GlassId.ROW2_RIGHT, 510f, 455f)
        text(c, "▲ 진행 방향", cx, 70f, 18f, Palette.MUTED)
        c.restore()
    }

    private fun glass(c: Canvas, id: GlassId, path: Path, touch: RectF) {
        val pct = decisions[id]?.percent ?: 100
        p.style = Paint.Style.FILL; p.color = Palette.tint(pct); c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        if (selected == id) { p.color = Palette.ACCENT; p.strokeWidth = 6f } else { p.color = Palette.BG; p.strokeWidth = 2f }
        c.drawPath(path, p)
        hit[id] = touch
    }

    private fun label(c: Canvas, id: GlassId, x: Float, y: Float) {
        val d = decisions[id]
        val inside = id == GlassId.WINDSHIELD || id == GlassId.ROOF || id == GlassId.REAR
        val light = (d?.percent ?: 100) > 55
        val color = when {
            d?.reason == Reason.MANUAL -> Palette.AMBER
            inside && light -> Palette.INK
            else -> Palette.TEXT
        }
        val tag = when (d?.reason) {
            Reason.MANUAL -> " [고정]"; Reason.LEGAL -> " [법규]"; Reason.VISIBILITY -> " [시야]"; Reason.PRIVACY -> " [P]"; else -> ""
        }
        text(c, id.label + tag, x, y, 20f, color, bold = true)
        text(c, "투과율 ${d?.percent ?: 100}%", x, y + 24f, 17f, if (inside && light) Palette.INK else Palette.MUTED)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = bold; c.drawText(s, x, y, p); p.isFakeBoldText = false
    }

    private fun trapezoid(vararg pts: Float) = Path().apply {
        moveTo(pts[0], pts[1]); var i = 2
        while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
        close()
    }

    private fun roundRect(r: RectF, rad: Float) = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val s = min(width / 600f, height / 700f)
        val x = (e.x - (width - 600f * s) / 2f) / s; val y = (e.y - (height - 700f * s) / 2f) / s
        hit.entries.firstOrNull { it.value.contains(x, y) }?.let { onSelect?.invoke(it.key) }
        return true
    }
}
