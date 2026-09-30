# EAFCON developer guide

## Environment and application identity

Use Android Studio with JDK 17, Android SDK Platform 36, and the Gradle/Android Gradle Plugin versions pinned by the repository. The local SDK path belongs in ignored `local.properties`.

EAFCON 1.1.2 uses:

- Namespace and application ID: `dev.sphc.eafcon`
- `minSdk = 26`
- `compileSdk = 36`
- `targetSdk = 36`
- `versionCode = 7`
- `versionName = 1.1.2`

The package ID was finalized before the first Google Play publication. It differs from early development builds (`com.astrophoto.geminifocuser`), so Android treats 1.1.2 as a separate application rather than an in-place update of those builds. It updates 1.1.1 when both APKs use the same signing key. Kotlin sources and tests use the matching `dev/sphc/eafcon` directory tree.

The project uses Kotlin, Jetpack Compose, AndroidX Lifecycle, Coroutines, and `usb-serial-for-android` 3.11.0. The serial dependency is published through JitPack, supports CH340/CH341 devices, and is MIT licensed; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Build and test

From PowerShell at the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:bundleRelease
```

Unix equivalents use `./gradlew`. The debug APK is normally written to `app/build/outputs/apk/debug/EAFCON_1.1.2.apk`. The release App Bundle is normally written to `app/build/outputs/bundle/release/app-release.aab`.

- A debug APK is signed with the local Android debug key and is intended for development/testing.
- A release APK is an installable release-mode package, but this project does not currently define a production signing configuration.
- A release AAB is the upload format for Google Play. It is not directly installed like an APK; Google Play generates optimized APKs from it.

`versionCode` must increase for every build uploaded to Google Play, even when the user-facing `versionName` is unchanged. Google Play publication is still pending and this repository has no deployment workflow.

## Google Play signing preparation

Use Google Play App Signing for publication. Create and protect the upload key outside the repository. Never commit a keystore, passwords, signing property files, or credentials. If Gradle signing is wired later, read secrets from ignored local Gradle properties or environment variables and make their absence fail clearly only for signed release tasks. Do not create placeholder production credentials.

Before a Play upload:

1. Confirm the application ID is `dev.sphc.eafcon` and cannot be changed after publication.
2. Increase `versionCode`.
3. Run unit tests and assemble the debug APK.
4. Complete the Android hardware regression checklist below.
5. Configure the local upload key securely and build the release AAB.
6. Inspect the bundle and upload it manually to the approved Play track.

## Architecture

The established boundaries remain:

`Android USB discovery/permission → SerialTransport → GeminiProtocol → FocuserController → ViewModel → Compose UI`

`usb/` owns passive discovery, permission, the serial-library adapter, metadata formatting, and fake transport. `protocol/` owns canonical commands and `#`-terminated parsing without Android dependencies. `control/` owns polling, trusted state, serialization, and movement safety. `presets/` owns the portable model, validation, versioned JSON, and SharedPreferences persistence. `ui/` owns ViewModel orchestration and Compose rendering.

## API 36 and lifecycle review

Dynamic USB attach/detach receivers use the Android receiver APIs appropriate to the runtime level. The USB permission receiver is app-scoped, and its `PendingIntent` remains immutable in line with the Android USB Host example. USB permission is requested only after explicit Connect.

Controller polling uses the IO dispatcher while remaining a child of `viewModelScope`. `FocuserViewModel.onCleared()` unregisters the receiver and performs serial shutdown synchronously on the IO dispatcher before returning. This avoids both an orphan cleanup scope and a Main-dispatcher join deadlock. A physical USB-detach/lifecycle regression test is still required because JVM tests cannot exercise Android's USB service or activity/ViewModel teardown.

## Position presets

`PositionPreset` stores an ID, user name, and integer position. `PositionPresetStore` persists one versioned JSON document in private SharedPreferences (`position_presets_v1`). No database framework is required.

Export/import uses the Storage Access Framework through `CreateDocument` and `OpenDocument`. Format version 1 is:

```json
{
  "format": "EAFCon Focuser Presets",
  "version": 1,
  "presets": [
    { "id": "preset-id", "name": "90GT Visual", "position": 5100 }
  ]
}
```

Import is limited to 1 MB and 500 entries. It parses and validates the complete document into a temporary list before offering Merge or Replace. Merge updates matching IDs and appends new IDs; duplicate names with different IDs remain separate. Replace requires a second confirmation. Failed validation does not mutate stored data. USB paths, IDs, and enumeration values are never stored as preset identity. Schema changes require an explicit version/migration path.

Selecting a preset only copies its position to Target Position. Preset CRUD and import/export issue no hardware commands. The existing GO/controller path performs the final safety validation and movement.

## USB discovery and device selection

`UsbSerialDeviceRepository.listDevices()` reads `UsbManager.deviceList` and uses the serial prober only to map descriptors to supported driver types. Scan does not call `openDevice`, open a port, claim/force-claim an interface, reset a device, request permission, or send any command.

The list shows driver, VID:PID, and current Bus/Device location. Details include path, current deviceId, manufacturer, product, serial number, class, subclass, protocol, and interface count when Android exposes them. Descriptor access is guarded because values can be unavailable before permission.

Selection policy is pure and unit-tested:

- No compatible devices: no selection.
- Exactly one device: automatic selection is allowed.
- Multiple devices: retain a previous path/name selection only while it is still present; otherwise require an explicit choice.

The Connect button remains disabled without a selection. Device path/name, deviceId, and Bus/Device values are only per-enumeration hints and never persistent identity. VID/PID never identifies Gemini by itself.

USB topology refreshes cancel an older pending refresh, wait briefly for `UsbManager.deviceList` to settle, and retry one unexpected empty result. A detach broadcast removes only its matching path from the current UI list immediately. Device inspection is isolated per entry so a stale device that disappears during probing cannot discard successfully enumerated neighboring devices. Scan still does not open, claim, reset, probe the focuser protocol, or send commands.

The field test used Gemini and a same-VID/PID SeRelCam relay with EAFCON disconnected; SvBony USB camera software and USB Serial Terminal were also installed. In 1.1.1, relay removal/reconnection changed only the relay entry, while Gemini removal temporarily cleared all scan entries. The 1.1.2 stabilization fixed that removal display path. Further testing showed that inserting Gemini physically disconnects the already attached relay and Android then re-enumerates both devices; the list subsequently recovers without manual Scan. This is a host/hub topology or power-layer bus reset rather than a Scan-only failure. EAFCON cannot preserve another application's open serial handle across it, and it must not auto-reconnect because the devices share VID/PID while their temporary paths may change.

## Serial and controller review

`UsbSerialPortTransport` serializes send/exchange/close with one mutex. It accepts fragmented frames and finds the expected response among coalesced frames. The parser is reset on open, close, and before a new exchange so a partial frame left by a timed-out request cannot contaminate the next command. Android integration testing remains necessary for late physical responses and USB removal during a read.

`FocuserController` keeps movement/STOP requests behind a movement mutex while the transport serializes them against polling. STOP clears the abandoned move target and updates `commandPending` from the latest state after its suspending send, avoiding an older snapshot overwriting a newer polling result. Tests cover connection, handshake failure, movement lifecycle, final position refresh, limit rejection, disconnect/error state, and STOP through the fake transport.

## Verified local build result

On 2026-09-30, `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:bundleRelease` completed successfully with JDK 17 and Android SDK 36. All 31 JVM tests passed. The generated debug APK metadata reports `dev.sphc.eafcon`, version code 7, and version name 1.1.2. The generated release AAB remains intentionally unsigned because production upload signing is not configured.

## Verification model

### VERIFIED — historical physical Gemini results

- Gemini enumerates through CH34x on Android USB Host (`VID 1A86`, `PID 7523`; these identifiers are not unique).
- 9600 baud, 8 data bits, no parity, one stop bit, no flow control.
- `:02#` → `EOK#` handshake.
- `:00#` → `P<number>#` position.
- `:01#` → `I0#`/`I1#` movement state.
- `:06#` → `Z<number>#` temperature.
- `:08#` → `M<number>#` device maximum.
- Canonical `:05<position>#` absolute movement, including `:057510#`, and subsequent position reporting.
- Long movement reporting `I1#` followed by `I0#`.
- Successful operation through EAFCON on the recorded Gemini/Galaxy Fold4 setup.

### SOURCE-VERIFIED / HARDWARE-UNVERIFIED ON GEMINI

- STOP sends `:27#`, matching `MyFocuserPro2::AbortFocuser()` in the INDI driver. Physical STOP behavior on this Gemini EAF still needs a separately recorded test. The app must not be treated as an emergency stop.

### UNVERIFIED

- Other MyFocuserPro2-compatible focuser models.
- RTS/DTR requirements on other hardware.
- Safe automatic Gemini identification.
- Commands that change device maximum; EAFCON does not send one.
- USB detach/reconnect stabilization and remaining multi-device behavior on the exact 1.1.2 Android build.
- Whether a sufficiently powered hub, separate Gemini power applied before USB, or a different hub/cable prevents the whole-bus reset during Gemini insertion.
- Preset behavior on the exact 1.1.2 Android build until manually tested.

Historical verification does not mean the exact 1.1.2 build has completed regression testing.

## 1.1.2 hardware regression checklist

- [ ] Detect the Gemini CH34x and distinguish it from another matching VID/PID device.
- [ ] Grant USB permission and connect at 9600 8N1.
- [ ] Confirm `:02#` handshake.
- [ ] Confirm current position and temperature.
- [ ] Set a conservative software maximum and confirm an out-of-range target is rejected without movement.
- [ ] Move −5/+5 and −25/+25 and confirm reported positions.
- [ ] Perform a small absolute move and confirm final `:00#` position.
- [ ] Perform a longer move and observe `I1#`.
- [ ] Press STOP during the long move; confirm `I1#` → STOP → `I0#` and final `:00#` position.
- [ ] Detach USB while connected and confirm disconnection/error state and command blocking.
- [ ] Confirm preset create/edit/delete, restart persistence, JSON export, Merge, confirmed Replace, and failure rollback.
- [ ] Run Scan while another app owns a USB serial device and confirm the other connection is unaffected.
- [ ] With Gemini and the SeRelCam relay listed but disconnected, remove/reinsert the relay and confirm only that entry changes.
- [ ] Remove/reinsert Gemini while the relay remains attached and confirm the relay never disappears permanently.
- [ ] Repeat with SvBony USB camera software and USB Serial Terminal installed/running as in the reported setup.
- [ ] Repeat Gemini insertion with a powered hub and Gemini external power already stable; note whether USB Serial Terminal keeps its relay handle.
- [ ] Connect both peripherals to the powered hub before attaching the hub upstream to Android and compare the result with hot-plugging Gemini.

## Continuous integration

`.github/workflows/android-ci.yml` uses JDK 17 and Gradle caching, then runs `:app:testDebugUnitTest` and `:app:assembleDebug` on pushes and pull requests. It does not deploy, access signing secrets, or publish artifacts.

## Troubleshooting

- If no device appears, check USB Host support, cable/OTG orientation, and hub power.
- If matching CH34x devices appear, use current Bus/Device and descriptor details and select manually.
- If permission is denied or lost, disconnect and request permission again for the selected device.
- If a response times out or is malformed, record exact serial settings and raw frame boundaries; do not try undocumented commands.
- If Gradle fails before compilation, confirm JDK 17, SDK Platform 36, and ignored `local.properties`.
