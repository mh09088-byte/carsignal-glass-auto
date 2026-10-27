package io.github.glassauto.core

import org.json.JSONArray
import org.json.JSONObject

/** 공공 실시간 신호 API에서 받은 한 교차로·한 방향의 현재 신호. */
data class LiveSignal(
    val color: SignalColor,
    val remainingSec: Double,
    val intersection: String,      // 교차로 이름 또는 ID
    val source: String,            // 화면에 표시할 출처
    val observedAtMs: Long,        // 신호 값이 측정된 시각
)

/**
 * 울산광역시_C-ITS API (공공데이터포털, /signal?linkId=).
 * realtime[] 의 color 는 신호 색 코드, remain_time 은 남은 초. type 1 = 직진.
 */
object UlsanSignalParser {
    const val SOURCE = "울산광역시 C-ITS (공공데이터포털)"

    /**
     * 울산 C-ITS 색 코드 → 신호 색. 2026-10-28 실측 흐름(1 약 150초 → 3 약 20초 → 1)으로 정한 값이며
     * 2(노랑)는 아직 관측하지 못했다. 활용가이드(CITS-CS-DES-001-21)로 최종 확인할 것.
     */
    val COLORS = mapOf(1 to SignalColor.RED, 2 to SignalColor.YELLOW, 3 to SignalColor.GREEN)

    fun parse(json: String, nowMs: Long): LiveSignal? {
        val root = JSONObject(json)
        if (root.optJSONObject("header")?.optInt("resultCode") != 200) return null
        val item = root.optJSONObject("body")?.optJSONArray("items")?.optJSONObject(0) ?: return null
        val rt = item.optJSONArray("realtime") ?: return null
        val straight = (0 until rt.length()).map { rt.getJSONObject(it) }
            .firstOrNull { it.optInt("type") == 1 } ?: return null
        val color = COLORS[straight.optInt("color")] ?: return null
        return LiveSignal(color, straight.optDouble("remain_time"), item.optString("intersec_nm"), SOURCE, nowMs)
    }
}

/**
 * 서울 T Data '신호제어기 신호 잔여시간 정보서비스' (v2xSignalPhaseTimingFusionInformation).
 * 방향별 직진 신호 상태(…StsgStatNm, J2735 MovementPhaseState)와 잔여시간(…StsgRmdrCs, 1/10초).
 * 같은 API는 5분에 한 번만 호출할 수 있어 실서비스에서는 서버가 받아 차량에 나눠 준다.
 */
object TDataSignalParser {
    const val SOURCE = "서울 T Data C-ITS"
    private val DIRECTIONS = listOf("nt", "et", "st", "wt", "ne", "se", "sw", "nw")

    fun colorOf(state: String): SignalColor? = when {
        state.startsWith("stop-") -> SignalColor.RED                    // stop-And-Remain, stop-Then-Proceed
        state.endsWith("-clearance") -> SignalColor.YELLOW              // protected-/permissive-clearance
        state.endsWith("Movement-Allowed") -> SignalColor.GREEN         // protected-/permissive-Movement-Allowed
        else -> null                                                    // dark, unavailable
    }

    /** 가장 최근 메시지에서 [direction](없으면 첫 유효 방향) 직진 신호를 고른다. */
    fun parse(json: String, direction: String? = null): LiveSignal? {
        val arr = JSONArray(json)
        val latest = (0 until arr.length()).map { arr.getJSONObject(it) }
            .maxByOrNull { it.optDouble("trsmUtcTime") } ?: return null
        val dirs = if (direction != null) listOf(direction) else DIRECTIONS
        for (d in dirs) {
            val color = colorOf(latest.optString("${d}StsgStatNm")) ?: continue
            val rem = latest.optDouble("${d}StsgRmdrCs")
            if (rem.isNaN()) continue
            return LiveSignal(color, rem / 10.0, "교차로 ${latest.optString("itstId")}", SOURCE, latest.optDouble("trsmUtcTime").toLong())
        }
        return null
    }
}

/** 받아 온 신호를 시간이 지난 만큼 줄여 지금 상태로 맞춘다. 0초가 되면 새로 받아야 한다. */
fun LiveSignal.toState(nowMs: Long, distanceMeters: Double, base: SignalState): SignalState? {
    val left = remainingSec - (nowMs - observedAtMs) / 1000.0
    if (left <= 0) return null
    return base.copy(color = color, remainingSec = left, distanceMeters = distanceMeters)
}
