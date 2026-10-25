package io.github.glassauto.core

/** 유리 기능의 입력 상태. 긴급차량 기능과는 무관하다(독립 기능). */
data class GlassInputs(
    val gear: Gear,
    val auto: Map<GlassId, Int>,          // 태양 계산으로 정한 자동 목표 투과율
    val manual: Map<GlassId, Int>,        // 사용자가 [고정]한 값
    val privacy: Boolean = false,         // 프라이버시 모드 (P단에서만 유효)
    val wiperOn: Boolean = false,
    val defrostOn: Boolean = false,
)

enum class Reason { AUTO, MANUAL, PRIVACY, VISIBILITY, LEGAL }

data class GlassDecision(val percent: Int, val reason: Reason)

/**
 * 유리 우선순위: 법규 가드 > 시야 우선 규칙 > 수동 설정 > 자동.
 * - 시야 우선: 후진 시 뒤·옆 유리, 와이퍼·성에 제거 작동 시 앞유리를 투명하게.
 * - 법규 가드: 주행 중 법정 하한보다 어둡지 않게 (마지막에 항상 적용).
 */
class GlassPolicy(private val legal: LegalGuard = LegalGuard()) {

    fun resolve(input: GlassInputs): Map<GlassId, GlassDecision> = GlassId.entries.associateWith { g ->
        var reason = Reason.AUTO
        var value = input.auto[g] ?: 100

        input.manual[g]?.let { value = it; reason = Reason.MANUAL }

        if (input.privacy && input.gear == Gear.PARK) { value = PRIVACY_LEVEL; reason = Reason.PRIVACY }

        if (needsVisibility(g, input)) { value = 100; reason = Reason.VISIBILITY }

        val clamped = legal.clamp(g, value, input.gear)
        if (clamped != clampPercent(value)) reason = Reason.LEGAL
        GlassDecision(clamped, reason)
    }

    private fun needsVisibility(g: GlassId, input: GlassInputs): Boolean = when {
        input.gear == Gear.REVERSE && g in REVERSE_VIEW -> true
        (input.wiperOn || input.defrostOn) && g == GlassId.WINDSHIELD -> true
        else -> false
    }

    companion object {
        const val PRIVACY_LEVEL = 10
        private val REVERSE_VIEW = setOf(GlassId.REAR, GlassId.ROW2_LEFT, GlassId.ROW2_RIGHT, GlassId.ROW1_LEFT, GlassId.ROW1_RIGHT)
    }
}

/**
 * 사용자가 고른 투과율(1% 단위)을 차량이 지원하는 스마트글라스 단계로 바꾼다.
 * Pleos `checkSmartGlassLevelCapabilities` 가 돌려주는 AreaBounds(min, max)를 사용한다.
 * 단계가 클수록 진하다고 가정하고(차량 사양에 따라 [darkerIsHigher] 로 뒤집을 수 있음),
 * 실제 적용 투과율도 함께 계산해 화면에 '차량 적용값'으로 보여 준다.
 */
data class LevelMapper(val minLevel: Int, val maxLevel: Int, val darkerIsHigher: Boolean = true) {

    fun toLevel(percent: Int): Int {
        if (maxLevel <= minLevel) return minLevel
        val darkness = (100 - clampPercent(percent)) / 100.0
        val steps = maxLevel - minLevel
        val idx = Math.round(darkness * steps).toInt()
        return if (darkerIsHigher) minLevel + idx else maxLevel - idx
    }

    fun toPercent(level: Int): Int {
        if (maxLevel <= minLevel) return 100
        val steps = maxLevel - minLevel
        val idx = (if (darkerIsHigher) level - minLevel else maxLevel - level).coerceIn(0, steps)
        return clampPercent(Math.round(100 - idx * 100.0 / steps).toInt())
    }

    /** 사용자가 고른 값과 실제 적용될 값. 단계 수가 적은 차량에서는 둘이 다를 수 있다. */
    fun applied(percent: Int): Int = toPercent(toLevel(percent))
}
