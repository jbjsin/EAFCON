package dev.sphc.eafcon.control

/** Persistence boundary for coordinate invalidation caused by controller-level changes. */
interface PositionSyncRequirementStore {
    fun isRequired(): Boolean
    fun setRequired(required: Boolean)
}

class InMemoryPositionSyncRequirementStore(initiallyRequired: Boolean = false) : PositionSyncRequirementStore {
    private var required = initiallyRequired
    override fun isRequired(): Boolean = required
    override fun setRequired(required: Boolean) { this.required = required }
}
