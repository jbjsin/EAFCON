package dev.sphc.eafcon.protocol

/** Typed MyFocuserPro2 frame catalog. This layer has no Android or transport dependencies. */
object MyFocuserPro2Protocol {
    const val LEGACY_HANDSHAKE = ":02#"
    const val READ_FIRMWARE_VERSION = ":03#"
    const val READ_POSITION = ":00#"
    const val READ_MOVEMENT = ":01#"
    const val READ_TEMPERATURE = ":06#"
    const val READ_MAX_POSITION = ":08#"
    const val STOP = ":27#"

    fun moveAbsolute(position: Int): String {
        require(position >= 0) { "Position must be zero or greater" }
        return ":05$position#"
    }

    fun parseCoreResponse(frame: String): GeminiResponse = GeminiProtocol.parseResponse(frame)

    fun readReverse() = ":13#"
    fun setReverse(enabled: Boolean) = ":14${enabled.asFlag()}#"
    fun readMotorSpeed() = ":43#"
    fun setMotorSpeed(speed: Int, geminiConsoleCanonical: Boolean = false): String {
        require(speed in MOTOR_SPEED_RANGE) { "Motor speed must be 0, 1, or 2" }
        return if (geminiConsoleCanonical) ":15$speed#" else ":150$speed#"
    }
    fun readBacklashInSteps() = ":78#"
    fun setBacklashInSteps(steps: Int): String = ":77${checkedBacklash(steps)}#"
    fun readBacklashInEnabled() = ":74#"
    fun setBacklashInEnabled(enabled: Boolean) = ":73${enabled.asFlag()}#"
    fun readBacklashOutSteps() = ":80#"
    fun setBacklashOutSteps(steps: Int): String = ":79${checkedBacklash(steps)}#"
    fun readBacklashOutEnabled() = ":76#"
    fun setBacklashOutEnabled(enabled: Boolean) = ":75${enabled.asFlag()}#"
    fun readTemperatureCompensation() = ":24#"
    fun setTemperatureCompensation(enabled: Boolean) = ":23${enabled.asFlag()}#"
    fun readTemperatureCoefficient() = ":26#"
    fun setTemperatureCoefficient(value: Int): String {
        require(value in TEMPERATURE_COEFFICIENT_RANGE) { "Temperature coefficient must be 0..1000" }
        return ":22$value#"
    }
    fun readTemperatureCompensationAvailable() = ":25#"
    fun readTemperatureCompensationDirection() = ":87#"
    fun setTemperatureCompensationDirection(outward: Boolean) = ":88${outward.asFlag()}#"
    fun readStepMode() = ":29#"
    fun setStepMode(mode: Int): String {
        require(mode in STEP_MODES) { "Unsupported MyFocuserPro2 step mode" }
        return ":30$mode#"
    }
    fun syncPosition(position: Int): String {
        require(position in 0..MAX_ENCODED_POSITION) { "Position must be 0..$MAX_ENCODED_POSITION" }
        return ":31$position#"
    }
    fun setDeviceMaximum(position: Int): String {
        require(position in 1..MAX_ENCODED_POSITION) { "Maximum position must be 1..$MAX_ENCODED_POSITION" }
        return ":07${position.toString().padStart(6, '0')}#"
    }
    fun setCoilPower(enabled: Boolean) = ":12${enabled.asFlag()}#"
    fun readCoilPower() = ":11#"
    fun home() = ":28#"
    fun setDisplayEnabled(enabled: Boolean) = ":36${enabled.asFlag()}#"
    fun readDisplayEnabled() = ":37#"
    fun setTemperatureDisplayCelsius() = ":16#"
    fun setTemperatureDisplayFahrenheit() = ":17#"
    fun readTemperatureDisplayUnit() = ":38#"
    fun readMaximumIncrement() = ":10#"
    fun readTemperatureResolution() = ":21#"
    fun setTemperatureResolution(bits: Int): String {
        require(bits in 9..12) { "Temperature resolution must be 9..12 bits" }
        return ":20$bits#"
    }
    fun readStepSizeEnabled() = ":32#"
    fun setStepSizeEnabled(enabled: Boolean) = ":18${enabled.asFlag()}#"
    fun readStepSize() = ":33#"
    fun setStepSize(value: Double): String {
        require(value.isFinite() && value >= 0.0) { "Step size must be zero or greater" }
        return ":19${formatDecimal(value)}#"
    }
    fun readDisplayPageTime() = ":34#"
    fun setDisplayPageTime(seconds: Int): String {
        require(seconds in 2..10) { "Display page time must be 2..10 seconds" }
        return ":35$seconds#"
    }
    fun readDisplayUpdateOnMove() = ":62#"
    fun setDisplayUpdateOnMove(enabled: Boolean) = ":61${enabled.asFlag()}#"
    fun readDisplayPageOptions() = ":93#"
    fun setDisplayPageOptions(bits: String): String {
        require(bits.isNotEmpty() && bits.length <= 9 && bits.all { it == '0' || it == '1' }) {
            "Display page options must be a 1..9 digit binary string"
        }
        return ":92$bits#"
    }
    fun readHomeSwitchAvailable() = ":50#"
    fun readHomeSwitchState() = ":63#"
    fun moveRelative(steps: Int): String {
        require(steps != 0) { "Relative movement cannot be zero" }
        return ":64$steps#"
    }
    fun readJogEnabled() = ":66#"
    fun setJogEnabled(enabled: Boolean) = ":65${enabled.asFlag()}#"
    fun readJogDirection() = ":68#"
    fun setJogDirection(outward: Boolean) = ":67${outward.asFlag()}#"
    fun readDelayAfterMove() = ":72#"
    fun setDelayAfterMove(milliseconds: Int): String {
        require(milliseconds in 0..255) { "Delay after move must be 0..255 ms" }
        return ":71$milliseconds#"
    }
    fun readTemperatureProbeAvailable() = ":83#"
    fun readStepperPower() = ":89#"
    fun persistSettings() = ":48#"
    fun resetController() = ":40#"
    fun restoreDefaults() = ":42#"

    fun parseFirmwareVersion(frame: String): Int = parseInteger(frame, "F")
    fun parseReverse(frame: String): Boolean = parseBoolean(frame, "R")
    fun parseMotorSpeed(frame: String): Int = parseInteger(frame, "C").also {
        require(it in MOTOR_SPEED_RANGE) { "Unknown MyFocuserPro2 motor speed: $it" }
    }
    fun parseBacklashInSteps(frame: String): Int = parseInteger(frame, "6").also(::checkedBacklash)
    fun parseBacklashInEnabled(frame: String): Boolean = parseBoolean(frame, "4")
    fun parseBacklashOutSteps(frame: String): Int = parseInteger(frame, "7").also(::checkedBacklash)
    fun parseBacklashOutEnabled(frame: String): Boolean = parseBoolean(frame, "5")
    fun parseTemperatureCompensation(frame: String): Boolean = parseBoolean(frame, "1")
    fun parseTemperatureCoefficient(frame: String): Int = parseInteger(frame, "B").also {
        require(it in TEMPERATURE_COEFFICIENT_RANGE) { "Temperature coefficient must be 0..1000" }
    }
    fun parseTemperatureCompensationAvailable(frame: String) = parseBoolean(frame, "A")
    fun parseTemperatureCompensationDirection(frame: String) = parseBoolean(frame, "k")
    fun parseStepMode(frame: String): Int = parseInteger(frame, "S").also {
        require(it in STEP_MODES) { "Unsupported MyFocuserPro2 step mode: $it" }
    }
    fun parseCoilPower(frame: String): Boolean = parseBoolean(frame, "O")
    fun parseDisplayEnabled(frame: String): Boolean = parseBoolean(frame, "D")
    fun parseTemperatureDisplayCelsius(frame: String): Boolean = parseBoolean(frame, "b")
    fun parseMaximumIncrement(frame: String): Int = parseInteger(frame, "Y")
    fun parseTemperatureResolution(frame: String): Int = parseInteger(frame, "Q").also {
        require(it in 9..12) { "Temperature resolution must be 9..12 bits" }
    }
    fun parseStepSizeEnabled(frame: String): Boolean = parseBoolean(frame, "U")
    fun parseStepSize(frame: String): Double = parseDecimal(frame, "T")
    fun parseDisplayPageTimeMilliseconds(frame: String): Int = parseInteger(frame, "X")
    fun parseDisplayUpdateOnMove(frame: String): Boolean = parseBoolean(frame, "L")
    fun parseDisplayPageOptions(frame: String): String = parseText(frame, "l").also {
        require(it.isNotEmpty() && it.all { char -> char == '0' || char == '1' }) { "Malformed display page options" }
    }
    fun parseHomeSwitchAvailable(frame: String): Boolean = parseBoolean(frame, "l")
    fun parseHomeSwitchState(frame: String): Boolean = parseBoolean(frame, "H")
    fun parseJogEnabled(frame: String): Boolean = parseBoolean(frame, "K")
    fun parseJogDirection(frame: String): Boolean = parseBoolean(frame, "V")
    fun parseDelayAfterMove(frame: String): Int = parseInteger(frame, "3").also {
        require(it in 0..255) { "Delay after move must be 0..255 ms" }
    }
    fun parseTemperatureProbeAvailable(frame: String): Boolean = parseBoolean(frame, "c")
    fun parseStepperPower(frame: String): Boolean = parseBoolean(frame, "9")
    fun parseFirmwareName(frame: String): String = parseText(frame, "F")
    fun parseBacklashInStepsForWire(frame: String): Int = parseInteger(frame, "6")
    fun parseBacklashOutStepsForWire(frame: String): Int = parseInteger(frame, "7")

    private fun parseBoolean(frame: String, prefix: String): Boolean {
        val value = parseInteger(frame, prefix)
        require(value == 0 || value == 1) { "Boolean response must be 0 or 1" }
        return value == 1
    }

    private fun parseInteger(frame: String, prefix: String): Int {
        require(frame.endsWith('#')) { "Response frame is incomplete" }
        val body = frame.dropLast(1)
        require(body.startsWith(prefix) && body.length > prefix.length) { "Unexpected MyFocuserPro2 response: $frame" }
        val value = body.drop(prefix.length).toIntOrNull()
            ?: throw IllegalArgumentException("Malformed MyFocuserPro2 response: $frame")
        require(value >= 0) { "MyFocuserPro2 response cannot be negative" }
        return value
    }

    private fun parseDecimal(frame: String, prefix: String): Double {
        val value = parseText(frame, prefix).toDoubleOrNull()
            ?: throw IllegalArgumentException("Malformed MyFocuserPro2 response: $frame")
        require(value.isFinite() && value >= 0.0) { "MyFocuserPro2 response cannot be negative or non-finite" }
        return value
    }

    private fun parseText(frame: String, prefix: String): String {
        require(frame.endsWith('#')) { "Response frame is incomplete" }
        val body = frame.dropLast(1)
        require(body.startsWith(prefix) && body.length > prefix.length) { "Unexpected MyFocuserPro2 response: $frame" }
        return body.drop(prefix.length)
    }

    private fun formatDecimal(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun checkedBacklash(steps: Int): Int {
        require(steps in BACKLASH_RANGE) { "Backlash steps must be 0..255" }
        return steps
    }

    private fun Boolean.asFlag() = if (this) 1 else 0

    val MOTOR_SPEED_RANGE = 0..2
    val BACKLASH_RANGE = 0..255
    val TEMPERATURE_COEFFICIENT_RANGE = 0..1000
    const val MAX_ENCODED_POSITION = 999_999
    val STEP_MODES = setOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
}
