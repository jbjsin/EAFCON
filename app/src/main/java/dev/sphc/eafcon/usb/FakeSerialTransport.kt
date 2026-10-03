package dev.sphc.eafcon.usb

import dev.sphc.eafcon.protocol.GeminiProtocol
import kotlinx.coroutines.delay

/** Deterministic local focuser for exercising the controller and UI without USB hardware. */
class FakeSerialTransport(
    startPosition: Int = 7500,
    private val maximum: Int = 15000,
    private val handshakeResponse: String = "EOK#",
    private val movementPollsPerMove: Int = 3,
    private val responseDelayMs: Long = 0,
    private val failOnSendCommand: String? = null,
    private val failOnExchangeCommand: String? = null,
) : SerialTransport {
    private var opened = false
    private var position = startPosition
    private var target = startPosition
    private var movementPollsRemaining = 0
    private var temperature = 18.0
    private var reverse = false
    private var speed = 1
    private var backlashInEnabled = false
    private var backlashInSteps = 0
    private var backlashOutEnabled = false
    private var backlashOutSteps = 0
    private var stepMode = 1
    private var coilPowerEnabled = true
    private var displayEnabled = true
    private var deviceMaximum = maximum
    private var temperatureCompensation = false
    private var temperatureCoefficient = 0
    private var temperatureCompensationOutward = false
    private var temperatureUnitCelsius = true
    private var delayAfterMove = 0
    private var jogEnabled = false
    private var jogOutward = false
    private var stepSizeEnabled = false
    private var stepSize = 1.0
    private var temperatureResolution = 12
    private var displayPageTime = 5
    private var displayUpdateOnMove = true
    private var displayPageOptions = "111"
    val sentCommands = mutableListOf<String>()
    val exchangeCommands = mutableListOf<String>()

    init {
        require(startPosition in 0..maximum)
        require(movementPollsPerMove > 0)
        require(responseDelayMs >= 0)
    }

    override suspend fun open() {
        check(!opened)
        opened = true
    }

    override suspend fun send(command: String) {
        check(opened)
        if (command == failOnSendCommand) error("Injected send failure for $command")
        sentCommands += command
        when {
            command == GeminiProtocol.STOP -> {
                target = position
                movementPollsRemaining = 0
            }
            command.startsWith(":05") && command.endsWith('#') -> {
                target = command.substring(3, command.length - 1).toInt()
                require(target in 0..maximum)
                movementPollsRemaining = if (target == position) 0 else movementPollsPerMove
            }
            command.startsWith(":14") -> reverse = command.drop(3).dropLast(1) == "1"
            command.startsWith(":15") -> speed = command.drop(3).dropLast(1).toInt()
            command.startsWith(":77") -> backlashInSteps = command.drop(3).dropLast(1).toInt()
            command.startsWith(":73") -> backlashInEnabled = command.drop(3).dropLast(1) == "1"
            command.startsWith(":79") -> backlashOutSteps = command.drop(3).dropLast(1).toInt()
            command.startsWith(":75") -> backlashOutEnabled = command.drop(3).dropLast(1) == "1"
            command.startsWith(":30") -> stepMode = command.drop(3).dropLast(1).toInt()
            command.startsWith(":31") -> position = command.drop(3).dropLast(1).toInt()
            command.startsWith(":07") -> deviceMaximum = command.drop(3).dropLast(1).toInt()
            command.startsWith(":12") -> coilPowerEnabled = command.drop(3).dropLast(1) == "1"
            command.startsWith(":36") -> displayEnabled = command.drop(3).dropLast(1) == "1"
            command == ":28#" -> { target = 0; position = 0; movementPollsRemaining = 0 }
            command == ":16#" -> temperatureUnitCelsius = true
            command == ":17#" -> temperatureUnitCelsius = false
            command.startsWith(":23") -> temperatureCompensation = command.drop(3).dropLast(1) == "1"
            command.startsWith(":22") -> temperatureCoefficient = command.drop(3).dropLast(1).toInt()
            command.startsWith(":88") -> temperatureCompensationOutward = command.drop(3).dropLast(1) == "1"
            command.startsWith(":71") -> delayAfterMove = command.drop(3).dropLast(1).toInt()
            command.startsWith(":65") -> jogEnabled = command.drop(3).dropLast(1) == "1"
            command.startsWith(":67") -> jogOutward = command.drop(3).dropLast(1) == "1"
            command.startsWith(":18") -> stepSizeEnabled = command.drop(3).dropLast(1) == "1"
            command.startsWith(":19") -> stepSize = command.drop(3).dropLast(1).toDouble()
            command.startsWith(":20") -> temperatureResolution = command.drop(3).dropLast(1).toInt()
            command.startsWith(":35") -> displayPageTime = command.drop(3).dropLast(1).toInt()
            command.startsWith(":61") -> displayUpdateOnMove = command.drop(3).dropLast(1) == "1"
            command.startsWith(":92") -> displayPageOptions = command.drop(3).dropLast(1)
            command in setOf(":40#", ":42#", ":48#") -> Unit
            else -> error("Unsupported fake command: $command")
        }
    }

    override suspend fun exchange(command: String, expectedPrefix: String): String {
        check(opened)
        if (command == failOnExchangeCommand) error("Injected exchange failure for $command")
        if (responseDelayMs > 0) delay(responseDelayMs)
        exchangeCommands += command
        val response = when (command) {
            GeminiProtocol.HANDSHAKE -> handshakeResponse
            GeminiProtocol.READ_POSITION -> "P$position#"
            GeminiProtocol.READ_MAX_POSITION -> "M$deviceMaximum#"
            GeminiProtocol.READ_TEMPERATURE -> "Z${"%.2f".format(java.util.Locale.US, temperature)}#"
            GeminiProtocol.READ_MOVEMENT -> {
                if (movementPollsRemaining > 0) {
                    movementPollsRemaining--
                    if (movementPollsRemaining == 0) position = target
                    "I1#"
                } else "I0#"
            }
            ":13#" -> "R${if (reverse) 1 else 0}#"
            ":43#" -> "C$speed#"
            ":78#" -> "6$backlashInSteps#"
            ":74#" -> "4${if (backlashInEnabled) 1 else 0}#"
            ":80#" -> "7$backlashOutSteps#"
            ":76#" -> "5${if (backlashOutEnabled) 1 else 0}#"
            ":29#" -> "S$stepMode#"
            ":11#" -> "O${if (coilPowerEnabled) 1 else 0}#"
            ":37#" -> "D${if (displayEnabled) 1 else 0}#"
            ":24#" -> "1${if (temperatureCompensation) 1 else 0}#"
            ":25#" -> "A1#"
            ":26#" -> "B$temperatureCoefficient#"
            ":87#" -> "k${if (temperatureCompensationOutward) 1 else 0}#"
            ":38#" -> "b${if (temperatureUnitCelsius) 1 else 0}#"
            ":21#" -> "Q$temperatureResolution#"
            ":32#" -> "U${if (stepSizeEnabled) 1 else 0}#"
            ":33#" -> "T$stepSize#"
            ":34#" -> "X${displayPageTime * 1000}#"
            ":62#" -> "L${if (displayUpdateOnMove) 1 else 0}#"
            ":93#" -> "l$displayPageOptions#"
            ":50#" -> "l1#"
            ":63#" -> "H0#"
            ":66#" -> "K${if (jogEnabled) 1 else 0}#"
            ":68#" -> "V${if (jogOutward) 1 else 0}#"
            ":72#" -> "3$delayAfterMove#"
            ":03#" -> "F338#"
            ":04#" -> "FmyFP2 Fake\r\n338#"
            ":10#" -> "Y$deviceMaximum#"
            ":83#" -> "c1#"
            ":89#" -> "91#"
            else -> error("Unsupported fake command: $command")
        }
        check(response.startsWith(expectedPrefix))
        return response
    }

    override suspend fun close() {
        opened = false
    }
}
