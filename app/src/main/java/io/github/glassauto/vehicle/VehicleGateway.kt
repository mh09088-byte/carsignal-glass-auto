package io.github.glassauto.vehicle

import io.github.glassauto.core.Gear
import io.github.glassauto.core.GlassId

/** 차량 연결 상태와 지원 기능. */
data class VehicleCapabilities(
    val source: String,                       // "Pleos Vehicle SDK" 또는 "시뮬레이션"
    val smartGlass: Set<GlassId> = emptySet(),// 실제 스마트글라스 제어가 되는 유리
    val ambientLight: Boolean = false,
)

/** 좌석 탑승 감지 대상 (썬 케어·동승자 맞춤용). */
enum class SeatPos { ROW1_RIGHT, ROW2_LEFT, ROW2_RIGHT }

/**
 * 차량 접근 계층. UI·정책 코드는 이 인터페이스만 사용하고,
 * 실제 차량(Pleos SDK)과 시뮬레이션 중 무엇이 붙었는지는 모른다.
 */
interface VehicleGateway {
    interface Listener {
        fun onCapabilities(caps: VehicleCapabilities)
        fun onGear(gear: Gear)
        fun onSeatOccupancy(seat: SeatPos, occupied: Boolean)
    }

    fun start(listener: Listener)
    fun stop()

    /**
     * 유리 투과율 적용. [done] 에는 차량이 실제로 적용한 투과율(%)을 넘긴다.
     * 스마트글라스가 없는 유리는 null(가상 적용)로 알린다.
     */
    fun applyTransmittance(glass: GlassId, percent: Int, done: (Int?) -> Unit)

    /** 긴급차량 기능용 실내 조명 경고. 유리와는 무관하다. */
    fun setAmbientWarning(on: Boolean)

    /** 오토정비: 오일·워셔액·경고등·타이어·주행거리를 한 번에 읽는다 (Vehicle State SDK — 현재 사용 가능). */
    fun readHealth(done: (io.github.glassauto.core.HealthSnapshot) -> Unit)
}
