package dev.sphc.eafcon.control

/** Pure movement calculations; every returned target is inside the user safety limit. */
object MovementLimits {
    fun validateAbsolute(target: Int, configuredMaximum: Int): Int {
        require(configuredMaximum >= 0) { "Configure a valid software maximum first" }
        require(target in 0..configuredMaximum) { "Target must be between 0 and $configuredMaximum" }
        return target
    }

    fun relativeTarget(current: Int?, delta: Int, configuredMaximum: Int): Int {
        require(current != null) { "Current position is unknown" }
        val target = current.toLong() + delta.toLong()
        require(target in 0L..configuredMaximum.toLong()) {
            "Relative move would exceed the 0..$configuredMaximum software limit"
        }
        return target.toInt()
    }
}
