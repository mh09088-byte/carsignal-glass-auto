package io.github.glassauto.core

enum class FluidLevel { NORMAL, LOW, CRITICALLY_LOW, HIGH, UNKNOWN }

enum class Severity(val label: String, val rank: Int) {
    OK("정상", 0), INFO("참고", 1), CHECK("점검 권장", 2), SERVICE("정비소 방문", 3), STOP("즉시 안전한 곳에 정차", 4)
}

enum class Wheel(val label: String) { LEFT_FRONT("앞 왼쪽"), RIGHT_FRONT("앞 오른쪽"), LEFT_REAR("뒤 왼쪽"), RIGHT_REAR("뒤 오른쪽") }

/** 차량이 스스로 점검한 결과 (Pleos Vehicle State SDK: Safety·Tire·Odometer 로 읽는 값). */
data class HealthSnapshot(
    val engineOil: FluidLevel = FluidLevel.UNKNOWN,
    val brakeFluid: FluidLevel = FluidLevel.UNKNOWN,
    val transmissionOil: FluidLevel = FluidLevel.UNKNOWN,
    val washerLow: Boolean = false,
    val tirePressure: Map<Wheel, Float> = emptyMap(),      // 현재 공기압
    val tireLowThreshold: Map<Wheel, Float> = emptyMap(),  // 차량이 정한 저압 기준
    val warningLights: Set<String> = emptySet(),           // 켜진 경고등 (SDK TelltaleType 이름)
    val odometerKm: Float? = null,
    val lastOilChangeKm: Float? = null,                    // 사용자가 입력한 마지막 엔진오일 교체 시점
)

data class Finding(val item: String, val severity: Severity, val message: String, val action: String)

data class LightGuide(val label: String, val severity: Severity, val meaning: String, val action: String)

/** 경고등 통역: 무슨 뜻인지, 계속 달려도 되는지(긴급도), 무엇을 하면 되는지. */
object WarningLightGuide {
    private val guides = mapOf(
        "ENGINE_OIL_PRESSURE" to LightGuide("엔진오일 압력 경고", Severity.STOP, "엔진 윤활이 부족해 엔진이 손상될 수 있습니다", "즉시 안전한 곳에 정차하고 시동을 끈 뒤 긴급출동을 부르세요"),
        "ENGINE_TEMPERATURE" to LightGuide("엔진 과열 경고", Severity.STOP, "냉각수 온도가 너무 높습니다", "즉시 정차해 엔진을 식히고, 냉각수를 점검하세요"),
        "EV_SYSTEM" to LightGuide("전기차 시스템 경고", Severity.STOP, "구동 시스템에 이상이 감지되었습니다", "안전한 곳에 정차하고 긴급출동을 부르세요"),
        "HEV_SYSTEM" to LightGuide("하이브리드 시스템 경고", Severity.SERVICE, "하이브리드 시스템 점검이 필요합니다", "가까운 정비소에서 점검받으세요"),
        "ENGINE" to LightGuide("엔진 점검 경고", Severity.SERVICE, "엔진·배출가스 계통 이상이 감지되었습니다", "주행은 가능하지만 가까운 정비소에서 점검받으세요"),
        "LOW_VOLT_BATTERY" to LightGuide("충전 계통 경고", Severity.SERVICE, "12V 배터리 충전에 문제가 있습니다", "전기 장치 사용을 줄이고 정비소로 이동하세요"),
        "ABS" to LightGuide("ABS 경고", Severity.SERVICE, "미끄럼 방지 제동 장치가 꺼졌습니다(일반 제동은 작동)", "급제동을 피하고 정비소에서 점검받으세요"),
        "ESC" to LightGuide("차체 자세 제어 경고", Severity.SERVICE, "ESC 이상 또는 작동 중입니다", "계속 켜져 있으면 정비소에서 점검받으세요"),
        "MDPS" to LightGuide("조향 보조 경고", Severity.SERVICE, "핸들이 무거워질 수 있습니다", "저속으로 정비소에 이동하세요"),
        "AIRBAG" to LightGuide("에어백 경고", Severity.SERVICE, "사고 시 에어백이 작동하지 않을 수 있습니다", "빠르게 정비소에서 점검받으세요"),
        "TPMS" to LightGuide("타이어 공기압 경고", Severity.CHECK, "공기압이 낮은 타이어가 있습니다", "가까운 주유소·정비소에서 공기압을 보충하세요"),
        "MASTER_WARNING" to LightGuide("종합 경고", Severity.CHECK, "계기판에 확인할 메시지가 있습니다", "계기판 메시지를 확인하세요"),
        "PARTICLE_FILTER" to LightGuide("배기 필터 경고", Severity.CHECK, "매연 필터가 막히고 있습니다", "고속도로를 20분 정도 꾸준히 주행하거나 정비소에서 재생하세요"),
        "LOW_FUEL" to LightGuide("연료 부족", Severity.INFO, "연료가 얼마 남지 않았습니다", "가까운 주유소를 경유지로 추가하세요"),
        "LOW_EV_BATTERY" to LightGuide("배터리 부족", Severity.INFO, "주행 가능 거리가 짧습니다", "가까운 충전소를 경유지로 추가하세요"),
        "PARKING_BRAKE" to LightGuide("주차 브레이크", Severity.INFO, "주차 브레이크가 걸려 있습니다", "출발 전에 해제하세요"),
        "DOOR_OPEN" to LightGuide("문 열림", Severity.INFO, "열린 문이 있습니다", "출발 전에 문을 닫으세요"),
        "SEAT_BELT" to LightGuide("안전벨트", Severity.INFO, "안전벨트를 매지 않은 좌석이 있습니다", "모든 탑승자가 안전벨트를 매도록 안내하세요"),
    )

    fun explain(typeName: String): LightGuide? = guides[typeName]
    val known: Set<String> get() = guides.keys
}

/** 오토정비: 차량이 스스로 점검한 값을 바탕으로 정비가 필요한 부분을 정확히 안내한다. */
object MaintenanceAdvisor {
    const val OIL_INTERVAL_KM = 10_000f   // 일반 조건 권장 주기 (차종별 취급설명서 기준으로 조정)
    const val OIL_SOON_KM = 1_000f

    fun diagnose(s: HealthSnapshot): List<Finding> {
        val out = mutableListOf<Finding>()

        s.warningLights.mapNotNull { WarningLightGuide.explain(it) }.forEach {
            out += Finding(it.label, it.severity, it.meaning, it.action)
        }
        fluid("엔진오일", s.engineOil, Severity.SERVICE)?.let { out += it }
        fluid("브레이크액", s.brakeFluid, Severity.STOP)?.let { out += it }
        fluid("변속기오일", s.transmissionOil, Severity.SERVICE)?.let { out += it }
        if (s.washerLow) out += Finding("워셔액", Severity.CHECK, "워셔액이 부족합니다", "비·눈 소식 전에 보충하세요")

        s.tirePressure.forEach { (w, p) ->
            val low = s.tireLowThreshold[w] ?: return@forEach
            if (p < low) out += Finding("타이어 공기압 (${w.label})", Severity.CHECK,
                "공기압 %.0f로 기준 %.0f보다 낮습니다".format(p, low), "가까운 주유소·정비소에서 보충하세요")
        }

        val odo = s.odometerKm; val last = s.lastOilChangeKm
        if (odo != null && last != null) {
            val left = OIL_INTERVAL_KM - (odo - last)
            when {
                left <= 0 -> out += Finding("엔진오일 교체 주기", Severity.SERVICE, "교체 주기를 %.0fkm 지났습니다".format(-left), "정비소 예약을 권장합니다")
                left <= OIL_SOON_KM -> out += Finding("엔진오일 교체 주기", Severity.CHECK, "교체까지 약 %.0fkm 남았습니다".format(left), "다음 정비 일정에 포함하세요")
            }
        }
        return out.sortedByDescending { it.severity.rank }
    }

    fun overall(findings: List<Finding>): Severity = findings.maxByOrNull { it.severity.rank }?.severity ?: Severity.OK

    private fun fluid(name: String, level: FluidLevel, lowSeverity: Severity): Finding? = when (level) {
        FluidLevel.CRITICALLY_LOW -> Finding(name, Severity.STOP, "$name 수준이 매우 낮습니다", "안전한 곳에 정차하고 점검·보충하세요")
        FluidLevel.LOW -> Finding(name, lowSeverity, "$name 수준이 낮습니다", "가까운 정비소에서 보충하세요")
        FluidLevel.HIGH -> Finding(name, Severity.CHECK, "$name 수준이 기준보다 높습니다", "정비소에서 점검하세요")
        else -> null
    }
}
