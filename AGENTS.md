# Repository instructions

Read `agent.md` for the development plan, verified Gemini hardware facts, safety requirements, milestones, test plan, and collaboration rules before making changes.

## Current implementation state

- EAFCON 1.1.2 uses package/application ID `dev.sphc.eafcon`, targets API 36, and includes persistent presets, SAF JSON import/export, richer USB metadata, and passive device scanning. The 1.1.2 candidate isolates per-device scan failures and stabilizes attach/detach rescans after a 1.1.1 field test found that removing Gemini could temporarily clear every listed device. Gemini is the physically tested focuser; the exact 1.1.2 fix still needs hardware regression testing and other compatible models remain unverified.
- Keep the public README and developer instructions aligned with the code. Clearly distinguish Gemini physical testing from untested compatible devices.
- Do not guess protocol commands. STOP may use only source-verified `:27#`; Gemini physical STOP remains hardware-unverified. Never send an unverified set-maximum command.

## Working rules

- Keep protocol parsing independent of Android and USB library types.
- Keep serial I/O outside Activities and Composables.
- Enforce software limits before issuing any move; require a known current position for relative moves.
- Preset selection only fills Target Position; preset import/export and CRUD never move hardware. Validate preset positions against known safety maxima.
- USB SCAN only enumerates and displays metadata: never open/claim candidate ports, reset devices, probe protocols, or send commands. Open/claim only after explicit user selection and CONNECT.
- Treat USB path, deviceId, Bus/Device numbers as temporary session details, not persistent identity. Never identify hardware by VID/PID alone.
- With multiple compatible devices, preserve a still-present explicit selection but never auto-select a new first device. Connect stays disabled until selection.
- A stale or detached USB entry must not erase successfully enumerated neighboring devices. Serialize/cancel overlapping rescans and allow Android's USB list to settle after topology broadcasts.
- Validate the complete versioned preset JSON import before changing stored data. Merge/Replace behavior and Replace confirmation must remain explicit.
- Documentation must distinguish VERIFIED, SOURCE-VERIFIED/HARDWARE-UNVERIFIED, and UNVERIFIED facts.
- Never commit signing keystores, passwords, local signing properties, or other release secrets.
- Run the relevant unit tests and build after meaningful changes when the local Android toolchain is available. Report missing tools or failed checks plainly.
- Update `agent.md` when architecture, workflow, or verified/unverified hardware facts change.
