package dev.sphc.eafcon.driver


enum class MovementState { UNKNOWN, IDLE, MOVING }

/** Transport-neutral core contract. Optional capabilities are separate interfaces. */
interface FocuserDriver {
    val descriptor: FocuserDriverDescriptor
    suspend fun connect()
    suspend fun disconnect()
    suspend fun readPosition(): Int
    suspend fun readMovementState(): MovementState
    suspend fun moveAbsolute(position: Int)
    suspend fun stop()
}

interface DeviceMaximumReader {
    suspend fun readDeviceMaximum(): Int
}

interface TemperatureReader {
    suspend fun readTemperatureCelsius(): Double
}

interface CapabilitySettingsDriver {
    suspend fun readCapability(id: CapabilityId): CapabilityValue
    suspend fun writeCapability(id: CapabilityId, value: CapabilityValue)
}

data class FocuserDriverDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: CapabilitySet,
)
