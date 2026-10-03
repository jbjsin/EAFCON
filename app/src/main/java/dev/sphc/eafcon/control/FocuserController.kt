package dev.sphc.eafcon.control

import dev.sphc.eafcon.driver.CapabilityAccess
import dev.sphc.eafcon.driver.CapabilityId
import dev.sphc.eafcon.driver.CapabilitySettingsDriver
import dev.sphc.eafcon.driver.CapabilitySupport
import dev.sphc.eafcon.driver.CapabilityValue
import dev.sphc.eafcon.driver.DeviceMaximumReader
import dev.sphc.eafcon.driver.FeatureCategory
import dev.sphc.eafcon.driver.FocuserDriver
import dev.sphc.eafcon.driver.TemperatureReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias MovementState = dev.sphc.eafcon.driver.MovementState

data class FocuserState(
    val connected: Boolean = false,
    val currentPosition: Int? = null,
    val deviceMaximum: Int? = null,
    val softwareMaximum: Int? = null,
    val movement: MovementState = MovementState.UNKNOWN,
    val temperatureCelsius: Double? = null,
    val commandPending: Boolean = false,
    val lastError: String? = null,
    val positionNeedsSync: Boolean = false,
)

class FocuserController(
    private val scope: CoroutineScope,
    private val pollIdleMs: Long = POLL_IDLE_MS,
    private val pollMovingMs: Long = POLL_MOVING_MS,
    private val pollTemperatureMs: Long = POLL_TEMPERATURE_MS,
    private val positionSyncStore: PositionSyncRequirementStore = InMemoryPositionSyncRequirementStore(),
) {
    private val mutableState = MutableStateFlow(FocuserState())
    val state: StateFlow<FocuserState> = mutableState.asStateFlow()
    private var driver: FocuserDriver? = null
    private var pollingJob: Job? = null
    private var lastTemperaturePoll = 0L
    private var commandTarget: Int? = null
    private var positionNeedsSync = positionSyncStore.isRequired()
    private val movementMutex = Mutex()

    init { require(pollIdleMs > 0 && pollMovingMs > 0 && pollTemperatureMs > 0) }

    suspend fun connect(selectedDriver: FocuserDriver) {
        disconnect()
        // Another controller instance may have persisted a Step Mode change since this
        // instance was constructed. Reload the safety requirement on explicit connect.
        positionNeedsSync = positionSyncStore.isRequired()
        driver = selectedDriver
        try {
            selectedDriver.connect()
            mutableState.value = FocuserState(connected = true, positionNeedsSync = positionNeedsSync)
            refreshPosition()
            val descriptor = selectedDriver.descriptor.capabilities[CapabilityId.DEVICE_MAX_POSITION]
            if (descriptor?.support == CapabilitySupport.SUPPORTED && selectedDriver is DeviceMaximumReader) {
                refreshMaximum()
            }
            refreshMovement()
            if (selectedDriver is TemperatureReader &&
                selectedDriver.descriptor.capabilities[CapabilityId.TEMPERATURE]?.support == CapabilitySupport.SUPPORTED
            ) refreshTemperature()
            lastTemperaturePoll = System.currentTimeMillis()
            pollingJob = scope.launch { pollLoop() }
        } catch (error: Exception) {
            runCatching { selectedDriver.disconnect() }
            driver = null
            mutableState.value = FocuserState(lastError = error.message ?: "Connection failed")
            throw error
        }
    }

    val capabilities get() = driver?.descriptor?.capabilities
    val driverDescriptor get() = driver?.descriptor

    fun setSoftwareMaximum(maximum: Int) {
        check(!positionNeedsSync) { "Sync the focuser position before setting movement limits" }
        require(maximum > 0) { "Software maximum must be positive" }
        val deviceMaximum = mutableState.value.deviceMaximum
        require(deviceMaximum == null || maximum <= deviceMaximum) {
            "Software maximum cannot exceed the device-reported maximum ($deviceMaximum)"
        }
        mutableState.value = mutableState.value.copy(softwareMaximum = maximum)
    }

    suspend fun moveTo(target: Int) = movementMutex.withLock { moveToLocked(target) }

    suspend fun moveBy(delta: Int) = movementMutex.withLock {
        val snapshot = mutableState.value
        check(snapshot.movement == MovementState.IDLE && !snapshot.commandPending) { "Focuser is not idle" }
        val maximum = checkNotNull(snapshot.softwareMaximum) { "Configure the software maximum first" }
        val target = MovementLimits.relativeTarget(snapshot.currentPosition, delta, maximum)
        moveToLocked(target)
    }

    suspend fun stop() = movementMutex.withLock {
        val snapshot = mutableState.value
        check(snapshot.connected) { "Connect to a focuser first" }
        check(snapshot.movement == MovementState.MOVING) { "Focuser is not moving" }
        try {
            checkNotNull(driver).stop()
            commandTarget = null
            mutableState.value = mutableState.value.copy(commandPending = true, lastError = null)
        } catch (error: Exception) {
            failAndDisconnect(error)
            throw error
        }
    }

    suspend fun readCapability(
        id: CapabilityId,
        category: FeatureCategory = FeatureCategory.ADVANCED,
    ): CapabilityValue = movementMutex.withLock {
        val selected = requireCapability(id, category, requireWrite = false)
        check(mutableState.value.connected) { "Connect to a focuser first" }
        if (selected.requiresIdle) {
            check(mutableState.value.movement == MovementState.IDLE) { "Focuser is not idle" }
            check(checkNotNull(driver).readMovementState() == MovementState.IDLE) { "Focuser is not idle" }
        }
        val access = driver as? CapabilitySettingsDriver
            ?: throw UnsupportedOperationException("This driver has no settings interface")
        access.readCapability(id)
    }

    suspend fun writeCapability(
        id: CapabilityId,
        value: CapabilityValue,
        category: FeatureCategory = FeatureCategory.ADVANCED,
    ): CapabilityValue = movementMutex.withLock {
        val selected = requireCapability(id, category, requireWrite = true)
        check(mutableState.value.connected) { "Connect to a focuser first" }
        check(mutableState.value.movement == MovementState.IDLE && !mutableState.value.commandPending) {
            "Focuser is not idle"
        }
        check(checkNotNull(driver).readMovementState() == MovementState.IDLE) { "Focuser is not idle" }
        selected.validate(value)
        when (id) {
            CapabilityId.SYNC_POSITION -> {
                val requested = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Sync Position expects an integer")
                mutableState.value.deviceMaximum?.let { require(requested <= it) { "Synced position exceeds device maximum ($it)" } }
                if (!positionNeedsSync) {
                    mutableState.value.softwareMaximum?.let { require(requested <= it) { "Synced position exceeds software maximum ($it)" } }
                }
            }
            CapabilityId.SET_MAX_POSITION -> {
                check(!positionNeedsSync) { "Sync the focuser position before setting movement limits" }
                val requested = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Device maximum expects an integer")
                val current = checkNotNull(mutableState.value.currentPosition) { "Current position is unknown" }
                require(requested >= current) { "Device maximum cannot be below current position ($current)" }
                mutableState.value.softwareMaximum?.let {
                    require(requested >= it) { "Device maximum cannot be below the active software maximum ($it)" }
                }
            }
            CapabilityId.HOME -> {
                check(!positionNeedsSync && mutableState.value.currentPosition != null) { "Sync the focuser position before using Home" }
                check(mutableState.value.softwareMaximum != null) { "Configure the software maximum first" }
            }
            CapabilityId.STEP_MODE -> {
                check(!positionNeedsSync) { "Sync the focuser position before changing Step Mode again" }
                val mode = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Step Mode expects an integer")
                require(mode in setOf(1, 2, 4, 8, 16, 32, 64, 128, 256)) { "Unsupported MyFocuserPro2 Step Mode" }
            }
            else -> Unit
        }
        val access = driver as? CapabilitySettingsDriver
            ?: throw UnsupportedOperationException("This driver has no settings interface")
        if (id == CapabilityId.STEP_MODE) {
            // Persist invalidation before transmitting: a disconnect after device-side change must not
            // let a later process trust stale coordinates.
            positionSyncStore.setRequired(true)
            positionNeedsSync = true
            mutableState.value = mutableState.value.copy(
                currentPosition = null,
                positionNeedsSync = true,
                softwareMaximum = null,
            )
        }
        access.writeCapability(id, value)
        when (id) {
            CapabilityId.SYNC_POSITION -> {
                val requested = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Sync Position expects an integer")
                val actual = checkNotNull(driver).readPosition()
                check(actual == requested) { "Focuser did not confirm the synced position" }
                positionSyncStore.setRequired(false)
                positionNeedsSync = false
                mutableState.value = mutableState.value.copy(currentPosition = actual, positionNeedsSync = false)
                CapabilityValue.IntegerValue(actual)
            }
            CapabilityId.SET_MAX_POSITION -> {
                val requested = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Device maximum expects an integer")
                val actual = (driver as? DeviceMaximumReader)?.readDeviceMaximum()
                    ?: throw UnsupportedOperationException("This driver cannot read back device maximum")
                check(actual == requested) { "Focuser did not confirm the device maximum" }
                mutableState.value = mutableState.value.copy(deviceMaximum = actual)
                CapabilityValue.IntegerValue(actual)
            }
            CapabilityId.STEP_MODE -> {
                val actual = access.readCapability(id)
                check(actual == value) { "Focuser did not confirm Step Mode" }
                mutableState.value = mutableState.value.copy(
                    currentPosition = null,
                    positionNeedsSync = true,
                    softwareMaximum = null,
                )
                actual
            }
            CapabilityId.HOME -> value // This protocol has no reliable acknowledgment; UI must say unconfirmed.
            else -> if (category == FeatureCategory.DEVICE_ADMINISTRATION && selected.access == CapabilityAccess.WRITE_ONLY) {
                value // High-risk one-shot operations have no generic readback contract.
            } else {
                access.readCapability(id).also {
                    check(it == value) { "Focuser did not confirm $id setting" }
                }
            }
        }
    }

    suspend fun disconnect() {
        pollingJob?.cancelAndJoin()
        pollingJob = null
        val previous = driver
        driver = null
        commandTarget = null
        if (previous != null) runCatching { previous.disconnect() }
        mutableState.value = FocuserState(positionNeedsSync = positionNeedsSync)
    }

    private fun requireCapability(id: CapabilityId, category: FeatureCategory, requireWrite: Boolean) =
        checkNotNull(driver?.descriptor?.capabilities?.get(id)) { "Unsupported capability: $id" }.also { descriptor ->
            check(descriptor.support == CapabilitySupport.SUPPORTED && descriptor.unavailableReason == null) {
                descriptor.unavailableReason ?: "Unsupported capability: $id"
            }
            check(descriptor.category == category) { "$id is not available in the $category interface" }
            if (requireWrite) check(descriptor.access == CapabilityAccess.READ_WRITE || descriptor.access == CapabilityAccess.WRITE_ONLY) {
                "$id is read-only"
            }
        }

    private suspend fun pollLoop() {
        while (mutableState.value.connected) {
            try {
                val startedMoving = mutableState.value.movement == MovementState.MOVING
                refreshMovement()
                val current = mutableState.value.movement
                if (startedMoving && current == MovementState.IDLE) {
                    refreshPosition()
                    commandTarget = null
                    mutableState.value = mutableState.value.copy(commandPending = false)
                } else if (current == MovementState.IDLE && mutableState.value.commandPending) {
                    refreshPosition()
                    if (commandTarget == null || mutableState.value.currentPosition == commandTarget) {
                        commandTarget = null
                        mutableState.value = mutableState.value.copy(commandPending = false)
                    }
                } else if (current == MovementState.IDLE) refreshPosition()

                val now = System.currentTimeMillis()
                if (driver is TemperatureReader && now - lastTemperaturePoll >= pollTemperatureMs) {
                    refreshTemperature()
                    lastTemperaturePoll = now
                }
                delay(if (current == MovementState.MOVING || mutableState.value.commandPending) pollMovingMs else pollIdleMs)
            } catch (error: Exception) {
                failAndDisconnect(error, cancelPolling = false)
                return
            }
        }
    }

    private suspend fun refreshPosition() {
        if (positionNeedsSync) return
        val position = checkNotNull(driver).readPosition()
        require(position >= 0) { "Focuser returned a negative position" }
        mutableState.value = mutableState.value.copy(currentPosition = position, lastError = null)
    }

    private suspend fun refreshMaximum() {
        val maximum = (checkNotNull(driver) as DeviceMaximumReader).readDeviceMaximum()
        require(maximum > 0) { "Focuser returned an invalid maximum position" }
        mutableState.value = mutableState.value.copy(deviceMaximum = maximum)
    }

    private suspend fun refreshMovement() {
        mutableState.value = mutableState.value.copy(movement = checkNotNull(driver).readMovementState())
    }

    private suspend fun refreshTemperature() {
        val temperature = (checkNotNull(driver) as TemperatureReader).readTemperatureCelsius()
        require(temperature.isFinite()) { "Focuser returned an invalid temperature" }
        mutableState.value = mutableState.value.copy(temperatureCelsius = temperature)
    }

    private suspend fun failAndDisconnect(error: Exception, cancelPolling: Boolean = true) {
        val previous = driver
        if (cancelPolling) pollingJob?.cancelAndJoin()
        pollingJob = null
        driver = null
        commandTarget = null
        runCatching { previous?.disconnect() }
        mutableState.value = FocuserState(
            lastError = error.message ?: "Focuser communication failed",
            positionNeedsSync = positionNeedsSync,
        )
    }

    private suspend fun moveToLocked(target: Int) {
        val snapshot = mutableState.value
        check(snapshot.connected) { "Connect to a focuser first" }
        check(!positionNeedsSync) { "Sync the focuser position before moving after a Step Mode change" }
        check(snapshot.movement == MovementState.IDLE && !snapshot.commandPending) { "Focuser is not idle" }
        val maximum = checkNotNull(snapshot.softwareMaximum) { "Configure the software maximum first" }
        snapshot.deviceMaximum?.let { check(target <= it) { "Target exceeds the device-reported maximum ($it)" } }
        MovementLimits.validateAbsolute(target, maximum)
        commandTarget = target
        mutableState.value = snapshot.copy(commandPending = true, lastError = null)
        try {
            checkNotNull(driver).moveAbsolute(target)
        } catch (error: Exception) {
            failAndDisconnect(error)
            throw error
        }
    }

    private companion object {
        const val POLL_IDLE_MS = 750L
        const val POLL_MOVING_MS = 250L
        const val POLL_TEMPERATURE_MS = 8_000L
    }
}
