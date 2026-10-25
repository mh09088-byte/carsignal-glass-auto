package io.github.glassauto.core

/** 차량 유리 위치. 1열 = 운전석·동승석 줄 (한국 기준 운전석은 왼쪽). */
enum class GlassId(val label: String) {
    WINDSHIELD("앞유리"),
    ROW1_LEFT("운전석"),
    ROW1_RIGHT("동승석"),
    ROW2_LEFT("뒷좌석 왼쪽"),
    ROW2_RIGHT("뒷좌석 오른쪽"),
    REAR("뒷유리"),
    ROOF("선루프"),
}

/** 기어 상태. 법규 가드는 PARK 가 아니면 '주행 중'으로 본다. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN;
    val isDriving: Boolean get() = this != PARK
}

/** 투과율(%) 범위 보정. 0 = 가장 진함, 100 = 투명. */
fun clampPercent(v: Int): Int = v.coerceIn(0, 100)

/**
 * 지역별 썬팅 법규. 주행 중 유리별 최소 가시광선 투과율(%).
 * 한국: 도로교통법 시행령 제28조 — 앞유리 70%, 1열 옆유리 40% 이상. 2열·뒷유리·선루프는 기준 없음.
 */
data class LegalProfile(val region: String, val minWhileDriving: Map<GlassId, Int>) {
    fun minFor(glass: GlassId): Int = minWhileDriving[glass] ?: 0

    companion object {
        val KOREA = LegalProfile(
            region = "KR",
            minWhileDriving = mapOf(
                GlassId.WINDSHIELD to 70,
                GlassId.ROW1_LEFT to 40,
                GlassId.ROW1_RIGHT to 40,
            ),
        )
    }
}

/** 법규 가드: 주행 중에는 법정 하한보다 어둡게 만들지 않는다. P단에서는 제한을 풀어 준다. */
class LegalGuard(private val profile: LegalProfile = LegalProfile.KOREA) {
    fun lowerBound(glass: GlassId, gear: Gear): Int = if (gear.isDriving) profile.minFor(glass) else 0

    fun clamp(glass: GlassId, requested: Int, gear: Gear): Int =
        maxOf(clampPercent(requested), lowerBound(glass, gear))

    fun isLimited(glass: GlassId, requested: Int, gear: Gear): Boolean =
        clampPercent(requested) < lowerBound(glass, gear)
}
