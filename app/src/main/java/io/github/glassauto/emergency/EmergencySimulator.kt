package io.github.glassauto.emergency

import android.os.Handler
import android.os.Looper
import io.github.glassauto.core.EmergencyMatcher
import io.github.glassauto.core.GeoPose

/**
 * 출동 시나리오 재생기 (시연용). 실제 서비스에서는 출동 차량이 보낸 위치를
 * WebSocket 으로 받아 같은 [onTick] 경로로 넘긴다.
 * 내 차: 북쪽으로 40km/h, 구급차: 1.4km 뒤에서 같은 방향 90km/h → 접근 후 추월.
 */
class EmergencySimulator(private val onTick: (me: GeoPose, ev: GeoPose, gapMeters: Double) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var t = 0.0

    private val origin = 37.5000 to 127.0300   // 시뮬레이션 기준점 (서울 강남대로 부근)
    private val heading = 0.0                  // 북쪽

    fun start() {
        if (running) return
        running = true; t = 0.0
        handler.post(step)
    }

    fun stop() { running = false; handler.removeCallbacks(step) }

    val isRunning: Boolean get() = running

    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            val mySpeed = 40 / 3.6; val evSpeed = 90 / 3.6
            val myDist = mySpeed * t
            val evDist = evSpeed * t - START_GAP
            val me = pose(myDist); val ev = pose(evDist)
            onTick(me, ev, myDist - evDist)
            t += TIME_SCALE * TICK_MS / 1000.0
            if (evDist - myDist > END_AHEAD) { running = false; return }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun pose(alongMeters: Double): GeoPose {
        val (lat, lon) = EmergencyMatcher.offset(origin.first, origin.second, heading, alongMeters)
        return GeoPose(lat, lon, heading)
    }

    companion object {
        private const val START_GAP = 1_400.0   // 시작 시 구급차가 뒤에 있는 거리(m)
        private const val END_AHEAD = 250.0     // 구급차가 이만큼 앞서면 종료
        private const val TICK_MS = 200L
        private const val TIME_SCALE = 3.0      // 시연을 위해 3배속
    }
}
