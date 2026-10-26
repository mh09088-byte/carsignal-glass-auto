package io.github.glassauto.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.glassauto.core.SignalAdvice
import io.github.glassauto.core.SignalAdvisor
import io.github.glassauto.core.SignalColor
import io.github.glassauto.core.SignalState
import kotlin.math.min

/** 신호등 그림 + 정지선까지 도로. */
class SignalView(context: Context) : View(context) {
    var state: SignalState? = null; set(v) { field = v; invalidate() }
    var speedKmh = 0.0; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = state ?: return
        val k = min(width / 500f, height / 760f); c.save()
        c.translate((width - 500f * k) / 2f, (height - 760f * k) / 2f); c.scale(k, k)

        // 신호등
        p.style = Paint.Style.FILL; p.color = 0xFF1B2633.toInt(); c.drawRoundRect(RectF(150f, 30f, 350f, 120f), 40f, 40f, p)
        listOf(SignalColor.RED to 0xFFFF4D4F, SignalColor.YELLOW to 0xFFFFB020, SignalColor.GREEN to 0xFF34D399).forEachIndexed { i, (col, argb) ->
            p.color = if (s.color == col) argb.toInt() else 0xFF2A3643.toInt()
            c.drawCircle(195f + i * 55f, 75f, 22f, p)
        }
        // 남은 시간
        text(c, SignalAdvisor.countdownText(s), 250f, 200f, 44f, colorOf(s.color), true)

        // 도로와 정지선
        p.color = 0xFF151E28.toInt(); c.drawRect(160f, 240f, 340f, 740f, p)
        p.color = 0xFFE8EEF4.toInt(); c.drawRect(160f, 268f, 340f, 276f, p)
        text(c, "정지선", 250f, 260f, 16f, Palette.MUTED)
        val y = 276f + (s.distanceMeters / 400.0 * 420.0).toFloat().coerceIn(0f, 420f) + 70f
        p.color = Palette.TEXT; c.drawRoundRect(RectF(220f, y - 60f, 280f, y + 60f), 16f, 16f, p)
        text(c, "${s.distanceMeters.toInt()}m · 시속 ${speedKmh.toInt()}km", 250f, min(y + 95f, 735f), 18f, Palette.TEXT, true)
        c.restore()
    }

    private fun colorOf(c: SignalColor) = when (c) { SignalColor.RED -> Palette.RED; SignalColor.YELLOW -> Palette.AMBER; SignalColor.GREEN -> Palette.GREEN }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = bold; c.drawText(s, x, y, p); p.isFakeBoldText = false
    }
}

/**
 * 신호등 타임 탭. 실제 서비스는 공공 실시간 신호 API(서울 T Data C-ITS 신호 잔여시간,
 * 울산 C-ITS, 행정안전부 교통안전 신호등 실시간 정보)를 받아 같은 [SignalState] 로 넘긴다.
 * 시연에서는 교차로 접근 → 정차 → 출발 시나리오를 재생한다.
 */
class SignalPage(private val ctx: Context) {
    private val view = SignalView(ctx)
    private val advice = ctx.label("", 20f, Palette.TEXT, true)
    private val status = ctx.label("", 15f, Palette.MUTED)
    private val button: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var s = initial()
    private var speed = 45.0
    private val limit = 60

    val root: View

    init {
        button = ctx.pill("교차로 접근 시나리오 재생 (시연용)") { if (running) stop() else start() }
        val side = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(8), 0, 0, ctx.dp(16)) }
        side.addView(ctx.card(ctx.label("신호등 타임", 13f, Palette.MUTED), advice, status))
        side.addView(gap(ctx))
        side.addView(ctx.card(ctx.label("이렇게 동작해요", 13f, Palette.MUTED),
            ctx.label("· 전방 교차로 신호의 남은 시간을 보여 줍니다", 15f),
            ctx.label("· 5초 이하에서는 숫자를 숨깁니다 (예측 출발 방지)", 15f),
            ctx.label("· 주행 중에는 초록불에 통과하는 권장 속도를 제한속도 안에서 안내합니다", 15f),
            ctx.label("· 정차 중 초록불로 바뀌면 출발을 알려 줍니다", 15f),
            ctx.label("· 데이터: 공공 실시간 신호 API (서울 T Data C-ITS 등)", 13f, Palette.MUTED)))
        side.addView(gap(ctx))
        side.addView(button)
        root = splitView(ctx, view, side)
        render()
    }

    private fun initial() = SignalState(SignalColor.RED, 18.0, greenSec = 30.0, yellowSec = 3.0, redSec = 40.0, distanceMeters = 380.0)

    fun start() { running = true; s = initial(); speed = 45.0; button.text = "시나리오 정지"; handler.post(tick) }

    fun stop() { running = false; handler.removeCallbacks(tick); button.text = "교차로 접근 시나리오 재생 (시연용)" }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val dt = 0.2 * TIME_SCALE
            val red = s.color != SignalColor.GREEN
            // 빨간·노란불이면 정지선 앞에서 멈추고, 초록불이면 제한속도 안에서 진행
            speed = when {
                red && s.distanceMeters < 25 -> 0.0
                red && s.distanceMeters < 80 -> maxOf(speed - 12 * dt, 8.0)
                !red && speed < 40 -> speed + 10 * dt
                else -> speed
            }
            val moved = speed / 3.6 * dt
            s = SignalAdvisor.tick(s, dt, moved)
            render()
            if (s.distanceMeters <= 0.0) { stop(); status.text = "교차로 통과 완료"; return }
            handler.postDelayed(this, 200)
        }
    }

    private fun render() {
        view.state = s; view.speedKmh = speed
        val a = SignalAdvisor.advise(s, speed, limit)
        advice.text = when {
            speed < 1 && s.color == SignalColor.GREEN -> "초록불입니다 · 출발하세요"
            speed < 1 -> "정차 중 · ${SignalAdvisor.countdownText(s)}"
            else -> a.text
        }
        advice.setTextColor(when (a) { is SignalAdvice.Keep -> Palette.GREEN; is SignalAdvice.Adjust -> Palette.ACCENT; is SignalAdvice.Coast -> Palette.AMBER })
        status.text = "정지선까지 ${s.distanceMeters.toInt()}m · 현재 시속 ${speed.toInt()}km · 제한속도 ${limit}km"
    }

    companion object { private const val TIME_SCALE = 2.0 }
}
