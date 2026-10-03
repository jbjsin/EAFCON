package dev.sphc.eafcon.driver

enum class CapabilityId {
    POSITION, ABSOLUTE_MOVE, STOP, MOVING_STATE, TEMPERATURE, DEVICE_MAX_POSITION,
    SYNC_POSITION, REVERSE, MOTOR_SPEED, STEP_MODE, BACKLASH_IN, BACKLASH_OUT,
    TEMPERATURE_COMPENSATION, TEMPERATURE_COMPENSATION_COEFFICIENT,
    TEMPERATURE_COMPENSATION_DIRECTION, COIL_POWER, HOME, JOG, JOG_DIRECTION,
    DELAY_AFTER_MOVE, STEP_SIZE_ENABLED, STEP_SIZE_VALUE, TEMPERATURE_RESOLUTION,
    HOME_SWITCH_AVAILABLE, HOME_SWITCH_STATE, DISPLAY_CONFIGURATION, DISPLAY_PAGE_TIME,
    DISPLAY_UPDATE_ON_MOVE, DISPLAY_PAGE_OPTIONS, TEMPERATURE_UNIT, FIRMWARE_VERSION,
    FIRMWARE_NAME, MAX_INCREMENT, TEMPERATURE_PROBE_AVAILABLE, STEPPER_POWER,
    SET_MAX_POSITION, PERSIST_SETTINGS, RESET_CONTROLLER,
    RESTORE_DEFAULTS,
}

enum class CapabilitySupport { SUPPORTED, UNSUPPORTED, UNKNOWN }
enum class CapabilityAccess { READ_ONLY, READ_WRITE, WRITE_ONLY }
enum class FeatureCategory { CORE, ADVANCED, ADMINISTRATIVE, DEVICE_ADMINISTRATION }
enum class VerificationState { VERIFIED, SOURCE_VERIFIED_HARDWARE_UNVERIFIED, UNVERIFIED, UNSUPPORTED, UNKNOWN }
enum class CapabilityRisk { LOW, MEDIUM, HIGH }
enum class PersistenceBehavior { NO, YES, UNKNOWN }

data class CapabilityDescriptor(
    val id: CapabilityId,
    val support: CapabilitySupport,
    val access: CapabilityAccess,
    val category: FeatureCategory,
    val verification: VerificationState,
    val risk: CapabilityRisk,
    val persistence: PersistenceBehavior,
    val requiresIdle: Boolean,
    val canMoveHardware: Boolean = false,
    val changesLogicalCoordinates: Boolean = false,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val default: Double? = null,
    val unavailableReason: String? = null,
) {
    fun validate(value: CapabilityValue) {
        if (value is CapabilityValue.BacklashValue) {
            require(value.steps >= (minimum ?: Int.MIN_VALUE.toDouble())) { "Value must be at least ${minimum}" }
            require(value.steps <= (maximum ?: Int.MAX_VALUE.toDouble())) { "Value must be at most ${maximum}" }
            return
        }
        val number = when (value) {
            is CapabilityValue.IntegerValue -> value.value.toDouble()
            is CapabilityValue.DecimalValue -> value.value
            is CapabilityValue.BooleanValue -> null
            is CapabilityValue.ChoiceValue -> value.value.toDoubleOrNull()
            is CapabilityValue.BacklashValue -> null
            is CapabilityValue.TextValue -> null
            CapabilityValue.TriggerValue -> null
        }
        if (number != null) {
            minimum?.let { require(number >= it) { "Value must be at least $it" } }
            maximum?.let { require(number <= it) { "Value must be at most $it" } }
        }
    }
}

data class CapabilitySet(val descriptors: List<CapabilityDescriptor>) {
    init { require(descriptors.map { it.id }.distinct().size == descriptors.size) }
    operator fun get(id: CapabilityId): CapabilityDescriptor? = descriptors.firstOrNull { it.id == id }
    fun available(category: FeatureCategory? = null): List<CapabilityDescriptor> = descriptors.filter {
        it.support == CapabilitySupport.SUPPORTED && (category == null || it.category == category)
    }
}

sealed interface CapabilityValue {
    data class BooleanValue(val value: Boolean) : CapabilityValue
    data class IntegerValue(val value: Int) : CapabilityValue
    data class DecimalValue(val value: Double) : CapabilityValue
    data class ChoiceValue(val value: String) : CapabilityValue
    data class BacklashValue(val enabled: Boolean, val steps: Int) : CapabilityValue
    data class TextValue(val value: String) : CapabilityValue
    data object TriggerValue : CapabilityValue
}
