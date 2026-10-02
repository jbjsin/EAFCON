# EAFCON

**Developer: SongPaHaeChi · SPHC**

EAFCON is an Android USB serial controller for manual electronic focuser operation. Gemini Focuser is the physically tested device. The command set targets MyFocuserPro2-compatible focusers, but compatibility with other models is not guaranteed and remains unverified.

## Features

- Passive USB serial discovery with manual device selection and Android USB permission handling.
- Safer selection when identical CH340 devices share VID/PID: one device may be selected automatically, while two or more require an explicit choice.
- Current position, movement state, temperature, and device-reported maximum display.
- User-configured software safety maximum enforced before movement.
- Absolute movement, ±5/±25/±50/±100 relative movement, and custom relative steps.
- Separate Connection and Control pages available from a persistent top switcher, plus Connection/Control/Settings navigation in the top-right menu and a connection indicator on every page.
- Android Back returns from Settings and future secondary menu pages to the Connection/Control page used immediately before entering the menu, instead of closing EAFCON.
- Light, low-glare dark, and red-only night-vision themes.
- In-app English/Korean language selection, saved across launches and applied to navigation, controls, dialogs, guidance, and common operation messages.
- Optional foreground vibration while the focuser reports MOVING, with persistent five-level strength selection at 10/30/50/70/90% of Android's maximum amplitude. Selecting a level gives a short preview pulse; movement vibration cancels when movement ends, USB disconnects, or EAFCON leaves the foreground.
- Named position presets in a dedicated dialog, with distinct Load, Create, and Edit flows plus portable JSON import/export through Android's file picker. First launch creates private `Preset.json` with `Default System` at position `7500`.
- Editable baud rate, data bits, stop bits, parity, flow control, and timeout values loaded from a versioned JSON connection profile.
- Demo mode for exercising the UI without hardware.
- STOP button while moving. STOP uses the source-backed MyFocuserPro2 `:27#` abort command; physical STOP behavior on the Gemini EAF remains to be explicitly verified.
- Parser support for fragmented and coalesced `#`-terminated serial responses.

## Connect and focus

1. Connect the focuser to an Android device with USB Host support, using an OTG adapter or powered hub as needed.
2. Open EAFCON and tap **Scan**. When multiple compatible serial devices are present, select the intended device explicitly; VID/PID alone does not identify a Gemini Focuser.
3. Tap **Connect** and grant Android USB permission.
4. Open **Control** from the top-right menu and set a conservative software safety maximum within the focuser's mechanical travel.
5. Enter a target or use a relative control. Custom steps use the centered step value with the adjacent −/+ movement buttons.
6. Tap **Disconnect** on the Connection page when finished.

Preset selection only fills **Target Position**. Preset CRUD and JSON import/export never move hardware. Import validates the complete selected file, then merges matching IDs and appends new IDs into the app-private `Preset.json`; a failed import leaves the stored document unchanged.

Android Auto Backup is enabled. Reinstalling EAFCON with the same application ID can restore `Preset.json` or legacy preset preferences from the Android backup service instead of starting with only the default preset. Use Android's Clear storage action when a completely fresh local state is required.

The Settings page edits the active serial profile and applies it only to the next connection. The bundled `Gemini / MyFocuserPro2` profile contains the verified 9600 8N1 settings. Connection profile selection/import is not exposed yet; the underlying versioned JSON catalog is present for future model expansion. Changing serial parameters does not add another focuser protocol, so compatibility beyond MyFocuserPro2 remains unverified.

USB **Scan** only enumerates serial-capable devices and reads available metadata. It does not open or claim ports, reset devices, probe protocols, or send commands. Device path, deviceId, and Bus/Device values help distinguish devices only during the current Android USB enumeration and are not persistent hardware identities.

## Compatibility and verification

Historically verified on a physical Gemini EAF with Android USB Host and a CH340/CH34x adapter:

- 9600 baud, 8 data bits, no parity, one stop bit, no flow control.
- `:02#` handshake, `:00#` position, `:01#` movement state, `:06#` temperature, and `:08#` device maximum.
- Canonical `:05<position>#` absolute movement and the `I1#` to `I0#` movement transition.
- Successful focuser operation through EAFCON on a Galaxy Fold4.

These historical results do not replace regression testing for each release. The `:27#` STOP command is source-verified from the INDI MyFocuserPro2 driver but remains hardware-unverified on Gemini. Other MyFocuserPro2-compatible focuser models, RTS/DTR requirements on other hardware, automatic device identification, and device-maximum-changing commands are unverified. EAFCON never sends an unverified set-maximum command.

## Android and builds

EAFCON 1.1.5 uses application ID `dev.sphc.eafcon`, requires Android 8.0/API 26 or newer, and targets/compiles against API 36. The debug APK is named `EAFCON_1.1.5.apk`. Developer setup, App Bundle creation, signing guidance, and hardware regression procedures are in [README_DEV.md](README_DEV.md).

The Google Play listing is not published yet. The final application ID has been selected, and Play release preparation is in progress. Because the application ID changed from early development builds, 1.1.5 installs as a different Android application rather than updating an older `com.astrophoto.geminifocuser` installation. It updates builds that already use `dev.sphc.eafcon`, including 1.1.1 through 1.1.4, when signed with the same key.

## Development progress

### DONE

- USB serial discovery, manual selection, permission, connect, and disconnect.
- Gemini handshake, position, movement, temperature, and device maximum display.
- Software safety limit, absolute movement, fixed/custom relative movement, and Demo mode.
- Persistent position presets in app-private `Preset.json`, with first-run defaults and atomic versioned JSON import/export/merge.
- Connection/Control/Settings navigation, popup-only preset management, and persistent Light/Dark/Night Vision themes.
- Persistent English/Korean interface selection on the Settings page.
- Card-grouped Settings sections for interface, movement vibration, serial connection, and compatibility guidance.
- Versioned JSON serial settings with editable connection parameters and a bundled Gemini default profile.
- Fragmented/coalesced parser handling and source-backed `:27#` STOP implementation.
- Historical physical operation with Gemini EAF.
- API 36 target, final `dev.sphc.eafcon` application ID, and basic GitHub Actions CI.

### IN PROGRESS

- Google Play account/release preparation and release signing setup.
- API 36 regression validation on Android hardware.
- Explicit physical Gemini STOP validation.
- Physical revalidation of Gemini detach/reconnect while the SeRelCam relay remains attached.
- Physical validation of preset and remaining identical-VID/PID multi-device flows.

### PLANNED

- Broader MyFocuserPro2-compatible hardware testing.
- Optional automatic device identification only if it can be made safe without interfering with other USB clients.
- Research a safe, source-verified way to change the focuser's internally reported current-position value. No command is selected or implemented yet.
- Explore grouped presets with a shared reference position and per-item increments/decrements; the interaction and storage model are not yet decided.

## Safety

Configure the software safety maximum conservatively and keep focuser travel clear. STOP is not documented as an emergency stop; remain able to remove power if the hardware behaves unexpectedly.

## License

EAFCON currently has no selected project distribution license. Third-party dependency licensing is documented separately in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); `usb-serial-for-android` is MIT licensed.
