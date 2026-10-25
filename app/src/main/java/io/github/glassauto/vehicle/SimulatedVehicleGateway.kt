package io.github.glassauto.vehicle

import io.github.glassauto.core.Gear
import io.github.glassauto.core.GlassId

/**
 * 시뮬레이션 차량. 스마트글라스가 없는 에뮬레이터·일반 기기에서 앱 화면의 '가상 유리'로 결과를 보여 준다.
 * 기어·좌석 상태는 앱 안 시뮬레이터 패널에서 바꾼다.
 */
class SimulatedVehicleGateway : VehicleGateway {
    private var listener: VehicleGateway.Listener? = null

    override fun start(listener: VehicleGateway.Listener) {
        this.listener = listener
        listener.onCapabilities(VehicleCapabilities(source = "시뮬레이션"))
        listener.onGear(Gear.DRIVE)
    }

    override fun stop() { listener = null }

    override fun applyTransmittance(glass: GlassId, percent: Int, done: (Int?) -> Unit) = done(null)

    override fun setAmbientWarning(on: Boolean) = Unit

    fun simulateGear(gear: Gear) = listener?.onGear(gear)

    fun simulateSeat(seat: SeatPos, occupied: Boolean) = listener?.onSeatOccupancy(seat, occupied)
}
