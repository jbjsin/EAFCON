package dev.sphc.eafcon.usb

/**
 * Maps one USB enumeration snapshot without allowing a stale or inaccessible device to discard
 * successfully inspected neighbors from the same snapshot.
 */
internal fun <T, R : Any> mapDevicesIndependently(
    devices: Iterable<T>,
    inspect: (T) -> R?,
): List<R> = devices.mapNotNull { device ->
    runCatching { inspect(device) }.getOrNull()
}
