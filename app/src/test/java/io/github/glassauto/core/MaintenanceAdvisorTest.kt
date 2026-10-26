package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceAdvisorTest {

    @Test fun `이상이 없으면 정상`() {
        val f = MaintenanceAdvisor.diagnose(HealthSnapshot(FluidLevel.NORMAL, FluidLevel.NORMAL, FluidLevel.NORMAL))
        assertTrue(f.isEmpty())
        assertEquals(Severity.OK, MaintenanceAdvisor.overall(f))
    }

    @Test fun `엔진오일 압력 경고등은 즉시 정차로 맨 위에 온다`() {
        val f = MaintenanceAdvisor.diagnose(HealthSnapshot(washerLow = true, warningLights = setOf("TPMS", "ENGINE_OIL_PRESSURE")))
        assertEquals(Severity.STOP, f.first().severity)
        assertEquals("엔진오일 압력 경고", f.first().item)
        assertEquals(Severity.STOP, MaintenanceAdvisor.overall(f))
        assertEquals(3, f.size)
    }

    @Test fun `브레이크액 부족은 정차 권고, 엔진오일 부족은 정비소`() {
        val f = MaintenanceAdvisor.diagnose(HealthSnapshot(engineOil = FluidLevel.LOW, brakeFluid = FluidLevel.LOW))
        assertEquals(Severity.STOP, f.first { it.item == "브레이크액" }.severity)
        assertEquals(Severity.SERVICE, f.first { it.item == "엔진오일" }.severity)
    }

    @Test fun `타이어 공기압이 차량 기준보다 낮은 바퀴만 알린다`() {
        val s = HealthSnapshot(
            tirePressure = mapOf(Wheel.LEFT_FRONT to 33f, Wheel.RIGHT_REAR to 25f),
            tireLowThreshold = mapOf(Wheel.LEFT_FRONT to 28f, Wheel.RIGHT_REAR to 28f),
        )
        val f = MaintenanceAdvisor.diagnose(s)
        assertEquals(1, f.size)
        assertTrue(f[0].item.contains("뒤 오른쪽"))
    }

    @Test fun `주행거리로 엔진오일 교체 시점을 계산한다`() {
        val soon = MaintenanceAdvisor.diagnose(HealthSnapshot(odometerKm = 29_400f, lastOilChangeKm = 20_000f))
        assertEquals(Severity.CHECK, soon.single().severity)
        val over = MaintenanceAdvisor.diagnose(HealthSnapshot(odometerKm = 31_000f, lastOilChangeKm = 20_000f))
        assertEquals(Severity.SERVICE, over.single().severity)
    }

    @Test fun `경고등 해석 사전`() {
        assertNotNull(WarningLightGuide.explain("ENGINE"))
        assertEquals(null, WarningLightGuide.explain("TURN_SIGNAL_LEFT"))
    }
}
