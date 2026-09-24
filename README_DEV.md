# Developer guide

## Environment

Use Android Studio with JDK 17, Android SDK Platform 36, and the Android Gradle Plugin/Gradle versions pinned by the project. The workstation now has Temurin JDK 17, Android Studio, SDK Platform 36, Platform Tools, Build Tools 35.0.0/36.0.0, and the project Gradle 8.13 wrapper. The SDK path is stored in ignored `local.properties` on this workstation; configure your own SDK path there if needed.

The project uses Kotlin, Jetpack Compose BOM 2026.04.01 (Compose 1.11 stable, compatible with the API 36 project target), AndroidX Lifecycle, Coroutines, and `usb-serial-for-android` 3.11.0. The serial dependency is published through JitPack, supports Qinheng CH340/CH341 drivers, and is MIT licensed. Its notice is kept in `THIRD_PARTY_NOTICES.md`. Validate CH34x behavior on Gemini hardware.

## Build and test

From PowerShell at the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Both commands were run successfully on this workstation on 2026-09-24 (`BUILD SUCCESSFUL`; debug APK and JVM unit tests completed). Unit tests do not count as physical hardware validation.

The debug APK is written to `app/build/outputs/apk/debug/EAFCON_1.0.2.apk`. In Android Studio, open the repository root and sync the Gradle project; install SDK Platform 36 and JDK 17 first. The checked-in wrapper uses Gradle 8.13 with Android Gradle Plugin 8.13.2. The launcher uses the generated Gemini focuser cartoon artwork, scaled to about half the square canvas width to leave generous white margins, and the app label is `EAFCON`.

## Architecture

`usb/` owns manual device discovery, Android USB permission, the CH34x library adapter, and a deterministic fake transport. `protocol/` encodes canonical Gemini commands and parses `#`-terminated frames. `control/` owns polling, state transitions, and movement safety. `ui/` contains the Compose screen and its ViewModel. USB library objects do not cross into the protocol package.

## USB hardware test procedure

Use a USB-C OTG adapter or powered hub as needed. Scan devices and manually choose the Gemini EAF; do not identify it by VID/PID alone because another device shares `1A86:7523`. Verify the Android permission flow and close the serial connection by disconnecting in the app before unplugging when practical.

Hardware checklist (all are **NOT YET VERIFIED** by this software implementation):

- [ ] Android detects the CH34x device and displays identifying details.
- [ ] USB permission is granted for the selected device.
- [ ] Serial connection succeeds at 9600 8N1, no flow control.
- [ ] `:02#` returns `EOK#`.
- [ ] `:00#` returns `P<number>#`.
- [ ] `:01#` returns `I0#` or `I1#`.
- [ ] `:06#` returns `Z<number>#`.
- [ ] `:08#` returns `M<number>#`.
- [ ] A small absolute move completes and final reported position matches the target.
- [ ] Movement completion follows `I1#` → `I0#` and triggers a position refresh.
- [ ] STOP is enabled only while moving, sends `:27#`, halts the motor, and leaves the focuser at the reported stop position.
- [ ] Disconnect during movement is reported and further commands stop.

Use conservative targets and keep within the configured software safety limit. STOP uses `:27#` based on the MyFocuserPro2 driver used with Gemini EAF; verify that it halts this physical device and do not treat the app as an emergency stop until that check passes. The source driver sends the command without waiting for a response: https://github.com/indilib/indi/blob/master/drivers/focuser/myfocuserpro2.cpp.

## Protocol and verification notes

The actual verified command list and the rules for distinguishing VERIFIED from ASSUMED/UNVERIFIED behavior are maintained in `agent.md`. All new command work must record the exact TX/RX frames, hardware setup, and physical result before being described as verified. Never invent a command to fill a missing feature.

The parser accepts fragmented and coalesced `#`-terminated messages. Keep protocol tests independent of Android and serial-library classes. If architecture, package ownership, or workflow changes, update both this guide and `agent.md`.

## Troubleshooting

- If no device appears, check OTG support, cable orientation, hub power, and whether Android lists the USB device.
- If a device appears more than once or shares the CH34x identifiers, select it manually and confirm product/manufacturer strings when available.
- If permission is denied or lost, disconnect and request permission again for the selected device.
- If responses time out or are malformed, record the exact serial settings and raw frame boundaries. Do not try alternate undocumented commands.
- If local Gradle builds fail before compilation, confirm JDK 17 and SDK Platform 36 are installed and that the SDK path is configured in `local.properties` (keep that file untracked).
