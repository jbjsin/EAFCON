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
            GeminiProtocol.READ_MAX_POSITION -> "M$maximum#"
            GeminiProtocol.READ_TEMPERATURE -> "Z${"%.2f".format(java.util.Locale.US, temperature)}#"
            GeminiProtocol.READ_MOVEMENT -> {
                if (movementPollsRemaining > 0) {
                    movementPollsRemaining--
                    if (movementPollsRemaining == 0) position = target
                    "I1#"
                } else "I0#"
            }
            else -> error("Unsupported fake command: $command")
        }
        check(response.startsWith(expectedPrefix))
        return response
    }

    override suspend fun close() {
        opened = false
    }
}
