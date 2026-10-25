package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPolicyTest {
    private val policy = GlassPolicy()
    private val autoDark = GlassId.entries.associateWith { 30 }

    @Test fun `주행 중 앞유리와 1열 옆유리는 법정 하한으로 올라간다`() {
        val r = policy.resolve(GlassInputs(Gear.DRIVE, autoDark, emptyMap()))
        assertEquals(70, r.getValue(GlassId.WINDSHIELD).percent)
        assertEquals(40, r.getValue(GlassId.ROW1_LEFT).percent)
        assertEquals(40, r.getValue(GlassId.ROW1_RIGHT).percent)
        assertEquals(Reason.LEGAL, r.getValue(GlassId.ROW1_RIGHT).reason)
        assertEquals(30, r.getValue(GlassId.ROW2_LEFT).percent)  // 2열은 기준 없음
    }

    @Test fun `P단에서는 법규 제한이 풀린다`() {
        val r = policy.resolve(GlassInputs(Gear.PARK, autoDark, emptyMap()))
        assertEquals(30, r.getValue(GlassId.WINDSHIELD).percent)
        assertEquals(Reason.AUTO, r.getValue(GlassId.WINDSHIELD).reason)
    }

    @Test fun `수동 설정은 자동보다 우선하고 1% 단위로 반영된다`() {
        val r = policy.resolve(GlassInputs(Gear.DRIVE, autoDark, mapOf(GlassId.ROW2_LEFT to 37)))
        assertEquals(37, r.getValue(GlassId.ROW2_LEFT).percent)
        assertEquals(Reason.MANUAL, r.getValue(GlassId.ROW2_LEFT).reason)
    }

    @Test fun `수동으로 더 어둡게 해도 주행 중이면 법규 가드가 이긴다`() {
        val r = policy.resolve(GlassInputs(Gear.DRIVE, autoDark, mapOf(GlassId.ROW1_RIGHT to 33)))
        assertEquals(40, r.getValue(GlassId.ROW1_RIGHT).percent)
        assertEquals(Reason.LEGAL, r.getValue(GlassId.ROW1_RIGHT).reason)
    }

    @Test fun `후진하면 뒤·옆 유리는 수동 설정보다 시야가 우선`() {
        val r = policy.resolve(GlassInputs(Gear.REVERSE, autoDark, mapOf(GlassId.REAR to 15)))
        assertEquals(100, r.getValue(GlassId.REAR).percent)
        assertEquals(Reason.VISIBILITY, r.getValue(GlassId.REAR).reason)
    }

    @Test fun `와이퍼가 켜지면 앞유리를 투명하게`() {
        val r = policy.resolve(GlassInputs(Gear.DRIVE, autoDark, emptyMap(), wiperOn = true))
        assertEquals(100, r.getValue(GlassId.WINDSHIELD).percent)
    }

    @Test fun `프라이버시 모드는 P단에서만 적용된다`() {
        val parked = policy.resolve(GlassInputs(Gear.PARK, autoDark, emptyMap(), privacy = true))
        assertTrue(parked.values.all { it.percent == GlassPolicy.PRIVACY_LEVEL })
        val driving = policy.resolve(GlassInputs(Gear.DRIVE, autoDark, emptyMap(), privacy = true))
        assertEquals(70, driving.getValue(GlassId.WINDSHIELD).percent)
    }

    @Test fun `법규 가드 판정`() {
        val g = LegalGuard()
        assertTrue(g.isLimited(GlassId.ROW1_LEFT, 39, Gear.DRIVE))
        assertFalse(g.isLimited(GlassId.ROW1_LEFT, 40, Gear.DRIVE))
        assertFalse(g.isLimited(GlassId.ROW1_LEFT, 5, Gear.PARK))
        assertEquals(0, g.lowerBound(GlassId.ROOF, Gear.DRIVE))
    }
}
