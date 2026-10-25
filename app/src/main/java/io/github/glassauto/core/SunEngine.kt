package io.github.glassauto.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** 태양 위치(도). azimuth: 북쪽 기준 시계 방향 0~360, elevation: 지평선 기준 고도. */
data class SunPosition(val azimuth: Double, val elevation: Double)

/**
 * 태양 위치 계산과 유리별 일사 판정.
 * 천문 연감(NOAA/USNO) 간이 공식 — 오차 약 1° 이내로 유리 제어에는 충분하다.
 */
object SunEngine {

    fun position(epochMillisUtc: Long, latitude: Double, longitude: Double): SunPosition {
        val jd = epochMillisUtc / 86_400_000.0 + 2_440_587.5
        val n = jd - 2_451_545.0
        val meanLon = norm360(280.460 + 0.9856474 * n)
        val meanAnom = Math.toRadians(norm360(357.528 + 0.9856003 * n))
        val eclLon = Math.toRadians(meanLon + 1.915 * sin(meanAnom) + 0.020 * sin(2 * meanAnom))
        val obliq = Math.toRadians(23.439 - 0.0000004 * n)

        val ra = atan2(cos(obliq) * sin(eclLon), cos(eclLon))
        val dec = asin(sin(obliq) * sin(eclLon))

        val gmstHours = norm(18.697374558 + 24.06570982441908 * n, 24.0)
        val lst = Math.toRadians(gmstHours * 15.0 + longitude)
        val ha = lst - ra
        val lat = Math.toRadians(latitude)

        val elev = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha))
        val az = atan2(-sin(ha), tan(dec) * cos(lat) - sin(lat) * cos(ha))
        return SunPosition(norm360(Math.toDegrees(az)), Math.toDegrees(elev))
    }

    /** 차량 진행 방향 기준 태양의 상대 각도. 0 = 정면, + = 오른쪽, − = 왼쪽 (−180~180). */
    fun relativeBearing(sunAzimuth: Double, vehicleHeading: Double): Double =
        norm360(sunAzimuth - vehicleHeading + 540.0) - 180.0

    /**
     * 유리별 직사광 노출도(0~1). 해가 낮을수록 옆·앞유리로, 높을수록 선루프로 들어온다.
     * 해가 지평선 아래면 모든 유리 0.
     */
    fun exposure(relative: Double, elevation: Double): Map<GlassId, Double> {
        if (elevation <= 0.0) return GlassId.entries.associateWith { 0.0 }
        val low = (1.0 - elevation / 60.0).coerceIn(0.0, 1.0)      // 낮은 해일수록 옆·앞유리에 강함
        val high = ((elevation - 30.0) / 50.0).coerceIn(0.0, 1.0)  // 높은 해일수록 선루프에 강함
        fun lobe(center: Double, halfWidth: Double): Double {
            val d = abs(norm360(relative - center + 540.0) - 180.0)
            return max(0.0, 1.0 - d / halfWidth)
        }
        return mapOf(
            GlassId.WINDSHIELD to lobe(0.0, 70.0) * low,
            GlassId.ROW1_RIGHT to lobe(60.0, 60.0) * low,
            GlassId.ROW2_RIGHT to lobe(120.0, 60.0) * low,
            GlassId.ROW1_LEFT to lobe(-60.0, 60.0) * low,
            GlassId.ROW2_LEFT to lobe(-120.0, 60.0) * low,
            GlassId.REAR to lobe(180.0, 60.0) * low,
            GlassId.ROOF to high,
        )
    }

    /** 노출도 → 자동 목표 투과율. 노출이 없으면 투명(100), 최대 노출이면 25%. */
    fun autoTransmittance(exposure: Double): Int = clampPercent((100 - 75 * exposure.coerceIn(0.0, 1.0)).toInt())

    private fun norm360(d: Double) = norm(d, 360.0)
    private fun norm(v: Double, m: Double) = ((v % m) + m) % m
}
