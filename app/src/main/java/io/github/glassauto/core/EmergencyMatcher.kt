package io.github.glassauto.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 위치와 진행 방향(북쪽 기준 시계 방향, 도). */
data class GeoPose(val lat: Double, val lon: Double, val heading: Double)

enum class EmergencyStage { NONE, PRE_ALERT, ALERT, PASSED }

data class EmergencyStatus(val stage: EmergencyStage, val distanceMeters: Double)

/**
 * 긴급차량 접근 판정 (유리 기능과 독립).
 * 같은 방향으로 달리고, 내 차가 긴급차량 진행 방향 앞쪽에 있을 때만 알린다.
 * 반대 차로나 이미 지나간 차량에는 알리지 않는다. 계산은 차 안에서만 한다(내 위치 서버 미전송).
 */
class EmergencyMatcher(
    private val preAlertMeters: Double = 1_000.0,
    private val alertMeters: Double = 300.0,
    private val sameDirectionDeg: Double = 45.0,
    private val aheadConeDeg: Double = 35.0,
) {
    private var alerted = false

    fun update(me: GeoPose, ev: GeoPose): EmergencyStatus {
        val dist = distanceMeters(me.lat, me.lon, ev.lat, ev.lon)
        val sameDirection = angleDiff(me.heading, ev.heading) <= sameDirectionDeg
        val meAheadOfEv = angleDiff(bearing(ev.lat, ev.lon, me.lat, me.lon), ev.heading) <= aheadConeDeg

        val stage = when {
            sameDirection && meAheadOfEv && dist <= alertMeters -> EmergencyStage.ALERT
            sameDirection && meAheadOfEv && dist <= preAlertMeters -> EmergencyStage.PRE_ALERT
            alerted && sameDirection && !meAheadOfEv -> EmergencyStage.PASSED
            else -> EmergencyStage.NONE
        }
        alerted = when (stage) {
            EmergencyStage.PRE_ALERT, EmergencyStage.ALERT -> true
            EmergencyStage.PASSED -> false
            EmergencyStage.NONE -> alerted && dist <= preAlertMeters
        }
        return EmergencyStatus(stage, dist)
    }

    fun reset() { alerted = false }

    companion object {
        private const val EARTH_R = 6_371_000.0

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
            val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_R * asin(sqrt(a))
        }

        /** a → b 방위각(북쪽 기준 시계 방향, 0~360). */
        fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
            val dl = Math.toRadians(lon2 - lon1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }

        /** 기준점에서 방위각·거리만큼 이동한 좌표 (시뮬레이션용). */
        fun offset(lat: Double, lon: Double, bearingDeg: Double, meters: Double): Pair<Double, Double> {
            val d = meters / EARTH_R; val b = Math.toRadians(bearingDeg)
            val p1 = Math.toRadians(lat); val l1 = Math.toRadians(lon)
            val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
            val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
            return Math.toDegrees(p2) to Math.toDegrees(l2)
        }

        fun angleDiff(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)
    }
}
