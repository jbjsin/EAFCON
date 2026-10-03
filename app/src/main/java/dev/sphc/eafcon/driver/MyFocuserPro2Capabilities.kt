package dev.sphc.eafcon.driver

/** Capability metadata shared by the official generic firmware and Gemini console profile. */
object MyFocuserPro2Capabilities {
    private val verified = VerificationState.VERIFIED
    private val sourceOnly = VerificationState.SOURCE_VERIFIED_HARDWARE_UNVERIFIED

    private fun descriptor(
        id: CapabilityId, category: FeatureCategory,
        access: CapabilityAccess = CapabilityAccess.READ_ONLY,
        verification: VerificationState = sourceOnly,
        risk: CapabilityRisk = CapabilityRisk.LOW,
        persistence: PersistenceBehavior = PersistenceBehavior.UNKNOWN,
        requiresIdle: Boolean = false, canMove: Boolean = false,
        changesCoordinates: Boolean = false, min: Double? = null, max: Double? = null,
        reason: String? = null,
    ) = CapabilityDescriptor(
        id, CapabilitySupport.SUPPORTED, access, category, verification, risk, persistence,
        requiresIdle, canMove, changesCoordinates, min, max, unavailableReason = reason,
    )

    fun forProfile(
        type: FocuserType,
        enableUnverifiedDeviceMaximumWrite: Boolean,
        enableUnverifiedProtocolWrites: Boolean,
    ): CapabilitySet {
        val experimental = if (enableUnverifiedProtocolWrites) null
        else "Available only in the EAFCON Dev build"
        val maxWrite = if (enableUnverifiedDeviceMaximumWrite) null
        else "Device-maximum writes are available only in EAFCON Dev"
        val geminiResetVerification = if (type == FocuserType.GEMINI_FOCUSER_PRO) verified else sourceOnly
        val entries = listOf(
            descriptor(CapabilityId.POSITION, FeatureCategory.CORE, verification = verified),
            descriptor(CapabilityId.ABSOLUTE_MOVE, FeatureCategory.CORE, CapabilityAccess.WRITE_ONLY, verified, CapabilityRisk.HIGH, canMove = true),
            descriptor(CapabilityId.STOP, FeatureCategory.CORE, CapabilityAccess.WRITE_ONLY, risk = CapabilityRisk.HIGH),
            descriptor(CapabilityId.MOVING_STATE, FeatureCategory.CORE, verification = verified),
            descriptor(CapabilityId.TEMPERATURE, FeatureCategory.CORE, verification = verified),
            descriptor(CapabilityId.DEVICE_MAX_POSITION, FeatureCategory.CORE, verification = verified),
            descriptor(CapabilityId.FIRMWARE_VERSION, FeatureCategory.CORE),
            descriptor(CapabilityId.FIRMWARE_NAME, FeatureCategory.CORE),
            descriptor(CapabilityId.MAX_INCREMENT, FeatureCategory.CORE),
            descriptor(CapabilityId.TEMPERATURE_PROBE_AVAILABLE, FeatureCategory.CORE),
            descriptor(CapabilityId.STEPPER_POWER, FeatureCategory.CORE),
            descriptor(CapabilityId.REVERSE, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.MEDIUM, requiresIdle = true),
            descriptor(CapabilityId.MOTOR_SPEED, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.MEDIUM, requiresIdle = true, min = 0.0, max = 2.0),
            descriptor(CapabilityId.BACKLASH_IN, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.MEDIUM, requiresIdle = true, canMove = true, min = 0.0, max = 255.0),
            descriptor(CapabilityId.BACKLASH_OUT, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.MEDIUM, requiresIdle = true, canMove = true, min = 0.0, max = 255.0),
            descriptor(CapabilityId.TEMPERATURE_COMPENSATION, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, requiresIdle = true, canMove = true, reason = experimental),
            descriptor(CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, requiresIdle = true, min = 0.0, max = 1000.0, reason = experimental),
            descriptor(CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION, FeatureCategory.ADVANCED, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, requiresIdle = true, reason = experimental),
            descriptor(CapabilityId.STEP_MODE, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, persistence = PersistenceBehavior.YES, requiresIdle = true, changesCoordinates = true),
            descriptor(CapabilityId.SYNC_POSITION, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.WRITE_ONLY, risk = CapabilityRisk.HIGH, requiresIdle = true, changesCoordinates = true, min = 0.0, max = 999_999.0),
            descriptor(CapabilityId.SET_MAX_POSITION, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.WRITE_ONLY, risk = CapabilityRisk.HIGH, requiresIdle = true, min = 1.0, max = 999_999.0, reason = maxWrite),
            descriptor(CapabilityId.COIL_POWER, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, requiresIdle = true),
            descriptor(CapabilityId.HOME, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.WRITE_ONLY, risk = CapabilityRisk.HIGH, requiresIdle = true, canMove = true),
            descriptor(CapabilityId.JOG, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, requiresIdle = true, canMove = true, reason = experimental),
            descriptor(CapabilityId.JOG_DIRECTION, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.HIGH, requiresIdle = true, reason = experimental),
            descriptor(CapabilityId.DELAY_AFTER_MOVE, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, risk = CapabilityRisk.MEDIUM, requiresIdle = true, min = 0.0, max = 255.0, reason = experimental),
            descriptor(CapabilityId.STEP_SIZE_ENABLED, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, requiresIdle = true, reason = experimental),
            descriptor(CapabilityId.STEP_SIZE_VALUE, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, requiresIdle = true, min = 0.0, reason = experimental),
            descriptor(CapabilityId.TEMPERATURE_RESOLUTION, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, min = 9.0, max = 12.0, reason = experimental),
            descriptor(CapabilityId.HOME_SWITCH_AVAILABLE, FeatureCategory.ADMINISTRATIVE),
            descriptor(CapabilityId.HOME_SWITCH_STATE, FeatureCategory.ADMINISTRATIVE),
            descriptor(CapabilityId.DISPLAY_CONFIGURATION, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE),
            descriptor(CapabilityId.DISPLAY_PAGE_TIME, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, min = 2.0, max = 10.0, reason = experimental),
            descriptor(CapabilityId.DISPLAY_UPDATE_ON_MOVE, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, reason = experimental),
            descriptor(CapabilityId.DISPLAY_PAGE_OPTIONS, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE, reason = experimental),
            descriptor(CapabilityId.TEMPERATURE_UNIT, FeatureCategory.ADMINISTRATIVE, CapabilityAccess.READ_WRITE),
            descriptor(CapabilityId.PERSIST_SETTINGS, FeatureCategory.DEVICE_ADMINISTRATION, CapabilityAccess.WRITE_ONLY, risk = CapabilityRisk.HIGH, persistence = PersistenceBehavior.YES, requiresIdle = true, reason = experimental),
            descriptor(CapabilityId.RESET_CONTROLLER, FeatureCategory.DEVICE_ADMINISTRATION, CapabilityAccess.WRITE_ONLY, verification = geminiResetVerification, risk = CapabilityRisk.HIGH, requiresIdle = true, reason = experimental),
            descriptor(CapabilityId.RESTORE_DEFAULTS, FeatureCategory.DEVICE_ADMINISTRATION, CapabilityAccess.WRITE_ONLY, verification = geminiResetVerification, risk = CapabilityRisk.HIGH, persistence = PersistenceBehavior.YES, requiresIdle = true, reason = experimental),
        )
        // Referencing type here is intentional: the two identities share frames but have separate
        // evidence and can diverge later without changing the generic driver contract.
        check(type in FocuserType.entries)
        return CapabilitySet(entries)
    }

    val all = forProfile(FocuserType.GEMINI_FOCUSER_PRO, false, false)
}
