package dev.sphc.eafcon.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class VibrationStrength(val percent: Int) {
    LEVEL_1(10),
    LEVEL_2(30),
    LEVEL_3(50),
    LEVEL_4(70),
    LEVEL_5(90),
    ;

    val amplitude: Int
        get() = (percent * MAX_AMPLITUDE + 50) / 100

    val level: Int
        get() = ordinal + 1

    private companion object {
        const val MAX_AMPLITUDE = 255
    }
}

internal fun vibrationStrengthFromStored(value: String?): VibrationStrength = when (value) {
    "LOW" -> VibrationStrength.LEVEL_2
    "MEDIUM" -> VibrationStrength.LEVEL_3
    "HIGH" -> VibrationStrength.LEVEL_5
    else -> runCatching { VibrationStrength.valueOf(value.orEmpty()) }
        .getOrDefault(VibrationStrength.LEVEL_3)
}

internal fun activeMovementVibrationStrength(
    moving: Boolean,
    settings: MovementVibrationSettings,
): VibrationStrength? = settings.strength.takeIf { moving && settings.enabled }

data class MovementVibrationSettings(
    val enabled: Boolean = false,
    val strength: VibrationStrength = VibrationStrength.LEVEL_3,
)

class MovementVibrationPreferenceStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): MovementVibrationSettings = MovementVibrationSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        strength = vibrationStrengthFromStored(preferences.getString(KEY_STRENGTH, null)),
    )

    fun save(settings: MovementVibrationSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_STRENGTH, settings.strength.name)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "eafcon_preferences"
        const val KEY_ENABLED = "movement_vibration_enabled_v1"
        const val KEY_STRENGTH = "movement_vibration_strength_v1"
    }
}

private class MovementVibrationController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    fun start(strength: VibrationStrength) {
        val activeVibrator = vibrator ?: return
        if (!runCatching { activeVibrator.hasVibrator() }.getOrDefault(false)) return
        runCatching {
            activeVibrator.cancel()
            activeVibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, PULSE_DURATION_MS, PULSE_GAP_MS),
                    intArrayOf(0, strength.amplitude, 0),
                    0,
                ),
            )
        }
    }

    fun preview(strength: VibrationStrength, continueMovingPattern: Boolean) {
        val activeVibrator = vibrator ?: return
        if (!runCatching { activeVibrator.hasVibrator() }.getOrDefault(false)) return
        runCatching {
            activeVibrator.cancel()
            if (continueMovingPattern) {
                activeVibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, PREVIEW_DURATION_MS, PREVIEW_GAP_MS, PULSE_DURATION_MS, PULSE_GAP_MS),
                        intArrayOf(0, strength.amplitude, 0, strength.amplitude, 0),
                        3,
                    ),
                )
            } else {
                activeVibrator.vibrate(
                    VibrationEffect.createOneShot(PREVIEW_DURATION_MS, strength.amplitude),
                )
            }
        }
    }

    fun stop() {
        runCatching { vibrator?.cancel() }
    }

    private companion object {
        const val PULSE_DURATION_MS = 180L
        const val PULSE_GAP_MS = 820L
        const val PREVIEW_DURATION_MS = 120L
        const val PREVIEW_GAP_MS = 80L
    }
}

@Composable
fun rememberMovementVibrationPreview(): (VibrationStrength, Boolean) -> Unit {
    val context = LocalContext.current
    val controller = remember(context) { MovementVibrationController(context.applicationContext) }
    return remember(controller) {
        { strength, continueMovingPattern ->
            controller.preview(strength, continueMovingPattern)
        }
    }
}

@Composable
fun MovementVibrationEffect(
    moving: Boolean,
    settings: MovementVibrationSettings,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { MovementVibrationController(context.applicationContext) }
    val activeStrength = activeMovementVibrationStrength(moving, settings)

    DisposableEffect(lifecycleOwner, controller, activeStrength) {
        fun updateForLifecycle() {
            if (activeStrength != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                controller.start(activeStrength)
            } else {
                controller.stop()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> updateForLifecycle()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY,
                -> controller.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateForLifecycle()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
        }
    }
}
