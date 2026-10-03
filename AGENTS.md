# Repository instructions

Read `agent.md` for the development plan, verified Gemini hardware facts, safety requirements, milestones, test plan, and collaboration rules before making changes.

## Current implementation state

- EAFCON 1.2.2.1 targets API 36 and uses permanent dual distribution variants from one source tree: Google Play Production is `dev.sphc.eafcon` / `EAFCON` / `prodRelease` AAB, while GitHub Development is `dev.sphc.eafcon.dev` / `EAFCON Dev` / `devDebug` APK. Version metadata is code 15/name 1.2.2.1. Both share the same code and version. GitHub receives only the Dev APK; archive the Production AAB locally unless an explicit Play upload is requested. Gradle never contains Production secrets; the final AAB may be post-signed with the external EAFCON upload key and verified before local archival. Signing secrets must never enter Git or logs. The apps can coexist but keep separate files, preferences, backups, and USB permissions; only one may own the focuser connection at a time.
- EAFCON preserves its 1.1.x behavior and 1.2.x safety model. 1.2.2.1 explicitly selects `Gemini Focuser Pro` (default) or `MyFocuserPro2 Generic`; never infer this from VID/PID. Gemini characterization is backed by manufacturer console 2.1.0.0 while generic coverage follows official firmware 338/Protocol 334. `devDebug` exposes the requested temperature compensation, device maximum, EEPROM persist `:48#`, and factory reset `:42#` controls; `prodRelease` locks these high-risk writes. Gemini physical testing found controller reset `:40#` and restore defaults `:42#` both behave as factory reset, so `:40#` remains catalogued but is hidden from the UI. Gemini reports Coil Power `O0#` on connection; firmware 338 defines this as releasing coil power only while idle, not disabling movement. Controller C/F commands `:16#`/`:17#`, verified by `:38#`, are the sole temperature-unit controls and synchronize the phone display. See `docs/protocol/MYFOCUSERPRO2_PROTOCOL.md` and `docs/architecture/FOCUSER_DRIVER_ARCHITECTURE.md`.
- Android Auto Backup remains enabled per application ID. The connection-profile picker/import UI is not implemented. Field testing confirmed the 1.1.2 detach-list fix; the 1.1.6 full hardware regression remains pending. Connecting Gemini physically disconnects an already attached SeRelCam relay and then both devices re-enumerate; treat this as Android host/hub topology or power behavior rather than a Scan-only failure. Gemini is the physically tested focuser and other compatible models remain unverified.
- Keep the public README and developer instructions aligned with the code. Clearly distinguish Gemini physical testing from untested compatible devices.
- Do not guess protocol commands. STOP may use only source-verified `:27#`; Gemini physical STOP remains hardware-unverified. The source-backed set-maximum command is enabled only in `devDebug` for explicit controlled hardware testing and must remain locked in `prodRelease` until Gemini physical verification is recorded.

## Working rules

- Keep protocol parsing independent of Android and USB library types.
- Keep generic controller/driver APIs free of USB serial, baud/framing, ASCII, and delimiter assumptions. Optional commands must use capability metadata and must not become dummy methods on every driver.
- Gate optional writes on known/supported capability, feature category, validated value, and idle state; verify reliable readback before reporting success. One-way/no-ACK operations must be labeled unconfirmed, high-risk Device Administration actions require explicit confirmation, and UNKNOWN frames remain unavailable.
- Keep serial I/O outside Activities and Composables.
- Enforce software limits before issuing any move; require a known current position for relative moves.
- Preset selection only fills Target Position; preset import/export and CRUD never move hardware. Validate preset positions against known safety maxima.
- Persist presets in app-private `filesDir/Preset.json`. First launch creates the stable-ID `Default System` preset at position `7500`; migrate the legacy SharedPreferences document when present. Import validates the complete selected document, then atomically merges by ID into `Preset.json`.
- Keep serial settings in the versioned JSON profile layer, validate before persistence/use, and apply only on a later explicit Connect. Do not treat baud/framing changes as support for another focuser protocol.
- Treat Connection and Control as primary pages. Android Back from Settings or any future secondary menu page must return to the primary page active before entry instead of finishing the Activity.
- USB SCAN only enumerates and displays metadata: never open/claim candidate ports, reset devices, probe protocols, or send commands. Open/claim only after explicit user selection and CONNECT.
- Treat USB path, deviceId, Bus/Device numbers as temporary session details, not persistent identity. Never identify hardware by VID/PID alone.
- With multiple compatible devices, preserve a still-present explicit selection but never auto-select a new first device. Connect stays disabled until selection.
- A stale or detached USB entry must not erase successfully enumerated neighboring devices. Serialize/cancel overlapping rescans and allow Android's USB list to settle after topology broadcasts.
- Do not claim EAFCON can preserve another app's serial connection across a physical USB bus reset. Automatic reconnection is unsafe when re-enumerated devices share VID/PID and temporary paths change.
- Validate the complete versioned preset JSON import before changing stored data. Import always merges by ID; matching IDs update, new IDs append, and duplicate names with different IDs remain separate. Never partially apply an invalid import.
- Documentation must distinguish VERIFIED, SOURCE-VERIFIED/HARDWARE-UNVERIFIED, and UNVERIFIED facts.
- Never commit signing keystores, passwords, local signing properties, or other release secrets.
- For a release, finalize one version commit, then build both `devDebug` and `prodRelease` from that exact commit. Do not modify source between artifacts. A GitHub release attaches `EAFCON_Dev_<version>.apk` only and must identify it as a development/field-test build. Keep `EAFCON_<version>_Play.aab` and its hash/commit/signing manifest in the local archive. Never upload to Google Play without an explicit user request.
- Run the relevant unit tests and build after meaningful changes when the local Android toolchain is available. Report missing tools or failed checks plainly.
- Update `agent.md` when architecture, workflow, or verified/unverified hardware facts change.
