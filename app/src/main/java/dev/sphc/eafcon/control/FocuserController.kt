package dev.sphc.eafcon.control

import dev.sphc.eafcon.protocol.GeminiProtocol
import dev.sphc.eafcon.protocol.GeminiResponse
import dev.sphc.eafcon.usb.SerialTransport
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

enum class MovementState { UNKNOWN, IDLE, MOVING }

data class FocuserState(
    val connected: Boolean = false,
    val currentPosition: Int? = null,
    val deviceMaximum: Int? = null,
    val softwareMaximum: Int? = null,
    val movement: MovementState = MovementState.UNKNOWN,
    val temperatureCelsius: Double? = null,
    val commandPending: Boolean = false,
    val lastError: String? = null,
)

class FocuserController(
    private val scope: CoroutineScope,
    private val pollIdleMs: Long = POLL_IDLE_MS,
    private val pollMovingMs: Long = POLL_MOVING_MS,
    private val pollTemperatureMs: Long = POLL_TEMPERATURE_MS,
) {
    private val mutableState = MutableStateFlow(FocuserState())
    val state: StateFlow<FocuserState> = mutableState.asStateFlow()

    private var transport: SerialTransport? = null
    private var pollingJob: Job? = null
    private var lastTemperaturePoll = 0L
    private var commandTarget: Int? = null
    private val movementMutex = Mutex()

    init {
        require(pollIdleMs > 0 && pollMovingMs > 0 && pollTemperatureMs > 0)
    }

    suspend fun connect(serialTransport: SerialTransport) {
        disconnect()
        transport = serialTransport
        try {
            serialTransport.open()
            val handshake = GeminiProtocol.parseResponse(serialTransport.exchange(GeminiProtocol.HANDSHAKE, "EOK"))
            check(handshake == GeminiResponse.Handshake) { "Gemini handshake was not accepted" }
            mutableState.value = FocuserState(connected = true)
            refreshPosition()
            refreshMaximum()
            refreshMovement()
            lastTemperaturePoll = 0L
            pollingJob = scope.launch { pollLoop() }
        } catch (error: Exception) {
            runCatching { serialTransport.close() }
            transport = null
            mutableState.value = FocuserState(lastError = error.message ?: "Connection failed")
            throw error
        }
    }

    fun setSoftwareMaximum(maximum: Int) {
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
        val active = checkNotNull(transport)
        try {
            active.send(GeminiProtocol.STOP)
            commandTarget = null
            // A poll may complete while send() is suspended; preserve that newer state.
            mutableState.value = mutableState.value.copy(commandPending = true, lastError = null)
        } catch (error: Exception) {
            failAndDisconnect(error)
            throw error
        }
    }

    suspend fun disconnect() {
        pollingJob?.cancelAndJoin()
        pollingJob = null
        val previous = transport
        transport = null
        commandTarget = null
        if (previous != null) runCatching { previous.close() }
        mutableState.value = FocuserState()
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
                } else if (current == MovementState.IDLE) {
                    refreshPosition()
                }
                val now = System.currentTimeMillis()
                if (now - lastTemperaturePoll >= pollTemperatureMs) {
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
        val response = request(GeminiProtocol.READ_POSITION)
        check(response is GeminiResponse.Position) { "Unexpected position response" }
        mutableState.value = mutableState.value.copy(currentPosition = response.steps, lastError = null)
    }

    private suspend fun refreshMaximum() {
        val response = request(GeminiProtocol.READ_MAX_POSITION)
        check(response is GeminiResponse.MaximumPosition) { "Unexpected maximum response" }
        mutableState.value = mutableState.value.copy(deviceMaximum = response.steps)
    }

    private suspend fun refreshMovement() {
        val response = request(GeminiProtocol.READ_MOVEMENT)
        check(response is GeminiResponse.Movement) { "Unexpected movement response" }
        mutableState.value = mutableState.value.copy(
            movement = if (response.isMoving) MovementState.MOVING else MovementState.IDLE,
        )
    }

    private suspend fun refreshTemperature() {
        val response = request(GeminiProtocol.READ_TEMPERATURE)
        check(response is GeminiResponse.Temperature) { "Unexpected temperature response" }
        mutableState.value = mutableState.value.copy(temperatureCelsius = response.celsius)
    }

    private suspend fun request(command: String): GeminiResponse {
        val expectedPrefix = when (command) {
            GeminiProtocol.HANDSHAKE -> "EOK"
            GeminiProtocol.READ_POSITION -> "P"
            GeminiProtocol.READ_MOVEMENT -> "I"
            GeminiProtocol.READ_TEMPERATURE -> "Z"
            GeminiProtocol.READ_MAX_POSITION -> "M"
            else -> error("No response type is defined for this command")
        }
        val frame = checkNotNull(transport).exchange(command, expectedPrefix)
        return GeminiProtocol.parseResponse(frame)
    }

    private suspend fun failAndDisconnect(error: Exception, cancelPolling: Boolean = true) {
        val previous = transport
        if (cancelPolling) pollingJob?.cancelAndJoin()
        pollingJob = null
        transport = null
        commandTarget = null
        runCatching { previous?.close() }
        mutableState.value = FocuserState(lastError = error.message ?: "USB serial communication failed")
    }

    private suspend fun moveToLocked(target: Int) {
        val snapshot = mutableState.value
        check(snapshot.connected) { "Connect to a focuser first" }
        check(snapshot.movement == MovementState.IDLE && !snapshot.commandPending) { "Focuser is not idle" }
        val maximum = checkNotNull(snapshot.softwareMaximum) { "Configure the software maximum first" }
        snapshot.deviceMaximum?.let { check(target <= it) { "Target exceeds the device-reported maximum ($it)" } }
        MovementLimits.validateAbsolute(target, maximum)
        val active = checkNotNull(transport)
        commandTarget = target
        mutableState.value = snapshot.copy(commandPending = true, lastError = null)
        try {
            active.send(GeminiProtocol.moveAbsolute(target))
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
