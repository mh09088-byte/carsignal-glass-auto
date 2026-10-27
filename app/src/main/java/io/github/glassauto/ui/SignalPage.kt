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
import io.github.glassauto.core.LiveSignal
import io.github.glassauto.core.SignalAdvice
import io.github.glassauto.core.SignalAdvisor
import io.github.glassauto.core.SignalColor
import io.github.glassauto.core.SignalState
import io.github.glassauto.core.toState
import io.github.glassauto.signal.LiveSignalClient
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
        // 차는 도로 안에, 거리·속도 글자는 도로 오른쪽에 두어 겹치지 않게 한다
        val y = 300f + (s.distanceMeters / 400.0 * 330.0).toFloat().coerceIn(0f, 330f) + 60f
        p.color = Palette.TEXT; c.drawRoundRect(RectF(220f, y - 60f, 280f, y + 60f), 16f, 16f, p)
        val d = s.distanceMeters.toInt()
        text(c, if (d > 0) "${d}m 앞" else "정지선 정차", 420f, y - 6f, 20f, Palette.TEXT, true)
        text(c, if (speedKmh >= 1) "시속 ${speedKmh.toInt()}km" else "정차 중", 420f, y + 22f, 16f, Palette.MUTED)
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
 * 시연에서는 교차로 접근 → 정차 → 출발 시나리오를 재생하고, 인증키가 있으면
 * 울산 C-ITS 실시간 신호를 받아 같은 화면에 보여 준다(차량 접근은 시뮬레이션).
 */
class SignalPage(private val ctx: Context) {
    private val view = SignalView(ctx)
    private val advice = ctx.label("", 20f, Palette.TEXT, true)
    private val status = ctx.label("", 15f, Palette.MUTED)
    private val button: TextView
    private val liveButton: TextView
    private val source = ctx.label("데이터: 시연 시나리오", 13f, Palette.MUTED)
    private val handler = Handler(Looper.getMainLooper())
    private val client = LiveSignalClient(ctx)
    private var running = false
    private var live = false
    private var liveSignal: LiveSignal? = null
    private var fetching = false
    private var s = initial()
    private var speed = 45.0
    private val limit = 60

    val root: View

    init {
        button = ctx.pill("교차로 접근 시나리오 재생 (시연용)") { if (running && !live) stop() else start() }
        liveButton = ctx.pill("실시간 신호 받기 (울산 C-ITS)") { if (running && live) stop() else startLive() }
        val side = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(8), 0, 0, ctx.dp(16)) }
        side.addView(ctx.card(ctx.label("신호등 타임", 13f, Palette.MUTED), advice, status, source))
        side.addView(gap(ctx))
        side.addView(ctx.card(ctx.label("이렇게 동작해요", 13f, Palette.MUTED),
            ctx.label("· 전방 교차로 신호의 남은 시간을 보여 줍니다", 15f),
            ctx.label("· 5초 이하에서는 숫자를 숨깁니다 (예측 출발 방지)", 15f),
            ctx.label("· 주행 중에는 초록불에 통과하는 권장 속도를 제한속도 안에서 안내합니다", 15f),
            ctx.label("· 정차 중 초록불로 바뀌면 출발을 알려 줍니다", 15f),
            ctx.label("· 데이터: 공공 실시간 신호 API (서울 T Data C-ITS 등)", 13f, Palette.MUTED)))
        side.addView(gap(ctx))
        side.addView(button)
        if (client.available) { side.addView(gap(ctx, 8)); side.addView(liveButton) }
        root = splitView(ctx, view, side)
        render()
    }

    private fun initial() = SignalState(SignalColor.RED, 18.0, greenSec = 30.0, yellowSec = 3.0, redSec = 40.0, distanceMeters = 380.0)

    fun start() {
        stop(); running = true; live = false; s = initial(); speed = 45.0
        button.text = "시나리오 정지"; source.text = "데이터: 시연 시나리오"; handler.post(tick)
    }

    /** 실시간 신호: 신호 색·남은 시간은 공공 API 실측값, 차량 접근은 시뮬레이션(실시간 속도). */
    fun startLive() {
        stop(); running = true; live = true; liveSignal = null; s = initial(); speed = 45.0
        liveButton.text = "실시간 신호 정지"; source.text = "실시간 신호 받는 중…"; refresh(); handler.post(tick)
    }

    fun stop() {
        running = false; handler.removeCallbacks(tick)
        button.text = "교차로 접근 시나리오 재생 (시연용)"; liveButton.text = "실시간 신호 받기 (울산 C-ITS)"
    }

    private fun refresh() {
        if (fetching) return
        fetching = true
        client.fetch { r ->
            fetching = false
            r.onSuccess { liveSignal = it; source.text = "실시간 · ${it.source} · ${it.intersection} (신호 주기 길이는 추정값)" }
             .onFailure { source.text = "실시간 신호를 받지 못했습니다 · ${it.message}" }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val dt = if (live) 0.2 else 0.2 * TIME_SCALE
            if (live && !liveTick()) { handler.postDelayed(this, 200); return }
            val red = s.color != SignalColor.GREEN
            // 빨간·노란불이면 정지선 앞에서 멈추고, 초록불이면 제한속도 안에서 진행
            speed = when {
                red && s.distanceMeters < 25 -> 0.0
                red && s.distanceMeters < 80 -> maxOf(speed - 12 * dt, 8.0)
                !red && speed < 40 -> speed + 10 * dt
                else -> speed
            }
            val moved = speed / 3.6 * dt
            s = if (live) s.copy(distanceMeters = (s.distanceMeters - moved).coerceAtLeast(0.0)) else SignalAdvisor.tick(s, dt, moved)
            render()
            if (s.distanceMeters <= 0.0) {
                if (live) { s = s.copy(distanceMeters = 380.0); speed = 45.0 }   // 실시간은 다음 접근을 이어서 보여 준다
                else { stop(); status.text = "교차로 통과 완료"; return }
            }
            handler.postDelayed(this, 200)
        }
    }

    /** 실측 신호를 지금 시각 기준으로 맞춘다. 남은 시간이 다 되면 새로 받는다. */
    private fun liveTick(): Boolean {
        val ls = liveSignal ?: run { refresh(); return false }
        val now = ls.toState(System.currentTimeMillis(), s.distanceMeters, s)
        if (now == null) { liveSignal = null; refresh(); return false }
        s = now; return true
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
