package dev.sphc.eafcon.driver

import dev.sphc.eafcon.driver.MovementState
import dev.sphc.eafcon.protocol.MyFocuserPro2Protocol
import dev.sphc.eafcon.protocol.GeminiResponse
import dev.sphc.eafcon.usb.SerialTransport

/** Serial and ASCII details stay inside this concrete driver. */
class MyFocuserPro2Driver(
    private val focuserType: FocuserType = FocuserType.GEMINI_FOCUSER_PRO,
    private val enableUnverifiedDeviceMaximumWrite: Boolean = false,
    private val enableUnverifiedProtocolWrites: Boolean = false,
    private val transportFactory: suspend () -> SerialTransport,
) : FocuserDriver, DeviceMaximumReader, TemperatureReader, CapabilitySettingsDriver {
    override val descriptor = FocuserDriverDescriptor(
        id = focuserType.persistentId,
        displayName = focuserType.displayName,
        capabilities = MyFocuserPro2Capabilities.forProfile(
            focuserType,
            enableUnverifiedDeviceMaximumWrite,
            enableUnverifiedProtocolWrites,
        ),
    )

    private var transport: SerialTransport? = null

    override suspend fun connect() {
        disconnect()
        val opened = transportFactory()
        try {
            opened.open()
            val response = opened.exchange(MyFocuserPro2Protocol.LEGACY_HANDSHAKE, "EOK")
            check(MyFocuserPro2Protocol.parseCoreResponse(response) == GeminiResponse.Handshake) {
                "MyFocuserPro2 handshake was not accepted"
            }
            transport = opened
        } catch (error: Exception) {
            runCatching { opened.close() }
            throw error
        }
    }

    override suspend fun disconnect() {
        val previous = transport
        transport = null
        if (previous != null) runCatching { previous.close() }
    }

    override suspend fun readPosition(): Int = parseCore(MyFocuserPro2Protocol.READ_POSITION, "P") {
        (it as? GeminiResponse.Position)?.steps ?: error("Unexpected position response")
    }

    override suspend fun readMovementState(): MovementState = parseCore(MyFocuserPro2Protocol.READ_MOVEMENT, "I") {
        val movement = it as? GeminiResponse.Movement ?: error("Unexpected movement response")
        if (movement.isMoving) MovementState.MOVING else MovementState.IDLE
    }

    override suspend fun readDeviceMaximum(): Int = parseCore(MyFocuserPro2Protocol.READ_MAX_POSITION, "M") {
        (it as? GeminiResponse.MaximumPosition)?.steps ?: error("Unexpected maximum response")
    }

    override suspend fun readTemperatureCelsius(): Double = parseCore(MyFocuserPro2Protocol.READ_TEMPERATURE, "Z") {
        (it as? GeminiResponse.Temperature)?.celsius ?: error("Unexpected temperature response")
    }

    override suspend fun moveAbsolute(position: Int) {
        checkNotNull(transport) { "Connect to a focuser first" }.send(MyFocuserPro2Protocol.moveAbsolute(position))
    }

    override suspend fun stop() {
        checkNotNull(transport) { "Connect to a focuser first" }.send(MyFocuserPro2Protocol.STOP)
    }

    override suspend fun readCapability(id: CapabilityId): CapabilityValue = when (id) {
        CapabilityId.REVERSE -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseReverse(exchange(MyFocuserPro2Protocol.readReverse(), "R")),
        )
        CapabilityId.MOTOR_SPEED -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseMotorSpeed(exchange(MyFocuserPro2Protocol.readMotorSpeed(), "C")),
        )
        CapabilityId.BACKLASH_IN -> CapabilityValue.BacklashValue(
            enabled = MyFocuserPro2Protocol.parseBacklashInEnabled(exchange(MyFocuserPro2Protocol.readBacklashInEnabled(), "4")),
            steps = MyFocuserPro2Protocol.parseBacklashInSteps(exchange(MyFocuserPro2Protocol.readBacklashInSteps(), "6")),
        )
        CapabilityId.BACKLASH_OUT -> CapabilityValue.BacklashValue(
            enabled = MyFocuserPro2Protocol.parseBacklashOutEnabled(exchange(MyFocuserPro2Protocol.readBacklashOutEnabled(), "5")),
            steps = MyFocuserPro2Protocol.parseBacklashOutSteps(exchange(MyFocuserPro2Protocol.readBacklashOutSteps(), "7")),
        )
        CapabilityId.TEMPERATURE_COMPENSATION -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseTemperatureCompensation(exchange(MyFocuserPro2Protocol.readTemperatureCompensation(), "1")),
        )
        CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseTemperatureCoefficient(exchange(MyFocuserPro2Protocol.readTemperatureCoefficient(), "B")),
        )
        CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseTemperatureCompensationDirection(exchange(MyFocuserPro2Protocol.readTemperatureCompensationDirection(), "k")),
        )
        CapabilityId.STEP_MODE -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseStepMode(exchange(MyFocuserPro2Protocol.readStepMode(), "S")),
        )
        CapabilityId.COIL_POWER -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseCoilPower(exchange(MyFocuserPro2Protocol.readCoilPower(), "O")),
        )
        CapabilityId.DISPLAY_CONFIGURATION -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseDisplayEnabled(exchange(MyFocuserPro2Protocol.readDisplayEnabled(), "D")),
        )
        CapabilityId.TEMPERATURE_UNIT -> CapabilityValue.ChoiceValue(
            if (MyFocuserPro2Protocol.parseTemperatureDisplayCelsius(exchange(MyFocuserPro2Protocol.readTemperatureDisplayUnit(), "b"))) "CELSIUS" else "FAHRENHEIT",
        )
        CapabilityId.TEMPERATURE_RESOLUTION -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseTemperatureResolution(exchange(MyFocuserPro2Protocol.readTemperatureResolution(), "Q")),
        )
        CapabilityId.STEP_SIZE_ENABLED -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseStepSizeEnabled(exchange(MyFocuserPro2Protocol.readStepSizeEnabled(), "U")),
        )
        CapabilityId.STEP_SIZE_VALUE -> CapabilityValue.DecimalValue(
            MyFocuserPro2Protocol.parseStepSize(exchange(MyFocuserPro2Protocol.readStepSize(), "T")),
        )
        CapabilityId.DELAY_AFTER_MOVE -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseDelayAfterMove(exchange(MyFocuserPro2Protocol.readDelayAfterMove(), "3")),
        )
        CapabilityId.JOG -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseJogEnabled(exchange(MyFocuserPro2Protocol.readJogEnabled(), "K")),
        )
        CapabilityId.JOG_DIRECTION -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseJogDirection(exchange(MyFocuserPro2Protocol.readJogDirection(), "V")),
        )
        CapabilityId.DISPLAY_PAGE_TIME -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseDisplayPageTimeMilliseconds(exchange(MyFocuserPro2Protocol.readDisplayPageTime(), "X")) / 1000,
        )
        CapabilityId.DISPLAY_UPDATE_ON_MOVE -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseDisplayUpdateOnMove(exchange(MyFocuserPro2Protocol.readDisplayUpdateOnMove(), "L")),
        )
        CapabilityId.DISPLAY_PAGE_OPTIONS -> CapabilityValue.TextValue(
            MyFocuserPro2Protocol.parseDisplayPageOptions(exchange(MyFocuserPro2Protocol.readDisplayPageOptions(), "l")),
        )
        CapabilityId.HOME_SWITCH_AVAILABLE -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseHomeSwitchAvailable(exchange(MyFocuserPro2Protocol.readHomeSwitchAvailable(), "l")),
        )
        CapabilityId.HOME_SWITCH_STATE -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseHomeSwitchState(exchange(MyFocuserPro2Protocol.readHomeSwitchState(), "H")),
        )
        CapabilityId.FIRMWARE_VERSION -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseFirmwareVersion(exchange(MyFocuserPro2Protocol.READ_FIRMWARE_VERSION, "F")),
        )
        CapabilityId.FIRMWARE_NAME -> CapabilityValue.TextValue(
            MyFocuserPro2Protocol.parseFirmwareName(exchange(":04#", "F")),
        )
        CapabilityId.MAX_INCREMENT -> CapabilityValue.IntegerValue(
            MyFocuserPro2Protocol.parseMaximumIncrement(exchange(MyFocuserPro2Protocol.readMaximumIncrement(), "Y")),
        )
        CapabilityId.TEMPERATURE_PROBE_AVAILABLE -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseTemperatureProbeAvailable(exchange(MyFocuserPro2Protocol.readTemperatureProbeAvailable(), "c")),
        )
        CapabilityId.STEPPER_POWER -> CapabilityValue.BooleanValue(
            MyFocuserPro2Protocol.parseStepperPower(exchange(MyFocuserPro2Protocol.readStepperPower(), "9")),
        )
        else -> throw UnsupportedOperationException("Advanced setting is not exposed: $id")
    }

    override suspend fun writeCapability(id: CapabilityId, value: CapabilityValue) {
        when (id) {
            CapabilityId.REVERSE -> {
                val enabled = (value as? CapabilityValue.BooleanValue)?.value
                    ?: error("Reverse expects a Boolean value")
                send(MyFocuserPro2Protocol.setReverse(enabled))
            }
            CapabilityId.MOTOR_SPEED -> {
                val speed = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Motor speed expects an integer value")
                send(MyFocuserPro2Protocol.setMotorSpeed(speed, focuserType == FocuserType.GEMINI_FOCUSER_PRO))
            }
            CapabilityId.BACKLASH_IN -> {
                val setting = (value as? CapabilityValue.BacklashValue)
                    ?: error("Backlash IN expects enabled and step values")
                send(MyFocuserPro2Protocol.setBacklashInSteps(setting.steps))
                send(MyFocuserPro2Protocol.setBacklashInEnabled(setting.enabled))
            }
            CapabilityId.BACKLASH_OUT -> {
                val setting = (value as? CapabilityValue.BacklashValue)
                    ?: error("Backlash OUT expects enabled and step values")
                send(MyFocuserPro2Protocol.setBacklashOutSteps(setting.steps))
                send(MyFocuserPro2Protocol.setBacklashOutEnabled(setting.enabled))
            }
            CapabilityId.TEMPERATURE_COMPENSATION -> {
                val enabled = (value as? CapabilityValue.BooleanValue)?.value ?: error("Temperature compensation expects a Boolean value")
                send(MyFocuserPro2Protocol.setTemperatureCompensation(enabled))
            }
            CapabilityId.TEMPERATURE_COMPENSATION_COEFFICIENT -> {
                val coefficient = (value as? CapabilityValue.IntegerValue)?.value ?: error("Temperature coefficient expects an integer")
                send(MyFocuserPro2Protocol.setTemperatureCoefficient(coefficient))
            }
            CapabilityId.TEMPERATURE_COMPENSATION_DIRECTION -> {
                val outward = (value as? CapabilityValue.BooleanValue)?.value ?: error("Temperature compensation direction expects a Boolean value")
                send(MyFocuserPro2Protocol.setTemperatureCompensationDirection(outward))
            }
            CapabilityId.STEP_MODE -> {
                val mode = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Step Mode expects one of the documented integer modes")
                send(MyFocuserPro2Protocol.setStepMode(mode))
            }
            CapabilityId.SYNC_POSITION -> {
                val position = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Sync Position expects an integer position")
                send(MyFocuserPro2Protocol.syncPosition(position))
            }
            CapabilityId.SET_MAX_POSITION -> {
                check(enableUnverifiedDeviceMaximumWrite) {
                    "Unverified device-maximum writes are enabled only in the EAFCON Dev build"
                }
                val position = (value as? CapabilityValue.IntegerValue)?.value
                    ?: error("Device maximum expects an integer")
                send(MyFocuserPro2Protocol.setDeviceMaximum(position))
            }
            CapabilityId.COIL_POWER -> {
                val enabled = (value as? CapabilityValue.BooleanValue)?.value
                    ?: error("Coil power expects a Boolean value")
                send(MyFocuserPro2Protocol.setCoilPower(enabled))
            }
            CapabilityId.HOME -> {
                require(value == CapabilityValue.TriggerValue) { "Home is a one-shot action" }
                send(MyFocuserPro2Protocol.home())
            }
            CapabilityId.DISPLAY_CONFIGURATION -> {
                val enabled = (value as? CapabilityValue.BooleanValue)?.value
                    ?: error("Display configuration expects a Boolean value")
                send(MyFocuserPro2Protocol.setDisplayEnabled(enabled))
            }
            CapabilityId.TEMPERATURE_UNIT -> {
                val unit = (value as? CapabilityValue.ChoiceValue)?.value ?: error("Temperature unit expects CELSIUS or FAHRENHEIT")
                send(when (unit) {
                    "CELSIUS" -> MyFocuserPro2Protocol.setTemperatureDisplayCelsius()
                    "FAHRENHEIT" -> MyFocuserPro2Protocol.setTemperatureDisplayFahrenheit()
                    else -> error("Unsupported temperature unit")
                })
            }
            CapabilityId.TEMPERATURE_RESOLUTION -> send(MyFocuserPro2Protocol.setTemperatureResolution((value as CapabilityValue.IntegerValue).value))
            CapabilityId.STEP_SIZE_ENABLED -> send(MyFocuserPro2Protocol.setStepSizeEnabled((value as CapabilityValue.BooleanValue).value))
            CapabilityId.STEP_SIZE_VALUE -> send(MyFocuserPro2Protocol.setStepSize((value as CapabilityValue.DecimalValue).value))
            CapabilityId.DELAY_AFTER_MOVE -> send(MyFocuserPro2Protocol.setDelayAfterMove((value as CapabilityValue.IntegerValue).value))
            CapabilityId.JOG -> send(MyFocuserPro2Protocol.setJogEnabled((value as CapabilityValue.BooleanValue).value))
            CapabilityId.JOG_DIRECTION -> send(MyFocuserPro2Protocol.setJogDirection((value as CapabilityValue.BooleanValue).value))
            CapabilityId.DISPLAY_PAGE_TIME -> send(MyFocuserPro2Protocol.setDisplayPageTime((value as CapabilityValue.IntegerValue).value))
            CapabilityId.DISPLAY_UPDATE_ON_MOVE -> send(MyFocuserPro2Protocol.setDisplayUpdateOnMove((value as CapabilityValue.BooleanValue).value))
            CapabilityId.DISPLAY_PAGE_OPTIONS -> send(MyFocuserPro2Protocol.setDisplayPageOptions((value as CapabilityValue.TextValue).value))
            CapabilityId.PERSIST_SETTINGS -> {
                require(value == CapabilityValue.TriggerValue)
                send(MyFocuserPro2Protocol.persistSettings())
            }
            CapabilityId.RESET_CONTROLLER -> {
                require(value == CapabilityValue.TriggerValue)
                send(MyFocuserPro2Protocol.resetController())
            }
            CapabilityId.RESTORE_DEFAULTS -> {
                require(value == CapabilityValue.TriggerValue)
                send(MyFocuserPro2Protocol.restoreDefaults())
            }
            else -> throw UnsupportedOperationException("Advanced setting is not exposed: $id")
        }
    }

    private suspend fun send(command: String) = checkNotNull(transport) { "Connect to a focuser first" }.send(command)

    private suspend fun exchange(command: String, expectedPrefix: String): String =
        checkNotNull(transport) { "Connect to a focuser first" }.exchange(command, expectedPrefix)

    private suspend fun <T> parseCore(command: String, prefix: String, convert: (GeminiResponse) -> T): T {
        val frame = exchange(command, prefix)
        return convert(MyFocuserPro2Protocol.parseCoreResponse(frame))
    }
}
