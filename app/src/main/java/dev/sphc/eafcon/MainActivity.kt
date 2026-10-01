package dev.sphc.eafcon

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.sphc.eafcon.ui.AppLanguage
import dev.sphc.eafcon.ui.EafconTheme
import dev.sphc.eafcon.ui.FocuserScreen
import dev.sphc.eafcon.ui.LanguagePreferenceStore
import dev.sphc.eafcon.ui.MovementVibrationPreferenceStore
import dev.sphc.eafcon.ui.MovementVibrationSettings
import dev.sphc.eafcon.ui.ThemePreferenceStore
import dev.sphc.eafcon.ui.withAppLanguage

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = LanguagePreferenceStore(newBase).load()
        super.attachBaseContext(newBase.withAppLanguage(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeStore = remember { ThemePreferenceStore(this) }
            val languageStore = remember { LanguagePreferenceStore(this) }
            val vibrationStore = remember { MovementVibrationPreferenceStore(this) }
            var themeMode by rememberSaveable { mutableStateOf(themeStore.load()) }
            var vibrationSettings by remember { mutableStateOf(vibrationStore.load()) }
            val appLanguage = languageStore.load()
            EafconTheme(themeMode) {
                Surface {
                    FocuserScreen(
                        themeMode = themeMode,
                        appLanguage = appLanguage,
                        vibrationSettings = vibrationSettings,
                        onThemeModeChange = { selected ->
                            themeMode = selected
                            themeStore.save(selected)
                        },
                        onAppLanguageChange = { selected: AppLanguage ->
                            if (selected != appLanguage) {
                                languageStore.save(selected)
                                recreate()
                            }
                        },
                        onVibrationSettingsChange = { selected: MovementVibrationSettings ->
                            vibrationSettings = selected
                            vibrationStore.save(selected)
                        },
                    )
                }
            }
        }
    }
}
