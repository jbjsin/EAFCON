# Focuser driver and capability architecture

## Runtime boundaries

```text
Compose UI -> FocuserViewModel -> FocuserController -> FocuserDriver
                                                     |
                                                     +-- MyFocuserPro2Driver
                                                           -> MyFocuserPro2Protocol
                                                           -> SerialTransport
```

`FocuserDriver` contains only transport-neutral focuser operations and telemetry. It does not mention USB, serial, baud rate, ASCII, or delimiters. A driver owns opening/closing its transport and translates its protocol into EAFCON's core model. A later HID driver can implement the same core contract without implementing serial concepts.

Transport-specific configuration remains owned by `settings/` and `usb/`. `MyFocuserPro2Driver` receives a serial transport provider at construction; UI and controller code do not construct protocol frames or access USB library types. Device enumeration remains passive and still requires explicit selection and Connect.

Optional command families use a separate capability/settings extension. `FocuserDriver` does not contain placeholder methods for unsupported features. The controller validates a descriptor before dispatching an optional setting operation; unsupported, unknown, unavailable-category, and non-idle operations fail before reaching a transport.

## Core driver contract

Core operations are connect/open, disconnect/close, read telemetry (position, movement, optional temperature and device maximum), absolute move, and stop. The controller remains responsible for movement serialization, software-limit enforcement, known-position checks, and trusted state. Driver telemetry values keep optional features nullable rather than fabricating defaults.

The MyFocuserPro2 serial connection profile is not the driver identity. The app currently has one registered driver profile (`MyFocuserPro2`) and one physically tested device model (`Gemini EAF`). Capability and verification data are associated with the selected driver's device profile; an eventual model/firmware identification flow must remain explicit and may not probe during USB Scan.

## Capability descriptor

Each descriptor records capability ID, support status (`SUPPORTED`, `UNSUPPORTED`, `UNKNOWN`), access (`READ_ONLY`, `READ_WRITE`, `WRITE_ONLY`), category, verification state, risk, persistence (`NO`, `YES`, `UNKNOWN`), idle requirement, optional minimum/maximum/default, and whether it can move hardware or change logical coordinates. The model is metadata, not an authorization token: controller policy also gates category and movement state.

The 1.2 capability IDs include POSITION, ABSOLUTE_MOVE, STOP, MOVING_STATE, TEMPERATURE, DEVICE_MAX_POSITION, SYNC_POSITION, REVERSE, MOTOR_SPEED, STEP_MODE, BACKLASH_IN, BACKLASH_OUT, TEMPERATURE_COMPENSATION, COIL_POWER, HOME, JOG, DELAY_AFTER_MOVE, DISPLAY_CONFIGURATION, SET_MAX_POSITION, PERSIST_SETTINGS, RESET_CONTROLLER, and RESTORE_DEFAULTS. Unknown protocol frames remain unknown descriptors without executable code.

## Feature-category policy

- **Advanced (1.2.0):** reversible operational tuning in the dedicated Advanced Focuser Controls page. Reverse, Motor Speed, and Backlash IN/OUT require a connected, idle driver and are read back before UI state is updated. Backlash input is capped at 255 because published sources disagree on 255 versus 512.
- **Administrative (1.2.1; UI delivered in 1.2.2):** a separate page exposes source-backed Step Mode, Sync Position, Device Maximum, Coil Power, Home, display configuration, controller C/F mode, and selected firmware settings. Step Mode's coordinate invalidation is persisted before command transmission; EAFCON blocks movement and new software limits until a subsequent explicit position sync matches device readback and the user configures a new software limit. Device Maximum write is enabled only in `devDebug` for controlled hardware verification and remains blocked in `prodRelease` until Gemini behavior is verified. Home has no reliable protocol acknowledgment and is reported as unconfirmed. Phone C/F presentation remains independent of the controller's `:16#`/`:17#` setting.
- **Device Administration (1.2.2/1.2.2.1):** the manufacturer console and official firmware establish EEPROM persist `:48#`, controller reset `:40#`, and restore defaults `:42#`. Gemini physical testing found `:40#` and `:42#` both produce factory-reset behavior, so reset remains catalogued while only `:42#` is user-visible. EEPROM save and Factory reset are capability-gated, require explicit confirmation, and are active only in `devDebug`; `prodRelease` keeps these high-risk writes locked. Firmware flashing is out of scope.

Temperature Compensation is exposed only in `devDebug` for requested hardware testing. Enabling it can cause the controller to move without a host move request, while coefficient ranges differ among sources and EAFCON's software movement limit cannot supervise firmware-generated motion. Step Mode is categorized Administrative because the project guide warns it invalidates the focuser's logical position and requires initial setup again.

## Device profile selection

`FocuserType` distinguishes `Gemini Focuser Pro` from `MyFocuserPro2 Generic`. The choice is explicit, persists independently of serial parameters, and is applied only to a later explicit connection. The app never infers it from VID/PID. Both currently instantiate `MyFocuserPro2Driver`, but the descriptor identity, evidence profile, command dialect (including Gemini's canonical motor-speed form), and future capability divergence remain separate. Gemini is the default to preserve existing installations.

## Serial versus future HID

`SerialParameters`, JSON connection profiles, `SerialTransport`, `UsbSerialPortTransport`, and the ASCII `#`-delimited protocol remain below `MyFocuserPro2Driver`. No public driver/controller method accepts baud rate, parity, delimiters, raw command strings, or USB serial-library types. A future ZWO EAF implementation is not part of 1.2.0.

## Verification and testing

Capability metadata distinguishes source evidence from actual Gemini physical verification. JVM tests prove encoders, parsers, dispatch rules, and controller safety only. The 1.2.2 Dev APK still needs the manual Gemini checklist in `README_DEV.md`; all new Advanced and Administrative writes are hardware-unverified until raw TX/RX and resulting behavior are reported by the user. Device maximum writes may be tested only through `devDebug`; Production remains gated until that hardware test is recorded.
