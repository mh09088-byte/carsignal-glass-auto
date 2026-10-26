package io.github.glassauto.core

import kotlin.math.ceil
import kotlin.math.floor

enum class SignalColor(val label: String) { RED("빨간불"), YELLOW("노란불"), GREEN("초록불") }

/**
 * 전방 교차로 신호 상태. 공공 실시간 신호 API(서울 T Data C-ITS 신호 잔여시간,
 * 울산 C-ITS, 행정안전부 교통안전 신호등 실시간 정보)에서 받는 값과 같은 구조.
 */
data class SignalState(
    val color: SignalColor,
    val remainingSec: Double,          // 현재 신호가 바뀌기까지 남은 시간
    val greenSec: Double,
    val yellowSec: Double,
    val redSec: Double,
    val distanceMeters: Double,        // 정지선까지 거리
)

sealed class SignalAdvice(val text: String) {
    class Keep(text: String) : SignalAdvice(text)                         // 지금 속도로 통과
    class Adjust(val minKmh: Int, val maxKmh: Int, text: String) : SignalAdvice(text) // 권장 속도 범위
    class Coast(text: String) : SignalAdvice(text)                        // 다음 신호 대기 → 미리 감속
}

/**
 * 신호등 타임: 남은 시간 표시와 그린웨이브(초록불 통과 권장 속도) 안내.
 * - 예측 출발을 막기 위해 5초 이하일 때는 숫자를 숨긴다(지자체 신호 잔여시간 표시 기준과 동일).
 * - 권장 속도는 제한속도를 넘지 않는다.
 */
object SignalAdvisor {
    const val HIDE_BELOW_SEC = 5.0
    private const val MIN_CRUISE_KMH = 20

    /** 화면·음성에 보여 줄 남은 시간 문구. */
    fun countdownText(s: SignalState): String =
        if (s.remainingSec <= HIDE_BELOW_SEC) "${s.color.label} · 곧 바뀝니다"
        else "${s.color.label} ${ceil(s.remainingSec).toInt()}초"

    /** 정지선에 초록불로 도착할 수 있는 시간 구간(초). */
    fun greenWindow(s: SignalState): Pair<Double, Double> = when (s.color) {
        SignalColor.GREEN -> 0.0 to s.remainingSec
        SignalColor.YELLOW -> (s.remainingSec + s.redSec) to (s.remainingSec + s.redSec + s.greenSec)
        SignalColor.RED -> s.remainingSec to (s.remainingSec + s.greenSec)
    }

    fun advise(s: SignalState, speedKmh: Double, limitKmh: Int): SignalAdvice {
        val (tStart, tEnd) = greenWindow(s)
        val d = s.distanceMeters
        // 초록불 구간에 도착하려면: d / tEnd <= v <= d / tStart
        val vMinKmh = if (tEnd <= 0) Double.MAX_VALUE else d / tEnd * 3.6
        val vMaxKmh = if (tStart <= 0) Double.MAX_VALUE else d / tStart * 3.6
        val lo = maxOf(vMinKmh, MIN_CRUISE_KMH.toDouble())
        val hi = minOf(vMaxKmh, limitKmh.toDouble())

        if (speedKmh in lo..hi) return SignalAdvice.Keep("지금 속도를 유지하면 초록불에 통과합니다")
        if (lo <= hi) {
            val a = ceil(lo).toInt(); val b = floor(hi).toInt()
            if (a <= b) return SignalAdvice.Adjust(a, b, "시속 $a~${b}km로 달리면 초록불에 통과합니다")
        }
        return SignalAdvice.Coast("다음 신호에 멈춥니다 · 가속을 줄여 미리 감속하세요")
    }

    /** 신호 주기를 따라 [dt]초 뒤 상태 (시뮬레이션·예측용). */
    fun tick(s: SignalState, dt: Double, movedMeters: Double = 0.0): SignalState {
        var color = s.color; var rem = s.remainingSec - dt
        while (rem <= 0) {
            color = when (color) { SignalColor.GREEN -> SignalColor.YELLOW; SignalColor.YELLOW -> SignalColor.RED; SignalColor.RED -> SignalColor.GREEN }
            rem += when (color) { SignalColor.GREEN -> s.greenSec; SignalColor.YELLOW -> s.yellowSec; SignalColor.RED -> s.redSec }
        }
        return s.copy(color = color, remainingSec = rem, distanceMeters = (s.distanceMeters - movedMeters).coerceAtLeast(0.0))
    }
}
