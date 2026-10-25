package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class SunEngineTest {
    private val lat = 37.5665
    private val lon = 126.9780

    private fun at(iso: String) = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test fun `서울 하지 정오 무렵 태양은 남쪽 높이 뜬다`() {
        val p = SunEngine.position(at("2026-06-21T12:30+09:00[Asia/Seoul]"), lat, lon)
        assertTrue("elevation=${p.elevation}", p.elevation in 74.0..78.0)
        assertTrue("azimuth=${p.azimuth}", p.azimuth in 150.0..215.0)
    }

    @Test fun `서울 동지 오후 5시 태양은 남서쪽 지평선 근처`() {
        val p = SunEngine.position(at("2026-12-21T17:00+09:00[Asia/Seoul]"), lat, lon)
        assertTrue("elevation=${p.elevation}", p.elevation in -2.0..6.0)
        assertTrue("azimuth=${p.azimuth}", p.azimuth in 225.0..250.0)
    }

    @Test fun `한밤중에는 태양이 지평선 아래`() {
        val p = SunEngine.position(at("2026-10-25T01:00+09:00[Asia/Seoul]"), lat, lon)
        assertTrue(p.elevation < 0)
    }

    @Test fun `상대 각도는 오른쪽이 양수`() {
        assertEquals(25.0, SunEngine.relativeBearing(115.0, 90.0), 1e-9)
        assertEquals(-70.0, SunEngine.relativeBearing(20.0, 90.0), 1e-9)
        assertEquals(-170.0, SunEngine.relativeBearing(10.0, 180.0), 1e-9)
    }

    @Test fun `오른쪽 앞 낮은 해는 동승석과 앞유리를 비춘다`() {
        val e = SunEngine.exposure(relative = 45.0, elevation = 12.0)
        assertTrue(e.getValue(GlassId.ROW1_RIGHT) > 0.5)
        assertTrue(e.getValue(GlassId.WINDSHIELD) > 0.1)
        assertEquals(0.0, e.getValue(GlassId.ROW1_LEFT), 1e-9)
        assertEquals(0.0, e.getValue(GlassId.ROOF), 1e-9)
    }

    @Test fun `높은 해는 선루프만 비춘다`() {
        val e = SunEngine.exposure(relative = 0.0, elevation = 75.0)
        assertTrue(e.getValue(GlassId.ROOF) > 0.8)
        assertEquals(0.0, e.getValue(GlassId.WINDSHIELD), 1e-9)
    }

    @Test fun `해가 지면 노출 없음 → 자동 투과율 100`() {
        val e = SunEngine.exposure(relative = 0.0, elevation = -5.0)
        assertTrue(e.values.all { it == 0.0 })
        assertEquals(100, SunEngine.autoTransmittance(0.0))
        assertEquals(25, SunEngine.autoTransmittance(1.0))
    }
}
