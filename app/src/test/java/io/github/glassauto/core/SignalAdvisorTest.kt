package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalAdvisorTest {
    private fun state(c: SignalColor, rem: Double, d: Double) =
        SignalState(c, rem, greenSec = 30.0, yellowSec = 3.0, redSec = 40.0, distanceMeters = d)

    @Test fun `5초 이하에서는 숫자를 숨긴다`() {
        assertEquals("빨간불 23초", SignalAdvisor.countdownText(state(SignalColor.RED, 22.2, 0.0)))
        assertEquals("빨간불 · 곧 바뀝니다", SignalAdvisor.countdownText(state(SignalColor.RED, 4.9, 0.0)))
    }

    @Test fun `초록불 남은 시간 안에 도착하면 속도 유지`() {
        // 200m, 초록 20초 남음, 시속 50km → 14.4초 만에 도착
        val a = SignalAdvisor.advise(state(SignalColor.GREEN, 20.0, 200.0), 50.0, 60)
        assertTrue(a is SignalAdvice.Keep)
    }

    @Test fun `빨간불이면 초록불에 맞춰 도착하는 속도 범위를 알려 준다`() {
        // 400m, 빨강 25초 남음 → 25~55초 사이 도착: 시속 26.2~57.6km, 제한 60
        val a = SignalAdvisor.advise(state(SignalColor.RED, 25.0, 400.0), 70.0, 60)
        assertTrue(a is SignalAdvice.Adjust)
        a as SignalAdvice.Adjust
        assertEquals(27, a.minKmh)
        assertEquals(57, a.maxKmh)
    }

    @Test fun `권장 속도는 제한속도를 넘지 않고 불가능하면 미리 감속`() {
        // 100m, 초록 3초 남음 → 시속 120km 이상 필요 → 제한 60이므로 불가, 다음 초록은 43초 뒤(너무 늦음 → 최저 속도 미만)
        val a = SignalAdvisor.advise(state(SignalColor.GREEN, 3.0, 100.0), 50.0, 60)
        assertTrue(a is SignalAdvice.Coast)
    }

    @Test fun `노란불 다음 초록 구간 계산`() {
        val (s, e) = SignalAdvisor.greenWindow(state(SignalColor.YELLOW, 2.0, 0.0))
        assertEquals(42.0, s, 1e-9)
        assertEquals(72.0, e, 1e-9)
    }

    @Test fun `신호 주기가 순서대로 넘어간다`() {
        val s = SignalAdvisor.tick(state(SignalColor.GREEN, 1.0, 100.0), 2.0, 10.0)
        assertEquals(SignalColor.YELLOW, s.color)
        assertEquals(2.0, s.remainingSec, 1e-9)
        assertEquals(90.0, s.distanceMeters, 1e-9)
    }
}
