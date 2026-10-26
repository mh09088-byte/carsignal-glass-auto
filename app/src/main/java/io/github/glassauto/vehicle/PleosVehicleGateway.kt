package io.github.glassauto.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import io.github.glassauto.core.FluidLevel
import io.github.glassauto.core.HealthSnapshot
import io.github.glassauto.core.Wheel
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.glassauto.core.Gear
import io.github.glassauto.core.GlassId
import io.github.glassauto.core.LevelMapper

/**
 * Pleos Connect Vehicle SDK 연결.
 * - 조회(사용 가능): 기어(Gear), 좌석 탑승(Seat)
 * - 제어(SDK 개방 시 동작): 스마트글라스 단계(Window.setSmartGlassLevel), 앰비언트 조명(Light)
 * 모든 제어 전에 checkSmartGlassLevelCapabilities 로 지원 유리와 단계 범위를 확인하고,
 * 지원하지 않는 유리는 '가상 적용'(null)으로 돌려 화면에서만 보여 준다.
 */
class PleosVehicleGateway(private val context: Context) : VehicleGateway {

    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var listener: VehicleGateway.Listener? = null
    private val mappers = mutableMapOf<GlassId, LevelMapper>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { main.post { listener?.onGear(gear?.toGear() ?: Gear.UNKNOWN) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener", e) }
    }
    private val seatListeners = mutableMapOf<SeatPos, SeatOccupancyListener>()

    override fun start(listener: VehicleGateway.Listener) {
        this.listener = listener
        val v = Vehicle(context).also { it.initialize() }
        vehicle = v

        // 기어 상태 조회 + 구독
        v.getGear().getCurrentGearSelectionState(
            { g: VehicleGear? -> main.post { listener.onGear(g?.toGear() ?: Gear.UNKNOWN) } },
            { e -> Log.w(TAG, "gear", e) })
        v.getGear().registerGearSelection(gearListener)

        // 좌석 탑승 구독
        SeatPos.entries.forEach { pos ->
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupied: Boolean?) {
                    main.post { listener.onSeatOccupancy(pos, occupied == true) }
                }
                override fun onFailed(e: Exception) { Log.w(TAG, "seat $pos", e) }
            }
            seatListeners[pos] = l
            v.getSeat().registerSeatOccupancy(SeatArea(pos.toSeatValue()), l)
        }

        // 스마트글라스 지원 유리와 단계 범위 확인
        v.getWindow().checkSmartGlassLevelCapabilities({ _, bounds ->
            bounds.forEach { (area, b) ->
                area.values.firstOrNull()?.toGlass()?.let { mappers[it] = LevelMapper(b.minValue, b.maxValue) }
            }
            main.post { listener.onCapabilities(VehicleCapabilities("Pleos Vehicle SDK", mappers.keys.toSet(), true)) }
        }, { e ->
            Log.i(TAG, "smart glass not supported: ${e.message}")
            main.post { listener.onCapabilities(VehicleCapabilities("Pleos Vehicle SDK", emptySet(), true)) }
        })
    }

    override fun stop() {
        val v = vehicle ?: return
        runCatching { v.getGear().unregisterGearSelection(gearListener) }
        seatListeners.forEach { (pos, l) -> runCatching { v.getSeat().unregisterSeatOccupancy(SeatArea(pos.toSeatValue()), l) } }
        seatListeners.clear()
        runCatching { v.release() }
        vehicle = null
        listener = null
    }

    override fun applyTransmittance(glass: GlassId, percent: Int, done: (Int?) -> Unit) {
        val v = vehicle
        val mapper = mappers[glass]
        if (v == null || mapper == null) { done(null); return }
        v.getWindow().setSmartGlassLevel(
            WindowArea(glass.toWindowValue()), mapper.toLevel(percent),
            { _, level: Int? -> main.post { done(level?.let(mapper::toPercent)) } },
            { e -> Log.w(TAG, "setSmartGlassLevel $glass", e); main.post { done(null) } },
        )
    }

    override fun setAmbientWarning(on: Boolean) {
        val v = vehicle ?: return
        // 경고: 밝기 최대 / 해제: 기본 밝기. 색상 값은 차량별 규격 확인 후 확장.
        v.getLight().setAmbientLightBrightness(SeatArea(SeatValue.ROW_1_LEFT), if (on) 100 else 40,
            { _, _ -> }, { e -> Log.w(TAG, "ambient", e) })
    }

    override fun readHealth(done: (HealthSnapshot) -> Unit) {
        val v = vehicle ?: return done(HealthSnapshot())
        var snap = HealthSnapshot()
        val wheels = mapOf(WheelValue.LEFT_FRONT to Wheel.LEFT_FRONT, WheelValue.RIGHT_FRONT to Wheel.RIGHT_FRONT,
            WheelValue.LEFT_REAR to Wheel.LEFT_REAR, WheelValue.RIGHT_REAR to Wheel.RIGHT_REAR)
        var pending = 6 + wheels.size * 2   // 오일 3 + 워셔액 + 경고등 + 주행거리 + 바퀴별(공기압·기준) 2
        // 비동기 IPC 응답을 모두 받으면(성공·실패 무관) 한 번만 돌려준다
        val step: (HealthSnapshot.() -> HealthSnapshot) -> Unit = { f ->
            main.post { snap = snap.f(); if (--pending == 0) done(snap) }
        }
        val fail: (Exception) -> Unit = { e -> Log.w(TAG, "health", e); step { this } }
        val safety = v.getSafety()
        safety.getEngineOilLevelState({ l: OilLevelState? -> step { copy(engineOil = l.toFluid()) } }, fail)
        safety.getBrakeFluidLevelState({ l: OilLevelState? -> step { copy(brakeFluid = l.toFluid()) } }, fail)
        safety.getTransmissionOilLevelState({ l: OilLevelState? -> step { copy(transmissionOil = l.toFluid()) } }, fail)
        safety.hasLowWasherFluidWarning({ b: Boolean? -> step { copy(washerLow = b == true) } }, fail)
        safety.getTelltalesInfo({ t: Telltales? ->
            val on = t?.telltales.orEmpty().filter { it.state != TelltaleState.OFF }.map { it.type.name }.toSet()
            step { copy(warningLights = on) }
        }, fail)
        wheels.forEach { (wv, w) ->
            v.getTire().getTirePressure(WheelArea(wv), { p: Float? -> step { if (p == null) this else copy(tirePressure = tirePressure + (w to p)) } }, fail)
            v.getTire().getLowTirePressure(WheelArea(wv), { p: Float? -> step { if (p == null) this else copy(tireLowThreshold = tireLowThreshold + (w to p)) } }, fail)
        }
        v.getOdometer().getCurrentDistanceDriven({ km: Float? -> step { copy(odometerKm = km) } }, fail)
    }

    private fun OilLevelState?.toFluid(): FluidLevel = when (this) {
        OilLevelState.NORMAL -> FluidLevel.NORMAL
        OilLevelState.LOW -> FluidLevel.LOW
        OilLevelState.CRITICALLY_LOW -> FluidLevel.CRITICALLY_LOW
        OilLevelState.HIGH -> FluidLevel.HIGH
        else -> FluidLevel.UNKNOWN
    }

    private fun VehicleGear.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }

    private fun SeatPos.toSeatValue(): SeatValue = when (this) {
        SeatPos.ROW1_RIGHT -> SeatValue.ROW_1_RIGHT
        SeatPos.ROW2_LEFT -> SeatValue.ROW_2_LEFT
        SeatPos.ROW2_RIGHT -> SeatValue.ROW_2_RIGHT
    }

    private fun GlassId.toWindowValue(): WindowValue = when (this) {
        GlassId.WINDSHIELD -> WindowValue.FRONT_WINDSHIELD
        GlassId.ROW1_LEFT -> WindowValue.ROW_1_LEFT
        GlassId.ROW1_RIGHT -> WindowValue.ROW_1_RIGHT
        GlassId.ROW2_LEFT -> WindowValue.ROW_2_LEFT
        GlassId.ROW2_RIGHT -> WindowValue.ROW_2_RIGHT
        GlassId.REAR -> WindowValue.REAR_WINDSHIELD
        GlassId.ROOF -> WindowValue.ROOF_TOP_1
    }

    private fun WindowValue.toGlass(): GlassId? = when (this) {
        WindowValue.FRONT_WINDSHIELD -> GlassId.WINDSHIELD
        WindowValue.ROW_1_LEFT -> GlassId.ROW1_LEFT
        WindowValue.ROW_1_RIGHT -> GlassId.ROW1_RIGHT
        WindowValue.ROW_2_LEFT -> GlassId.ROW2_LEFT
        WindowValue.ROW_2_RIGHT -> GlassId.ROW2_RIGHT
        WindowValue.REAR_WINDSHIELD -> GlassId.REAR
        WindowValue.ROOF_TOP_1 -> GlassId.ROOF
        else -> null
    }

    companion object {
        private const val TAG = "GlassAuto/Pleos"

        /** Pleos 차량 서비스가 있는 기기인지 확인. 없으면 시뮬레이션으로 동작한다. */
        fun isAvailable(context: Context): Boolean {
            val intent = android.content.Intent("ai.pleos.playground.vehicle.VEHICLE_START")
            return context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
        }
    }
}
