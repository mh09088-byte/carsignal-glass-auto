package io.github.glassauto.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.github.glassauto.core.FluidLevel
import io.github.glassauto.core.MaintenanceAdvisor
import io.github.glassauto.core.Severity
import io.github.glassauto.core.Wheel
import io.github.glassauto.vehicle.SimulatedVehicleGateway
import io.github.glassauto.vehicle.VehicleGateway

/**
 * 오토정비 탭. 차량이 스스로 점검한 값(Vehicle State SDK — 현재 사용 가능)으로
 * 정비가 필요한 부분과 긴급도(즉시 정차 / 정비소 방문 / 점검 권장)를 알려 준다.
 */
class MaintenancePage(private val ctx: Context, private val gateway: () -> VehicleGateway) {
    private val summary = ctx.label("", 26f, Palette.TEXT, true)
    private val summarySub = ctx.label("", 15f, Palette.MUTED)
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val simRow = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val toggles = linkedMapOf(
        "엔진오일 부족" to false, "뒤 오른쪽 타이어 저압" to false, "워셔액 부족" to false,
        "엔진 경고등" to false, "엔진오일 압력 경고등" to false,
    )

    val root: View

    init {
        val left = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, ctx.dp(8), ctx.dp(16))
            addView(ctx.card(ctx.label("차량 자가 점검", 13f, Palette.MUTED), summary, summarySub))
            addView(gap(ctx)); addView(list)
        }
        val side = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(8), 0, 0, ctx.dp(16)) }
        side.addView(ctx.pill("지금 자가 점검") { check() })
        side.addView(gap(ctx, 8))
        side.addView(ctx.pill("가까운 정비소를 경유지로 추가") {
            summarySub.text = "NaviHelper 경유지 추가 연동은 2차 PT 전 반영 예정입니다 (현재 사용 가능 SDK)"
        })
        side.addView(gap(ctx))
        side.addView(ctx.card(ctx.label("점검 항목 (Pleos Vehicle State SDK)", 13f, Palette.MUTED),
            ctx.label("엔진오일 · 브레이크액 · 변속기오일 수준", 15f),
            ctx.label("워셔액 부족 · 켜진 경고등", 15f),
            ctx.label("타이어 공기압(차량 기준 대비) · 누적 주행거리", 15f)))
        side.addView(gap(ctx))
        if (gateway() is SimulatedVehicleGateway) {
            side.addView(ctx.card(ctx.label("시연용: 이상 상황 만들기", 13f, Palette.MUTED), simRow))
            refreshToggles()
        }
        root = splitView(ctx, android.widget.ScrollView(ctx).apply { addView(left) }, side)
        check()
    }

    private fun refreshToggles() {
        simRow.removeAllViews()
        toggles.forEach { (name, on) ->
            simRow.addView(ctx.pill(if (on) "■ $name" else "□ $name", on) { toggles[name] = !on; applySim(); refreshToggles(); check() })
            simRow.addView(gap(ctx, 6))
        }
    }

    private fun applySim() {
        val g = gateway() as? SimulatedVehicleGateway ?: return
        val lights = mutableSetOf<String>()
        if (toggles["엔진 경고등"] == true) lights += "ENGINE"
        if (toggles["엔진오일 압력 경고등"] == true) lights += "ENGINE_OIL_PRESSURE"
        if (toggles["뒤 오른쪽 타이어 저압"] == true) lights += "TPMS"
        g.health = g.health.copy(
            engineOil = if (toggles["엔진오일 부족"] == true) FluidLevel.LOW else FluidLevel.NORMAL,
            washerLow = toggles["워셔액 부족"] == true,
            warningLights = lights,
            tirePressure = g.health.tirePressure + (Wheel.RIGHT_REAR to if (toggles["뒤 오른쪽 타이어 저압"] == true) 24f else 35f),
        )
    }

    fun check() {
        gateway().readHealth { h ->
            val findings = MaintenanceAdvisor.diagnose(h)
            val overall = MaintenanceAdvisor.overall(findings)
            summary.text = if (findings.isEmpty()) "이상 없음 · 정상" else "${overall.label} · ${findings.size}건"
            summary.setTextColor(colorOf(overall))
            summarySub.text = "주행거리 ${h.odometerKm?.toInt() ?: "-"}km · 시동 시 자동 점검"
            list.removeAllViews()
            if (findings.isEmpty()) list.addView(ctx.card(ctx.label("점검한 모든 항목이 정상입니다.", 16f)))
            findings.forEach { f ->
                val badge = ctx.label(f.severity.label, 14f, Palette.INK, true).apply {
                    setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
                    background = rounded(colorOf(f.severity), ctx.dp(10).toFloat())
                }
                val title = ctx.label(f.item, 18f, Palette.TEXT, true)
                val head = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(title, LinearLayout.LayoutParams(0, -2, 1f)); addView(badge)
                }
                list.addView(ctx.card(head, ctx.label(f.message, 15f), ctx.label("→ ${f.action}", 15f, Palette.ACCENT)))
                list.addView(gap(ctx, 8))
            }
        }
    }

    private fun colorOf(s: Severity): Int = when (s) {
        Severity.STOP -> Palette.RED; Severity.SERVICE -> Palette.AMBER; Severity.CHECK -> Palette.ACCENT
        Severity.INFO -> Palette.MUTED; Severity.OK -> Palette.GREEN
    }
}

/** 넓은 화면은 좌우, 좁은 화면은 위아래로 나눈다. */
fun splitView(ctx: Context, stage: View, side: View): View {
    val wide = ctx.resources.displayMetrics.widthPixels > ctx.resources.displayMetrics.heightPixels
    return LinearLayout(ctx).apply {
        orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val sideScroll = android.widget.ScrollView(ctx).apply { addView(side) }
        if (wide) {
            addView(stage, LinearLayout.LayoutParams(0, -1, 1f)); addView(sideScroll, LinearLayout.LayoutParams(0, -1, 1.1f))
        } else {
            addView(stage, LinearLayout.LayoutParams(-1, 0, 0.9f)); addView(sideScroll, LinearLayout.LayoutParams(-1, 0, 1.1f))
        }
    }
}
