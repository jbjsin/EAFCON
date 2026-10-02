# Repository instructions

Read `agent.md` for the development plan, verified Gemini hardware facts, safety requirements, milestones, test plan, and collaboration rules before making changes.

## Current implementation state

- EAFCON 1.1.5 uses package/application ID `dev.sphc.eafcon`, targets API 36, and includes separate Connection/Control/Settings pages, a persistent top Connection/Control switcher, popup-only preset management, app-private `Preset.json` persistence, Light/Dark/Night Vision themes, persistent English/Korean selection, optional foreground-only MOVING vibration, and versioned JSON serial connection settings. Settings are grouped into Interface, Movement vibration, Serial connection, and Compatibility cards. Vibration levels 1–5 use 10/30/50/70/90% amplitude and give a short preview on selection. Cancel vibration on movement end, disconnect, disable, and Activity pause/stop/destroy; keep vibration preferences independent of serial profiles. Night Vision explicitly uses black/dark-burgundy Material surface containers instead of gray defaults. Localization remains in Android resources and the UI boundary; preserve device strings, user data, and unknown technical errors verbatim. Android Auto Backup remains enabled, so reinstalling the same application ID can restore `Preset.json` or legacy preferences. The connection-profile picker/import UI is not implemented. Serial changes apply only to the next explicit connection and do not establish compatibility with another protocol. Field testing confirmed that the 1.1.2 detach-list fix works; the full 1.1.5 hardware regression remains pending. Connecting Gemini physically disconnects an already attached SeRelCam relay and then both devices re-enumerate; treat this as Android host/hub topology or power behavior rather than a Scan-only failure. Gemini is the physically tested focuser and other compatible models remain unverified.
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
- Run the relevant unit tests and build after meaningful changes when the local Android toolchain is available. Report missing tools or failed checks plainly.
- Update `agent.md` when architecture, workflow, or verified/unverified hardware facts change.
