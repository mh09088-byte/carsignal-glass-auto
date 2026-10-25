package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMatcherTest {
    private val baseLat = 37.5
    private val baseLon = 127.03

    /** 기준점에서 북쪽으로 [m] 미터 떨어진 지점, 진행 방향 [heading]. */
    private fun at(m: Double, heading: Double = 0.0): GeoPose {
        val (lat, lon) = EmergencyMatcher.offset(baseLat, baseLon, 0.0, m)
        return GeoPose(lat, lon, heading)
    }

    @Test fun `거리 계산이 정확하다`() {
        val a = at(0.0); val b = at(1000.0)
        assertEquals(1000.0, EmergencyMatcher.distanceMeters(a.lat, a.lon, b.lat, b.lon), 1.0)
    }

    @Test fun `같은 방향 뒤쪽에서 접근하면 1km 예고 후 300m 경보`() {
        val m = EmergencyMatcher()
        assertEquals(EmergencyStage.NONE, m.update(at(1000.0), at(-500.0)).stage)       // 1.5km 뒤
        assertEquals(EmergencyStage.PRE_ALERT, m.update(at(1000.0), at(200.0)).stage)   // 800m 뒤
        assertEquals(EmergencyStage.ALERT, m.update(at(1000.0), at(750.0)).stage)       // 250m 뒤
    }

    @Test fun `추월하면 통과로 바뀌고 다시 조용해진다`() {
        val m = EmergencyMatcher()
        m.update(at(1000.0), at(800.0))
        assertEquals(EmergencyStage.PASSED, m.update(at(1000.0), at(1100.0)).stage)
        assertEquals(EmergencyStage.NONE, m.update(at(1000.0), at(1200.0)).stage)
    }

    @Test fun `반대 방향 차로의 긴급차량은 알리지 않는다`() {
        val m = EmergencyMatcher()
        assertEquals(EmergencyStage.NONE, m.update(at(1000.0, 0.0), at(800.0, 180.0)).stage)
    }

    @Test fun `이미 앞서 가는 긴급차량은 알리지 않는다`() {
        val m = EmergencyMatcher()
        val s = m.update(at(1000.0), at(1200.0))
        assertEquals(EmergencyStage.NONE, s.stage)
        assertTrue(s.distanceMeters in 190.0..210.0)
    }
}
