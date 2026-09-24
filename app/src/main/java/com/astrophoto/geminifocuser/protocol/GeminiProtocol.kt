package com.astrophoto.geminifocuser.protocol

/** Canonical Gemini EAF command encoder and response decoder. */
object GeminiProtocol {
    const val HANDSHAKE = ":02#"
    const val READ_POSITION = ":00#"
    const val READ_MOVEMENT = ":01#"
    const val READ_TEMPERATURE = ":06#"
    const val READ_MAX_POSITION = ":08#"
    const val STOP = ":27#"

    fun moveAbsolute(position: Int): String {
        require(position >= 0) { "Position must be zero or greater" }
        return ":05$position#"
    }

    fun parseResponse(frame: String): GeminiResponse {
        require(frame.endsWith('#')) { "Response frame is incomplete" }
        val body = frame.dropLast(1)
        return when {
            body == "EOK" -> GeminiResponse.Handshake
            body.startsWith('P') -> {
                val position = body.drop(1).toIntOrNull() ?: malformed(frame)
                if (position < 0) malformed(frame)
                GeminiResponse.Position(position)
            }
            body == "I0" -> GeminiResponse.Movement(isMoving = false)
            body == "I1" -> GeminiResponse.Movement(isMoving = true)
            body.startsWith('Z') -> {
                val temperature = body.drop(1).toDoubleOrNull() ?: malformed(frame)
                if (!temperature.isFinite()) malformed(frame)
                GeminiResponse.Temperature(temperature)
            }
            body.startsWith('M') -> {
                val maximum = body.drop(1).toIntOrNull() ?: malformed(frame)
                if (maximum < 0) malformed(frame)
                GeminiResponse.MaximumPosition(maximum)
            }
            else -> throw IllegalArgumentException("Unknown Gemini response: $frame")
        }
    }

    private fun malformed(frame: String): Nothing =
        throw IllegalArgumentException("Malformed Gemini response: $frame")
}

sealed interface GeminiResponse {
    data object Handshake : GeminiResponse
    data class Position(val steps: Int) : GeminiResponse
    data class Movement(val isMoving: Boolean) : GeminiResponse
    data class Temperature(val celsius: Double) : GeminiResponse
    data class MaximumPosition(val steps: Int) : GeminiResponse
}

/** Buffers arbitrary serial chunks and emits each complete '#' delimited frame. */
class DelimitedResponseParser(private val maxFrameLength: Int = 128) {
    private val pending = StringBuilder()

    init {
        require(maxFrameLength > 0)
    }

    fun append(chunk: String): List<String> {
        val frames = mutableListOf<String>()
        for (character in chunk) {
            if (character == '#') {
                if (pending.isNotEmpty()) {
                    frames += pending.append(character).toString()
                    pending.clear()
                }
            } else if (pending.isEmpty() && (character == '\r' || character == '\n')) {
                continue
            } else if (pending.length < maxFrameLength) {
                pending.append(character)
            } else {
                pending.clear()
                throw IllegalArgumentException("Gemini response exceeded $maxFrameLength characters")
            }
        }
        return frames
    }

    fun reset() = pending.clear()
}
