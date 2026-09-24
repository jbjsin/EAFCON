# Repository instructions

Read `agent.md` for the development plan, verified Gemini hardware facts, safety requirements, milestones, test plan, and collaboration rules before making changes.

## Current implementation state

- EAFCON 1.0.2 includes the Gemini cartoon launcher icon with added white margins, named APK output, USB serial adapter, protocol/control layers, and Compose UI. The user confirmed the app works with a physical Gemini EAF; other MyFocuserPro2-compatible EAF models remain untested.
- Keep the public README and developer instructions aligned with the code. Clearly distinguish Gemini physical testing from untested compatible devices.
- Do not guess protocol commands. STOP may use only the source-backed `:27#` documented in `agent.md`; physical stop behavior remains unverified. Do not send set-maximum commands.

## Working rules

- Keep protocol parsing independent of Android and USB library types.
- Keep serial I/O outside Activities and Composables.
- Enforce software limits before issuing any move; require a known current position for relative moves.
- Run the relevant unit tests and build after meaningful changes when the local Android toolchain is available. Report missing tools or failed checks plainly.
- Update `agent.md` when architecture, workflow, or verified/unverified hardware facts change.
