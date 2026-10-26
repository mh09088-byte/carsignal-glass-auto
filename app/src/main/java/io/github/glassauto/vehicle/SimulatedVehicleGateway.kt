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

    /** 시연용 차량 상태. 정비 탭의 시뮬레이터 버튼으로 이상 상황을 켜고 끈다. */
    var health = io.github.glassauto.core.HealthSnapshot(
        engineOil = io.github.glassauto.core.FluidLevel.NORMAL,
        brakeFluid = io.github.glassauto.core.FluidLevel.NORMAL,
        transmissionOil = io.github.glassauto.core.FluidLevel.NORMAL,
        tirePressure = io.github.glassauto.core.Wheel.entries.associateWith { 35f },
        tireLowThreshold = io.github.glassauto.core.Wheel.entries.associateWith { 30f },
        odometerKm = 28_450f,
        lastOilChangeKm = 19_000f,
    )

    override fun readHealth(done: (io.github.glassauto.core.HealthSnapshot) -> Unit) = done(health)

    fun simulateGear(gear: Gear) = listener?.onGear(gear)

    fun simulateSeat(seat: SeatPos, occupied: Boolean) = listener?.onSeatOccupancy(seat, occupied)
}
