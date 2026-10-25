package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelMapperTest {

    @Test fun `101단계 차량은 1% 그대로 적용된다`() {
        val m = LevelMapper(0, 100)
        assertEquals(63, m.toLevel(37))      // 단계가 클수록 진함
        assertEquals(37, m.applied(37))
    }

    @Test fun `단계가 적은 차량은 가장 가까운 단계로 적용하고 실제 값을 알려 준다`() {
        val m = LevelMapper(0, 4)            // 5단계: 100, 75, 50, 25, 0
        assertEquals(2, m.toLevel(45))
        assertEquals(50, m.applied(45))
        assertEquals(100, m.applied(100))
        assertEquals(0, m.applied(0))
    }

    @Test fun `단계 방향이 반대인 차량도 지원`() {
        val m = LevelMapper(1, 3, darkerIsHigher = false)   // 1 = 진함, 3 = 투명
        assertEquals(3, m.toLevel(100))
        assertEquals(1, m.toLevel(0))
        assertEquals(50, m.applied(55))
    }

    @Test fun `단계가 하나뿐이면 항상 같은 단계`() {
        val m = LevelMapper(1, 1)
        assertEquals(1, m.toLevel(20))
        assertEquals(100, m.toPercent(1))
    }
}
