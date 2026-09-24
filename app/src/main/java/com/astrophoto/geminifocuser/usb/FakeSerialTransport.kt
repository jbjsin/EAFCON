package com.astrophoto.geminifocuser.usb

import com.astrophoto.geminifocuser.protocol.GeminiProtocol

/** Deterministic local focuser for exercising the controller and UI without USB hardware. */
class FakeSerialTransport(
    startPosition: Int = 7500,
    private val maximum: Int = 15000,
) : SerialTransport {
    private var opened = false
    private var position = startPosition
    private var target = startPosition
    private var movementPollsRemaining = 0
    private var temperature = 18.0

    init {
        require(startPosition in 0..maximum)
    }

    override suspend fun open() {
        check(!opened)
        opened = true
    }

    override suspend fun send(command: String) {
        check(opened)
        when {
            command == GeminiProtocol.STOP -> {
                target = position
                movementPollsRemaining = 0
            }
            command.startsWith(":05") && command.endsWith('#') -> {
                target = command.substring(3, command.length - 1).toInt()
                require(target in 0..maximum)
                movementPollsRemaining = if (target == position) 0 else 3
            }
            else -> error("Unsupported fake command: $command")
        }
    }

    override suspend fun exchange(command: String, expectedPrefix: String): String {
        check(opened)
        val response = when (command) {
            GeminiProtocol.HANDSHAKE -> "EOK#"
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
