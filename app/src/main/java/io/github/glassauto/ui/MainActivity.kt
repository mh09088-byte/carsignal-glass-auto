package io.github.glassauto.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import io.github.glassauto.core.EmergencyMatcher
import io.github.glassauto.core.EmergencyStage
import io.github.glassauto.core.Gear
import io.github.glassauto.core.GlassDecision
import io.github.glassauto.core.GlassId
import io.github.glassauto.core.GlassInputs
import io.github.glassauto.core.GlassPolicy
import io.github.glassauto.core.LegalGuard
import io.github.glassauto.core.Reason
import io.github.glassauto.core.SunEngine
import io.github.glassauto.emergency.AlertController
import io.github.glassauto.emergency.EmergencySimulator
import io.github.glassauto.signal.LiveSignalClient
import io.github.glassauto.vehicle.PleosVehicleGateway
import io.github.glassauto.vehicle.SeatPos
import io.github.glassauto.vehicle.SimulatedVehicleGateway
import io.github.glassauto.vehicle.VehicleCapabilities
import io.github.glassauto.vehicle.VehicleGateway
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 카시그널 (글래스 오토) 메인 화면. 차 안팎의 '신호'를 읽어 주는 세 가지 현재 기능과 미래 기능.
 *  - 긴급차량: 접근 예고·볼륨 낮춤·조명 경고·양보 안내
 *  - 신호등: 남은 시간·초록불 통과 권장 속도
 *  - 정비: 차량 자가 점검·경고등 해석·긴급도
 *  - 스마트글라스(미래): 오토 틴트, 투과율 1% 조절 — 스마트글라스 탑재 차량 출시 후 도입
 */
class MainActivity : Activity(), VehicleGateway.Listener {

    // ---- 차량 연결 ----
    private lateinit var gateway: VehicleGateway
    private var caps = VehicleCapabilities("연결 중")

    // ---- 유리 기능 상태 ----
    private val legal = LegalGuard()
    private val policy = GlassPolicy(legal)
    private var gear = Gear.DRIVE
    private var heading = 90.0                    // 동쪽으로 출근
    private var minutes = 7 * 60 + 40             // 07:40
    private var wiper = false
    private var privacy = false
    private val manual = mutableMapOf<GlassId, Int>()
    private val applied = mutableMapOf<GlassId, Int?>()
    private val sent = mutableMapOf<GlassId, Int>()
    private var decisions: Map<GlassId, GlassDecision> = emptyMap()
    private var selected = GlassId.ROW1_RIGHT

    // ---- 긴급차량 기능 상태 ----
    private lateinit var alert: AlertController
    private val matcher = EmergencyMatcher()
    private lateinit var simulator: EmergencySimulator
    private var emergencyOn = true
    private val main = Handler(Looper.getMainLooper())

    // ---- 화면 ----
    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Triple<TextView, View, Int>>()   // 탭 버튼, 페이지, 강조색
    private lateinit var sourceText: TextView
    private lateinit var glassPage: View
    private lateinit var emPage: View
    private lateinit var signalPage: SignalPage
    private lateinit var maintPage: MaintenancePage
    private lateinit var carView: CarGlassView
    private lateinit var laneView: LaneView
    private lateinit var selName: TextView
    private lateinit var selValue: TextView
    private lateinit var selInfo: TextView
    private lateinit var seek: SeekBar
    private lateinit var sunText: TextView
    private lateinit var legalText: TextView
    private lateinit var simText: TextView
    private lateinit var gearRow: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var emStage: TextView
    private lateinit var emMsg: TextView
    private lateinit var emCount: TextView
    private lateinit var emButton: TextView
    private var updatingSeek = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Palette.BG)
        LiveSignalClient.saveKeys(this, intent?.extras)   // run.ps1 이 넘긴 공공 신호 API 키 (있을 때만)
        // 차량 연결 객체를 먼저 만들고(화면이 참조), 화면을 그린 뒤 연결을 시작한다
        gateway = if (PleosVehicleGateway.isAvailable(this)) PleosVehicleGateway(this) else SimulatedVehicleGateway()
        alert = AlertController(this, { gateway })
        simulator = EmergencySimulator(::onEmergencyTick)
        setContentView(buildRoot())

        runCatching { gateway.start(this) }.onFailure {
            gateway = SimulatedVehicleGateway().also { g -> g.start(this) }
        }
        showTab(0)
        recompute()
        refreshEmergency()
        maintPage.check()   // 시동 시 자동 점검
    }

    override fun onDestroy() {
        simulator.stop(); signalPage.stop(); alert.release(); runCatching { gateway.stop() }
        super.onDestroy()
    }

    // ================= VehicleGateway.Listener =================
    override fun onCapabilities(caps: VehicleCapabilities) {
        this.caps = caps
        val glass = if (caps.smartGlass.isEmpty()) "스마트글라스 미탑재(미래 기능은 가상 표시)" else "스마트글라스 ${caps.smartGlass.size}면 감지"
        sourceText.text = "차량 연결: ${caps.source} · $glass"
        sent.clear(); recompute()
    }

    override fun onGear(gear: Gear) = setGear(gear)

    override fun onSeatOccupancy(seat: SeatPos, occupied: Boolean) = Unit // 썬 케어(좌석 통풍)는 다음 단계에서 연결

    // ================= 화면 구성 =================
    private fun buildRoot(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        val title = label("카시그널 (글래스 오토)", 22f, Palette.TEXT, true)
        sourceText = label("차량 연결 확인 중…", 13f, Palette.MUTED)

        content = FrameLayout(this)
        emPage = buildEmergencyPage()
        signalPage = SignalPage(this)
        maintPage = MaintenancePage(this) { gateway }
        glassPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("미래 기능 미리보기 · 스마트글라스 탑재 차량 출시 후 도입 예정 (현재 차량은 가상 유리로 표시)", 14f, Palette.AMBER, true))
            addView(buildGlassPage(), LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val pages = listOf(
            Triple("긴급차량", emPage, Palette.RED),
            Triple("신호등", signalPage.root, Palette.GREEN),
            Triple("정비", maintPage.root, Palette.ACCENT),
            Triple("스마트글라스 (미래)", glassPage, Palette.AMBER),
        )
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pages.forEachIndexed { i, (name, page, color) ->
            val b = pill(name) { showTab(i) }
            tabs += Triple(b, page, color)
            tabRow.addView(b); tabRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            content.addView(page)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f)); addView(tabRow)
        }
        root.addView(top); root.addView(sourceText); root.addView(gap(this, 8))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun split(stage: View, side: View): View = splitView(this, stage, side)

    private fun buildGlassPage(): View {
        carView = CarGlassView(this).apply { onSelect = { selected = it; this.selected = it; refreshSelected() } }
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, dp(16)) }

        modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        side.addView(modeRow); side.addView(gap(this, 8))

        selName = label("", 15f, Palette.MUTED)
        selValue = label("", 40f, Palette.TEXT, true)
        selInfo = label("", 14f, Palette.MUTED)
        seek = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) { if (fromUser && !updatingSeek) setManual(v) }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        val steps = row(pill("−5") { nudge(-5) }, pill("−1") { nudge(-1) }, pill("+1") { nudge(1) }, pill("+5") { nudge(5) })
        val quick = row(*listOf(10, 20, 35, 50, 70, 100).map { v -> pill("$v%") { setManual(v) } }.toTypedArray())
        val autoBtn = pill("이 유리 자동으로 (고정 해제)") { manual.remove(selected); recompute() }
        side.addView(card(selName, selValue, selInfo, gap(this, 6), seek, gap(this, 6), steps, gap(this, 6), quick, gap(this, 6), autoBtn))
        side.addView(gap(this))

        sunText = label("", 15f, Palette.TEXT)
        legalText = label("", 14f, Palette.MUTED)
        side.addView(card(label("지금 햇빛 · 법규 가드", 13f, Palette.MUTED), sunText, legalText))
        side.addView(gap(this))

        // 시연용 시뮬레이터
        simText = label("", 14f, Palette.MUTED)
        val headingBar = SeekBar(this).apply {
            max = 359; progress = heading.toInt()
            setOnSeekBarChangeListener(simpleSeek { heading = it.toDouble(); recompute() })
        }
        val timeBar = SeekBar(this).apply {
            max = 15 * 60; progress = minutes - 5 * 60
            setOnSeekBarChangeListener(simpleSeek { minutes = 5 * 60 + it; recompute() })
        }
        gearRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val wiperBtn = pill("와이퍼 켜기/끄기") { wiper = !wiper; recompute() }
        side.addView(card(label("시연용 시뮬레이터 (서울 기준 실제 태양 계산)", 13f, Palette.MUTED), simText,
            label("진행 방향", 13f, Palette.MUTED), headingBar, label("시각 05:00 ~ 20:00", 13f, Palette.MUTED), timeBar,
            gap(this, 6), gearRow, gap(this, 6), wiperBtn))
        return split(carView, side)
    }

    private fun buildEmergencyPage(): View {
        laneView = LaneView(this)
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, dp(16)) }
        val master = switch("긴급차량 안내 사용", emergencyOn) { emergencyOn = it; if (!it) { simulator.stop(); resetEmergency() }; refreshEmergency() }
        side.addView(card(label("유리(썬팅) 기능과 독립적으로 동작하며, 유리 설정은 바꾸지 않습니다.", 13f, Palette.MUTED), master))
        side.addView(gap(this))
        side.addView(card(label("알림 방식", 13f, Palette.MUTED),
            switch("음성 예고 (1km 전)", true) { alert.settings.voice = it },
            switch("음악 볼륨 낮춤 (300m 이내)", true) { alert.settings.duckMedia = it },
            switch("실내 조명 경고 (300m 이내)", true) { alert.settings.ambientWarning = it },
            switch("양보 방향 안내 (화면 + 음성)", true) { alert.settings.yieldGuide = it }))
        side.addView(gap(this))
        emStage = label("", 22f, Palette.TEXT, true)
        emMsg = label("", 15f, Palette.TEXT)
        emCount = label("", 14f, Palette.MUTED)
        side.addView(card(label("상태", 13f, Palette.MUTED), emStage, emMsg, emCount))
        side.addView(gap(this))
        emButton = pill("출동 시나리오 재생 (시연용)") {
            if (simulator.isRunning) { simulator.stop(); resetEmergency() } else { resetEmergency(); simulator.start() }
            refreshEmergency()
        }
        side.addView(emButton)
        side.addView(label("내 위치는 서버로 보내지 않습니다. 접근 여부는 차 안에서 계산합니다.", 13f, Palette.MUTED))
        return split(laneView, side)
    }

    private fun switch(text: String, on: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        this.text = text; isChecked = on; textSize = 16f; setTextColor(Palette.TEXT)
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun simpleSeek(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) { if (fromUser) f(v) }
        override fun onStartTrackingTouch(sb: SeekBar) = Unit
        override fun onStopTrackingTouch(sb: SeekBar) = Unit
    }

    private fun showTab(index: Int) {
        tabs.forEachIndexed { i, (button, page, color) ->
            val on = i == index
            page.visibility = if (on) View.VISIBLE else View.GONE
            button.background = rounded(if (on) color else Palette.PANEL2, dp(12).toFloat(), Palette.LINE)
            button.setTextColor(if (on) Palette.INK else Palette.TEXT)
        }
    }

    // ================= 유리 기능 =================
    private fun setGear(g: Gear) {
        if (gear == Gear.PARK && g != Gear.PARK && privacy) privacy = false  // 출발하면 프라이버시 해제 → 법규 상태로 복귀
        gear = g
        recompute()
    }

    private fun setManual(v: Int) { manual[selected] = v.coerceIn(0, 100); recompute() }

    private fun nudge(d: Int) = setManual((manual[selected] ?: decisions[selected]?.percent ?: 100) + d)

    private fun recompute() {
        val zone = ZoneId.of("Asia/Seoul")
        val at = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(minutes / 60, minutes % 60), zone)
        val sun = SunEngine.position(at.toInstant().toEpochMilli(), SEOUL_LAT, SEOUL_LON)
        val rel = SunEngine.relativeBearing(sun.azimuth, heading)
        val exposure = SunEngine.exposure(rel, sun.elevation)
        val auto = exposure.mapValues { SunEngine.autoTransmittance(it.value) }

        decisions = policy.resolve(GlassInputs(gear, auto, manual, privacy, wiperOn = wiper))
        carView.decisions = decisions
        carView.selected = selected
        carView.sun = rel to sun.elevation

        decisions.forEach { (g, d) ->
            if (sent[g] != d.percent) {
                sent[g] = d.percent
                gateway.applyTransmittance(g, d.percent) { real -> applied[g] = real; if (g == selected) refreshSelected() }
            }
        }

        val side = when { rel > 15 -> "오른쪽"; rel < -15 -> "왼쪽"; else -> "정면" }
        sunText.text = if (sun.elevation > 0) "$side ${"%.0f".format(kotlin.math.abs(rel))}° · 고도 ${"%.0f".format(sun.elevation)}°"
                       else "해가 진 시간 · 모든 유리 투명"
        val limited = decisions.filterValues { it.reason == Reason.LEGAL }.keys
        legalText.text = when {
            !gear.isDriving -> "P단 · 법규 제한 해제 (프라이버시 모드 사용 가능)"
            limited.isNotEmpty() -> "주행 중 · 법정 하한 적용: " + limited.joinToString { it.label }
            else -> "주행 중 · 앞유리 70% · 1열 옆유리 40% 이상 유지"
        }
        simText.text = "진행 방향 ${heading.toInt()}° · %02d:%02d · 기어 %s · 와이퍼 %s".format(minutes / 60, minutes % 60, gear.name, if (wiper) "ON" else "OFF")
        refreshModes(); refreshGear(); refreshSelected()
    }

    private fun refreshSelected() {
        val d = decisions[selected] ?: return
        selName.text = "${selected.label} · ${if (gear.isDriving) "주행 중" else "정차 중"}${if (manual.containsKey(selected)) " · [고정]" else " · 자동"}"
        selValue.text = "투과율 ${d.percent}%"
        val lower = legal.lowerBound(selected, gear)
        val real = applied[selected]
        val why = when (d.reason) {
            Reason.LEGAL -> "주행 중 법정 하한 ${lower}% 미만으로 내릴 수 없습니다"
            Reason.VISIBILITY -> "시야 우선 규칙으로 투명하게 유지 중"
            Reason.PRIVACY -> "프라이버시 모드"
            Reason.MANUAL -> "수동 설정 (조절 단위 1%)"
            Reason.AUTO -> "태양 위치에 따른 자동 조절"
        }
        val realText = if (real != null) "차량 적용값 $real%" else "가상 유리에 표시 (차량 스마트글라스 미지원)"
        selInfo.text = "$why\n허용 범위 $lower~100% · $realText"
        updatingSeek = true; seek.progress = d.percent; updatingSeek = false
    }

    private fun refreshModes() {
        modeRow.removeAllViews()
        val auto = manual.isEmpty() && !privacy
        modeRow.addView(pill("AUTO", auto) { manual.clear(); privacy = false; recompute() })
        modeRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        modeRow.addView(pill("프라이버시 (P단)", privacy) {
            if (gear == Gear.PARK) { privacy = !privacy; recompute() } else legalText.text = "프라이버시 모드는 P단에서만 켤 수 있습니다"
        })
    }

    private fun refreshGear() {
        gearRow.removeAllViews()
        listOf(Gear.PARK to "P", Gear.REVERSE to "R", Gear.DRIVE to "D").forEach { (g, t) ->
            gearRow.addView(pill("기어 $t", gear == g) {
                (gateway as? SimulatedVehicleGateway)?.simulateGear(g) ?: setGear(g)
            })
            gearRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        }
    }

    // ================= 긴급차량 기능 (유리와 독립) =================
    private fun onEmergencyTick(me: io.github.glassauto.core.GeoPose, ev: io.github.glassauto.core.GeoPose, gap: Double) {
        if (!emergencyOn) return
        val status = matcher.update(me, ev)
        laneView.stage = status.stage
        laneView.gapMeters = gap
        alert.onStage(status.stage)?.let { emMsg.text = "“$it”" }
        if (status.stage == EmergencyStage.PASSED) main.postDelayed({ if (!simulator.isRunning) resetEmergency(); refreshEmergency() }, 4_000)
        refreshEmergency(status.stage, gap)
    }

    private fun resetEmergency() {
        matcher.reset(); alert.onStage(EmergencyStage.NONE)
        laneView.stage = EmergencyStage.NONE; laneView.gapMeters = null
        emMsg.text = ""
    }

    private fun refreshEmergency(stage: EmergencyStage = laneView.stage, gap: Double? = laneView.gapMeters) {
        emStage.text = when {
            !emergencyOn -> "꺼짐"
            stage == EmergencyStage.PRE_ALERT -> "접근 예고 · 뒤쪽 ${gap?.toInt()}m"
            stage == EmergencyStage.ALERT -> "접근 · 볼륨 낮춤 · 조명 경고 · 오른쪽으로 양보"
            stage == EmergencyStage.PASSED -> "통과 완료 · 볼륨·조명 복귀"
            simulator.isRunning -> "출동 차량 추적 중 · 뒤쪽 ${gap?.toInt() ?: 0}m"
            else -> "켜짐 · 대기 중"
        }
        emStage.setTextColor(when (stage) {
            EmergencyStage.ALERT -> Palette.RED; EmergencyStage.PRE_ALERT -> Palette.AMBER
            EmergencyStage.PASSED -> Palette.GREEN; else -> Palette.TEXT
        })
        emCount.text = "이번 기기 누적 양보 ${alert.yieldCount}회 · 유리 설정 변경 없음"
        emButton.text = if (simulator.isRunning) "시나리오 정지" else "출동 시나리오 재생 (시연용)"
    }

    companion object {
        private const val SEOUL_LAT = 37.5665
        private const val SEOUL_LON = 126.9780
    }
}
