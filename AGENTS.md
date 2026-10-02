# Repository instructions

Read `agent.md` for the development plan, verified Gemini hardware facts, safety requirements, milestones, test plan, and collaboration rules before making changes.

## Current implementation state

- The current unreleased EAFCON 1.1.6 source targets API 36 and uses permanent dual distribution variants from one source commit: Google Play Production is `dev.sphc.eafcon` / `EAFCON` / `prodRelease` AAB, while GitHub Development is `dev.sphc.eafcon.dev` / `EAFCON Dev` / `devDebug` APK. Both share the same code and version. GitHub receives only the Dev APK; archive the Production AAB locally unless an explicit Play upload is requested. Production upload signing is not configured and signing secrets must never enter Git. The apps can coexist but keep separate files, preferences, backups, and USB permissions; only one may own the focuser connection at a time.
- EAFCON includes separate Connection/Control/Settings pages, a persistent top Connection/Control switcher, popup-only preset management, app-private `Preset.json` persistence, Light/Dark/Night Vision themes, persistent English/Korean selection, optional foreground-only MOVING vibration, and versioned JSON serial connection settings. Android Auto Backup remains enabled per application ID. The connection-profile picker/import UI is not implemented. Field testing confirmed that the 1.1.2 detach-list fix works; the full 1.1.6 hardware regression remains pending. Connecting Gemini physically disconnects an already attached SeRelCam relay and then both devices re-enumerate; treat this as Android host/hub topology or power behavior rather than a Scan-only failure. Gemini is the physically tested focuser and other compatible models remain unverified.
- Keep the public README and developer instructions aligned with the code. Clearly distinguish Gemini physical testing from untested compatible devices.
- Do not guess protocol commands. STOP may use only source-verified `:27#`; Gemini physical STOP remains hardware-unverified. Never send an unverified set-maximum command.

## Working rules

- Keep protocol parsing independent of Android and USB library types.
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
