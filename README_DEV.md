# EAFCON developer guide

## Environment and application identity

Use Android Studio with JDK 17, Android SDK Platform 36, and the Gradle/Android Gradle Plugin versions pinned by the repository. The local SDK path belongs in ignored `local.properties`.

EAFCON 1.2.2.1 uses:

- Shared namespace: `dev.sphc.eafcon`
- Production application ID / label: `dev.sphc.eafcon` / `EAFCON`
- Development application ID / label: `dev.sphc.eafcon.dev` / `EAFCON Dev`
- `minSdk = 26`
- `compileSdk = 36`
- `targetSdk = 36`
- `versionCode = 15`
- `versionName = 1.2.2.1`

The `distribution` flavor dimension produces only `devDebug` and `prodRelease`; unused `devRelease` and `prodDebug` variants are disabled. Both variants share the same Kotlin source tree, versionName, versionCode, and release commit. Do not maintain separate source branches merely to change package identity, label, signing, or artifact type.

The project uses Kotlin, Jetpack Compose, AndroidX Lifecycle, Coroutines, and `usb-serial-for-android` 3.11.0. The serial dependency is published through JitPack, supports CH340/CH341 devices, and is MIT licensed; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Build and test

From PowerShell at the repository root:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:lintDevDebug
.\gradlew.bat :app:bundleProdRelease
.\gradlew.bat :app:lintVitalProdRelease
.\gradlew.bat :app:archiveProdReleaseBundle
```

Unix equivalents use `./gradlew`. Key outputs are:

- GitHub Development APK: `app/build/outputs/apk/dev/debug/EAFCON_Dev_1.2.2.1.apk`
- Raw Production bundle: `app/build/outputs/bundle/prodRelease/app-prod-release.aab`
- Locally archived Production bundle: `app/build/outputs/distribution/EAFCON_1.2.2.1_Play.aab`

- `devDebug` is signed with the local Android debug key and is intended for GitHub development/field testing.
- `prodRelease` is the Google Play source artifact. Gradle produces it without repository-bound credentials; the canonical release process post-signs the archived AAB with the dedicated external upload key and verifies that signature before the file is described as Play-upload-ready.
- An AAB is not directly installed like an APK; Google Play generates optimized APKs from it.

`versionCode` must increase for every build uploaded to Google Play, even when the user-facing `versionName` is unchanged. Google Play publication is still pending and this repository has no deployment workflow.

## Permanent distribution policy

Google Play is the official Production channel. GitHub Releases distribute only the `EAFCON Dev` APK. A normal release uses one finalized source commit for both artifacts: validate, set the shared version, commit, push when requested, build `devDebug`, build `prodRelease`, publish only the Dev APK to GitHub, and archive the Production AAB locally. Do not attach the Production AAB to GitHub or upload to Google Play unless the user explicitly requests that external action.

The GitHub release note must state: "This GitHub APK is the EAFCON development/field-test build. The official production distribution channel is Google Play."

Archive the Production AAB with a local manifest containing versionName, versionCode, commit SHA, filename, SHA-256, build date, and signing status. No binary archive or signing secret belongs in Git. Promotion between Google Play tracks should reuse the same uploaded artifact rather than rebuilding it.

Pre-Play releases through 1.1.5 used `dev.sphc.eafcon` with a development/debug key. Do not convert that key into the Production identity. The first Play migration may require exporting presets, uninstalling the pre-Play app, installing the Play app, and importing presets. Future `dev.sphc.eafcon.dev` installations can coexist with Production, but the packages have separate files, preferences, backups, and USB permissions. Disconnect or close one app before the other opens the same USB focuser; no cross-app USB arbitration is implemented.

## Google Play signing preparation

Use Google Play App Signing for publication. Protect the upload key outside the repository. Never commit a keystore, passwords, signing property files, or credentials. The current local release workflow decrypts the separately stored password only in memory, passes it to signing tools through a temporary environment variable, post-signs the archived AAB, and clears the variable immediately. Do not print the password or inject it into command arguments, logs, manifests, or Git.

Before a Play upload:

1. Confirm the application ID is `dev.sphc.eafcon` and cannot be changed after publication.
2. Increase `versionCode`.
3. Run unit tests and assemble the debug APK.
4. Complete the Android hardware regression checklist below.
5. Configure the local upload key securely and build the release AAB.
6. Inspect the bundle and upload it manually to the approved Play track.

## Architecture

The 1.2.x runtime boundaries are:

`Android USB discovery/permission → driver-specific transport → MyFocuserPro2Driver → FocuserController → ViewModel → Compose UI`

`driver/FocuserDriver` owns the transport-neutral core contract. It has no USB, serial, baud/framing, ASCII, or delimiter API. `MyFocuserPro2Driver` is the serial-specific adapter; it owns `SerialTransport` usage and calls the pure Kotlin `MyFocuserPro2Protocol` command/response layer. `CapabilitySettingsDriver` is optional so unrelated future drivers do not need dummy functions. `control/` owns polling, trusted state, serialization, software movement limits, and category/idle/capability enforcement. `usb/` owns passive discovery, permission, the serial-library adapter, metadata formatting, and fake transport. `ui/` owns ViewModel orchestration, navigation, themes, and Compose rendering. Presets and settings retain their existing responsibilities.

The formal command evidence and unresolved frames are in [docs/protocol/MYFOCUSERPRO2_PROTOCOL.md](docs/protocol/MYFOCUSERPRO2_PROTOCOL.md); the architecture/capability model and 1.2.x categories are in [docs/architecture/FOCUSER_DRIVER_ARCHITECTURE.md](docs/architecture/FOCUSER_DRIVER_ARCHITECTURE.md). Keep these classifications synchronized with this guide and `agent.md`.

### Advanced, Administrative, and Device Administration boundaries

The Advanced page exposes Reverse, Motor Speed, and Backlash IN/OUT. Writes require a connected, idle focuser, use bounded typed inputs, and are read back before UI state is treated as confirmed. Backlash is conservatively capped at 255 because the project user guide and INDI driver differ (0–255 versus 0–512). These commands are source-verified/hardware-unverified on Gemini.

The Administrative page exposes source-backed Step Mode, Sync Position, Device Maximum, Coil Power, Home, display controls, controller C/F mode, and selected firmware settings. Step Mode changes coordinate scale and can invalidate the position; EAFCON persists a position-sync-required safety flag before sending the command, blocks movement and safety-limit changes, requires explicit logical-position sync, and requires a new software maximum before movement. Device Maximum write is enabled only in `devDebug`; the dedicated card validates the current position and active software limit before confirmation and controller readback. The UI requires confirmation, controller readback where available, idle state, and software-limit/current-position validation. Home may move the focuser and has no reliable completion acknowledgment. Administrative `°C`/`°F` uses `:16#`/`:17#`, reads back `:38#`, and is the sole temperature-unit control; it synchronizes the app's main temperature presentation and compensation-coefficient unit. Temperature compensation is available only in Dev with an autonomous-motion safety warning.

The Device Administration page is capability-gated and requires explicit confirmation. Manufacturer console 2.1.0.0 and official firmware 338 establish EEPROM persistence `:48#`, reset `:40#`, and restore defaults `:42#`. Gemini physical testing reported that `:40#` and `:42#` both perform factory-reset behavior; exact raw response capture is unavailable. EAFCON therefore keeps `:40#` in the protocol catalog but hides the duplicate action, exposing only EEPROM save and factory reset in `devDebug`. `prodRelease` locks these high-risk writes. Firmware flashing remains outside scope.

The Compose UI has separate Connection, Control, and Settings pages. Connection and Control are primary pages always available from a persistent top switcher; all pages remain available from the top-right menu. Secondary menu pages retain the primary page that was active when entered. Android Back on a secondary page returns to that Connection/Control page rather than finishing the Activity, providing the same behavior for future secondary menu destinations. A compact connection indicator remains outside page scrolling and also shows position, temperature, and movement state so short screens retain essential telemetry while the movement controls are visible. Theme selection cycles among Light, low-glare Dark, and red-only Night Vision and is stored privately. The Night Vision color scheme explicitly defines every Material surface-container role as black or dark burgundy so cards and disabled controls never fall back to gray. Position presets appear only in a dialog: Load fills Target Position; Save has explicit New/Create and Edit/Update states.

The Settings page also provides persistent English/Korean selection. English remains the default for existing and fresh installations until the user explicitly selects Korean. `LanguagePreferenceStore` keeps only the language enum; `MainActivity.attachBaseContext()` applies its locale to a copied Android `Configuration`, and selecting another language recreates the Activity so resource-backed Compose text changes immediately. User data, USB metadata, preset names, and unknown technical errors are preserved verbatim. Common operation messages are localized at the UI boundary, while protocol and controller layers remain language-independent. App Bundle language splitting is disabled so both languages remain installed and the in-app switch never depends on a later Play language-pack download.

Settings are grouped into separate Interface, Movement vibration, Serial connection, and Compatibility cards. Movement vibration is disabled by default at level 3 and stored independently from serial profiles. While the Activity is resumed and the controller reports `MOVING`, `MovementVibrationEffect` requests a 180 ms pulse followed by an 820 ms gap. Levels 1–5 use 10/30/50/70/90% of Android's 255 maximum amplitude, which rounds to 26/77/128/179/230. Selecting any level requests a 120 ms preview pulse even when movement vibration is disabled; if movement vibration is active during a move, the repeating pattern continues at the selected level. Stored legacy Low/Medium/High values migrate to levels 2/3/5. Devices without amplitude control may not produce visibly different levels. The effect cancels vibration on `IDLE`, disconnect/unknown movement, disable, Activity pause/stop/destroy, or disposal. `android.permission.VIBRATE` is a normal permission and requires no runtime permission dialog.

## Serial connection profiles

The app loads `app/src/main/assets/connection_profiles.json` through `ConnectionProfileStore`. User changes are validated, encoded with the same schema, and stored in private SharedPreferences as `connection_profiles_v1`. Invalid stored JSON falls back to the bundled asset. Settings cannot be changed while connected and are passed to `UsbSerialPortTransport` on the next explicit Connect.

Focuser type is a separate persisted choice, not part of serial framing. `Gemini Focuser Pro` is the compatibility-preserving default; `MyFocuserPro2 Generic` follows the official firmware family. Selection is disabled while connected and applies to the next explicit Connect. Never infer this choice from VID/PID or temporary USB paths.

Format version 1 is:

```json
{
  "format": "EAFCon Connection Profiles",
  "version": 1,
  "defaultProfileId": "gemini-myfocuserpro2",
  "profiles": [
    {
      "id": "gemini-myfocuserpro2",
      "name": "Gemini / MyFocuserPro2",
      "serial": {
        "baudRate": 9600,
        "dataBits": 8,
        "stopBits": "ONE",
        "parity": "NONE",
        "flowControl": "NONE",
        "readTimeoutMs": 250,
        "writeTimeoutMs": 1000,
        "responseTimeoutMs": 3000
      }
    }
  ]
}
```

The schema already supports multiple profiles, but profile selection/import is intentionally not exposed in this UI revision. Only serial transport parameters are configurable. Protocol commands and response parsing remain MyFocuserPro2-specific, so changing baud rate or framing does not establish compatibility with a different focuser protocol.

## API 36 and lifecycle review

Dynamic USB attach/detach receivers use the Android receiver APIs appropriate to the runtime level. The USB permission receiver is app-scoped, and its `PendingIntent` remains immutable in line with the Android USB Host example. USB permission is requested only after explicit Connect.

Controller polling uses the IO dispatcher while remaining a child of `viewModelScope`. `FocuserViewModel.onCleared()` unregisters the receiver and performs serial shutdown synchronously on the IO dispatcher before returning. This avoids both an orphan cleanup scope and a Main-dispatcher join deadlock. A physical USB-detach/lifecycle regression test is still required because JVM tests cannot exercise Android's USB service or activity/ViewModel teardown.

## Position presets

`PositionPreset` stores an ID, user name, and integer position. On first app launch, `PositionPresetStore` atomically creates `filesDir/Preset.json` with the stable-ID preset `Default System` at position `7500`. Android does not execute application code at package-install time, so initialization happens when EAFCON first starts. Updates from versions that used private SharedPreferences migrate the existing `position_presets_v1` document into `Preset.json` before removing the legacy entry. No database framework is required.

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

Import is limited to 1 MB and 500 entries. The file picker selection is parsed and validated completely before it is applied. Import always merges into app-private `Preset.json`: matching IDs are updated, new IDs are appended, and duplicate names with different IDs remain separate. The merged document is written atomically; failed validation or a write failure does not replace the stored file. USB paths, IDs, and enumeration values are never stored as preset identity. Schema changes require an explicit version/migration path.

`android:allowBackup="true"` remains enabled and no preset-specific exclusion rule is configured. Android Auto Backup therefore includes both `filesDir/Preset.json` and SharedPreferences by default. Uninstalling and reinstalling the same application ID can restore previous presets before first launch; a restored legacy `position_presets_v1` value is then migrated into `Preset.json`. Production and Dev backup sets are independent. For a clean-install test, clear the relevant app storage after installation and before launch, or run `adb shell pm clear dev.sphc.eafcon` for Production and `adb shell pm clear dev.sphc.eafcon.dev` for Dev. Whether presets should remain backed up or receive an explicit reset/exclusion policy is a future product decision.

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

`UsbSerialPortTransport` serializes send/exchange/close with one mutex. It accepts validated serial parameters from the active JSON profile, accepts fragmented frames, and finds the expected response among coalesced frames. The parser is reset on open, close, and before a new exchange so a partial frame left by a timed-out request cannot contaminate the next command. Android integration testing remains necessary for alternate serial settings, late physical responses, and USB removal during a read.

`FocuserController` keeps movement/STOP requests behind a movement mutex while the transport serializes them against polling. STOP clears the abandoned move target and updates `commandPending` from the latest state after its suspending send, avoiding an older snapshot overwriting a newer polling result. Tests cover connection, handshake failure, movement lifecycle, final position refresh, limit rejection, disconnect/error state, and STOP through the fake transport.

## Verified local build result

On 2026-10-01, after the language, card-grouped Settings, five-level movement vibration, and secondary-page Back navigation changes, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:bundleRelease`, and `:app:lintDebug` completed successfully with JDK 17 and Android SDK 36; release lint-vital also passed. All 44 JVM tests passed, including four primary/secondary navigation cases and five vibration-level/migration/effect-key cases. Release build metadata is `dev.sphc.eafcon`, version code 9, and version name 1.1.4. Production upload signing remains intentionally unconfigured. Preview/continuous vibration timing, hardware amplitude differences, lifecycle cancellation, and physical Android Back-key behavior still require on-device regression testing.

On 2026-10-02, after moving preset persistence to app-private `Preset.json`, adding the first-run `Default System`/`7500` entry, migrating legacy SharedPreferences, and defining Import as validated atomic merge, 46 JVM tests passed. The 1.1.5 debug APK, release AAB, debug lint, and release lint-vital completed successfully with version code 10. Production upload signing remained intentionally unconfigured. The dual-channel 1.1.6 build results are recorded after its variant validation completes.

On 2026-10-02, the unreleased 1.1.6 dual-channel structure generated only the intended `devDebug` and `prodRelease` application variants. All 46 JVM tests passed; the Dev APK, Production AAB, Dev lint, and Production lint-vital completed successfully. Artifact inspection confirmed `dev.sphc.eafcon.dev` / `EAFCON Dev` for the debug-signed Dev APK and `dev.sphc.eafcon` / `EAFCON` for the unsigned Production AAB. The AAB is a structural validation artifact and is not Play-upload-ready. These were validation builds from an uncommitted working tree, not canonical release artifacts.

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
- USB detach/reconnect stabilization and remaining multi-device behavior on the exact 1.1.6 Android builds.
- Whether a sufficiently powered hub, separate Gemini power applied before USB, or a different hub/cable prevents the whole-bus reset during Gemini insertion.
- Preset behavior on the exact 1.1.6 Android builds until manually tested.

Historical verification does not mean the exact 1.1.6 builds have completed regression testing.

## 1.1.6 hardware regression checklist

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
- [ ] Confirm first-launch `Preset.json` creation (`Default System`, `7500`), legacy migration, preset create/edit/delete, restart persistence, JSON export, import merge, and failure rollback.
- [ ] Run Scan while another app owns a USB serial device and confirm the other connection is unaffected.
- [ ] With Gemini and the SeRelCam relay listed but disconnected, remove/reinsert the relay and confirm only that entry changes.
- [ ] Remove/reinsert Gemini while the relay remains attached and confirm the relay never disappears permanently.
- [ ] Repeat with SvBony USB camera software and USB Serial Terminal installed/running as in the reported setup.
- [ ] Repeat Gemini insertion with a powered hub and Gemini external power already stable; note whether USB Serial Terminal keeps its relay handle.
- [ ] Connect both peripherals to the powered hub before attaching the hub upstream to Android and compare the result with hot-plugging Gemini.
- [ ] Verify Connection/Control top switching and the compact position/temperature/movement indicator on a short display.
- [ ] Enter Settings separately from Connection and Control, press Android Back, and verify each returns to the primary page used immediately before entry without closing EAFCON.
- [ ] Verify Light, Dark, and Night Vision themes; confirm Night Vision has no unintended gray Material surfaces.
- [ ] Switch English → 한국어 → English in Settings and verify navigation, status, controls, dialogs, preset flows, and common operation messages update and persist after restarting the app.
- [ ] Select vibration levels 1–5 and verify each gives a short preview pulse; compare the requested 10/30/50/70/90% levels on the test phone.
- [ ] Enable movement vibration, test all five levels during Demo and a short Gemini move, and confirm vibration stops on movement completion, STOP, disconnect, and app backgrounding.
- [ ] Restart the app and verify vibration enabled state and selected level persist; confirm the default for a fresh install is Off/Level 3 and legacy Low/Medium/High preferences migrate to levels 2/3/5.
- [ ] Change serial settings while disconnected, reconnect, then restore the bundled 9600 8N1 defaults.

## EAFCON 1.2.0 Gemini manual regression checklist

Run the existing 1.1.x checklist as applicable, then verify each newly exposed setting independently. Keep the focuser clear of mechanical end stops, choose conservative settings, and do not treat the app's STOP control as an emergency stop. Record firmware version only if obtained through an explicitly selected, user-triggered diagnostic; EAFCON does not automatically send the INDI `:03#` firmware query.

- [ ] Open Advanced only after explicit connect; confirm only capabilities marked available for the selected driver are shown.
- [ ] Refresh/read Reverse (`:13#` → `R0#`/`R1#`); toggle once and confirm `:14n#` then readback matches. Check physical direction only using an obviously safe small move afterward.
- [ ] Read Motor Speed (`:43#` → `C0#`/`C1#`/`C2#`); change one level at a time with the focuser idle and confirm `:150n#` plus readback.
- [ ] Read Backlash IN (`:74#`, `:78#`); with a conservative 0–255 value, change only the step count and confirm `:77n#` followed by readback.
- [ ] Change Backlash IN enable separately; confirm `:73n#` and readback. Test compensation behavior only with a small safe reversing move.
- [ ] Repeat separately for Backlash OUT (`:76#`, `:80#`, writes `:75n#`, `:79n#`).
- [ ] Verify Advanced edits are blocked while MOVING and no write frame is emitted; verify the value is only shown as confirmed when readback equals the requested value.
- [ ] Confirm a configured software maximum still blocks explicit movement beyond the limit when Reverse/Backlash are configured. Stop and reassess if any unexpected physical behavior occurs.
- [ ] Do not test Step Mode, Sync Position, Set Device Maximum, Home, Coil Power, temperature compensation, persistence, reset, or restore-defaults from 1.2.0 UI; these are withheld or reserved for later milestones.

All above newly introduced Advanced commands remain `SOURCE-VERIFIED / HARDWARE-UNVERIFIED` until the user records actual Gemini device, firmware if available, serial profile, exact TX/RX, and observed motion/settings after each individual operation. JVM tests and successful APK build do not change the verification state.

## EAFCON 1.2.2 Gemini Administrative regression checklist

First complete the applicable Connection/Control regression and the 1.2.0 Advanced checklist. Keep the focuser away from mechanical end stops. Test one setting at a time; record raw TX/RX and physical behavior. These operations are source-verified but not hardware-verified on Gemini. Do not treat STOP as an emergency stop.

- [ ] Open Administration only after explicit connection; confirm actions are disabled while disconnected or MOVING.
- [ ] Read Step Mode (`:29#` → `S<1|2|4|8|16|32|64|128|256>#`). Do not change it unless prepared to establish the correct logical coordinate again. If changed, confirm `:30<mode>#` then read back `:29#`.
- [ ] After a Step Mode change, confirm Current Position becomes unknown, the safety maximum is cleared, movement and limit-setting are blocked, and the lock survives process termination and reconnect.
- [ ] With the physical focuser position independently known, explicitly Sync Position (`:31<position>#`) and confirm EAFCON's `:00#` readback matches. Confirm movement remains blocked until a new software maximum is set.
- [ ] Confirm position-sync-required state survives app restart and reconnect until successful explicit Sync Position; confirm syncing one value clears the lock only after the device position readback matches.
- [ ] Test Device Maximum only from `devDebug`; Production intentionally keeps the command locked. Keep a large mechanical travel margin and a known current position, use a value above both current position and software limit, confirm the explicit warning, then verify the app reads the same value back with `:08#`. Stop if the device rejects the frame, moves unexpectedly, or reports a different maximum. Do not test this through Production.
- [x] Read Coil Power (`:11#` → `O0#`/`O1#`): the physical Gemini reported `O0#` after connection. Firmware 338 defines `0` as energizing coils during movement and releasing them while idle; `1` keeps coils energized while idle for holding torque. The reported value is verified, but the physical torque/heat/power effects remain unverified. Test any change only with the focuser mechanically secured and a clear load path, then confirm `:12<0|1>#` with `:11#` readback.
- [ ] Read display state (`:37#` → `D0#`/`D1#`), change once (`:36<0|1>#`), and confirm by reading `:37#`.
- [ ] Change Settings → Temperature Display Unit between Celsius and Fahrenheit; confirm the displayed value converts from the same sensor reading and the selection persists after app restart. This is a local EAFCON display preference. The controller-side Celsius command `:16#` is a separate source-backed operation with no readback and is not invoked by these buttons.
- [ ] Use Home (`:28#`) only when a correctly configured home switch is physically present and the travel path is clear. The protocol has no reliable acknowledgment; do not infer that the focuser reached home from command transmission. Record switch presence and observe position/movement independently.
- [ ] Verify Home, coil, and other Administrative actions are rejected before transmission while the controller reports MOVING.
- [ ] In Production, confirm EEPROM persist, reset, and restore defaults remain unavailable. In Development, do not execute them until the separate 1.2.2.1 characterization checklist and recovery preparation are complete.

Until individually tested and recorded, all newly implemented Advanced, Administrative, and Device Administration commands remain `SOURCE-VERIFIED / HARDWARE-UNVERIFIED`. EEPROM persistence, reset, and restore-default frames are source-backed but high-risk and Dev-only; test them only with the checklist below.

## EAFCON 1.2.2.1 Gemini characterization checklist

- [ ] Select `Gemini Focuser Pro`, connect, and confirm core position/movement/temperature/maximum behavior is unchanged.
- [ ] Disconnect, select `MyFocuserPro2 Generic`, reconnect only to known MyFocuserPro2-compatible hardware, and record firmware name/version.
- [ ] Verify Gemini speed 0/1/2 independently and record `:150#`, `:151#`, `:152#` behavior and `:43#` readback.
- [ ] With temperature compensation disabled and safe mechanical clearance, read enable/coefficient/direction; change coefficient and direction separately; enable compensation last and monitor motion closely.
- [ ] Test controller `°C` and `°F` buttons separately; record `:16#`/`:17#` and `:38#` readback. Confirm the main phone presentation follows the confirmed controller setting.
- [ ] Test delay-after-move with a conservative value within 0..255 ms and record `:72#` readback.
- [ ] Before EEPROM save, record all readable settings. Send `:48#` once, power-cycle, and compare settings.
- [x] User-tested controller reset `:40#` and restore defaults `:42#` on Gemini; both produced factory-reset behavior. No exact raw RX capture was retained, so the duplicate `:40#` action is hidden and only `:42#` remains in the UI as Factory reset.
- [ ] Never combine first-time tests. Record selected profile, firmware, exact TX/RX, prior value, resulting value, and physical effect for each command.

## Continuous integration

`.github/workflows/android-ci.yml` uses JDK 17 and Gradle caching, then runs `:app:testDebugUnitTest` and `:app:assembleDebug` on pushes and pull requests. It does not deploy, access signing secrets, or publish artifacts.

## Troubleshooting

- If no device appears, check USB Host support, cable/OTG orientation, and hub power.
- If matching CH34x devices appear, use current Bus/Device and descriptor details and select manually.
- If permission is denied or lost, disconnect and request permission again for the selected device.
- If a response times out or is malformed, record exact serial settings and raw frame boundaries; do not try undocumented commands.
- If Gradle fails before compilation, confirm JDK 17, SDK Platform 36, and ignored `local.properties`.

## EAFCON 1.2.0 validation record (2026-10-03)

- `:app:testDevDebugUnitTest`: 54 tests, 0 failures, 0 errors.
- `:app:assembleDevDebug`, `:app:lintDevDebug`, `:app:bundleProdRelease`, `:app:lintVitalProdRelease`, and `:app:archiveProdReleaseBundle`: BUILD SUCCESSFUL.
- Test APK: `app/build/outputs/apk/dev/debug/EAFCON_Dev_1.2.0.apk`; package `dev.sphc.eafcon.dev`, versionCode 12, versionName 1.2.0; Android Debug certificate verified. Shared copy: `D:\Sync_Data\BJ_Private\0000_활동(취미 등)\Astrophoto\Dev\EAFCON\EAFCON_Dev_1.2.0.apk`; SHA-256 `E6CFAE738277362D728A1B3A17C5FF6866CE6C495D1EF996015DDA82D7D8BBD5` (source and copy match).
- Production AAB build/archive and lint tasks succeeded. The generated AAB is unsigned in this environment (`jarsigner` reports unsigned); it is not a Play-upload artifact until configured signing is applied and verified.
- Newly added Advanced controls and all newly catalogued protocol functions remain hardware-unverified on Gemini. Follow the 1.2.0 checklist above; automated tests do not change this status.
- No commit, push, or release was made.

## EAFCON 1.2.2 validation record (2026-10-03)

- `:app:testDevDebugUnitTest`: 61 tests, 0 failures, 0 errors. `:app:assembleDevDebug`, `:app:lintDevDebug`, `:app:bundleProdRelease`, `:app:lintVitalProdRelease`, and `:app:archiveProdReleaseBundle` completed successfully. Dev lint reports 31 warnings (dependency/SDK/resource and other lint notices); no lint error failed the task.
- Test APK: `app/build/outputs/apk/dev/debug/EAFCON_Dev_1.2.2.apk`; package `dev.sphc.eafcon.dev`, versionCode 14, versionName 1.2.2. APK signature verification succeeded with the Android Debug certificate (SHA-256 `C40A8A73916C2E6120018616AC247BED742698C246A2C89E7E50258EB9FDFFD1`). APK SHA-256: `01205A0C1F6CFB09E7D68649D518E999BDD0FF7EEE1DCB25ED3DF8BCA613CB3E`.
- BuildConfig verification confirmed the development APK enables `ENABLE_UNVERIFIED_DEVICE_MAX_WRITE`, while Production sets it to `false` and keeps the command locked.
- Production bundle: `app/build/outputs/distribution/EAFCON_1.2.2_Play.aab`, SHA-256 `ED9F05EA6B756B31A901F22EB67FCED44855D704949D8A6966759370C6E2B7FD`. The AAB is unsigned because production upload signing is not configured; it is not Play-upload-ready.
- The Dev APK was copied to the shared test folder at `D:\Sync_Data\BJ_Private\0000_활동(취미 등)\Astrophoto\Dev\EAFCON\EAFCON_Dev_1.2.2.apk`; the copied file hash matches the source.
- This is an uncommitted development/field-test build. No commit, push, GitHub release, or Play upload was made. Newly added Administrative behavior remains source-verified/hardware-unverified; EEPROM persistence/reset/defaults remain UNKNOWN and unavailable. Set Device Maximum is available only in `devDebug` pending Gemini physical verification and is locked in Production.

## EAFCON 1.2.2.1 validation record (2026-10-03)

- 67 Dev JVM tests passed with zero failures/errors/skips. `assembleDevDebug`, `lintDevDebug`, `bundleProdRelease`, `lintVitalAnalyzeProdRelease`, and `archiveProdReleaseBundle` succeeded.
- Development APK: `app/build/outputs/apk/dev/debug/EAFCON_Dev_1.2.2.1.apk`; package `dev.sphc.eafcon.dev`, versionCode 15, versionName 1.2.2.1. APK Signature Scheme v2 verification succeeded with the Android Debug certificate SHA-256 `C40A8A73916C2E6120018616AC247BED742698C246A2C89E7E50258EB9FDFFD1`.
- APK SHA-256: `E35FD099A0C08C359EA1FD41B0A18F7D64496DFFACA86CB0EA3CF64BDC85B43B`. Matching shared copies: `D:\Sync_Data\BJ_Private\0000_활동(취미 등)\Astrophoto\Dev\EAFCON\EAFCON_Dev_1.2.2.1.apk` and the cache-distinct `EAFCON_Dev_1.2.2.1_vc15_E35FD099.apk`.
- Production-path AAB: `app/build/outputs/distribution/EAFCON_1.2.2.1_Play.aab`, SHA-256 `9FA718D2C8E1531BAA04B4FB94F142CEE8EEF8616E831408F8EE01B003007FCD`. Building this path does not authorize Play upload or publication.
- This record is the pre-release validation of the 1.2.2.1 working tree. Canonical release artifacts must be rebuilt from the finalized release commit. All newly characterized settings remain source-verified/hardware-unverified pending the manual checklist.
