# EAFCON

EAFCON is designed to work with electronic focusers that use the MyFocuserPro2 serial protocol. General use should be possible with compatible EAFs, but physical-device testing so far has been done only with the Gemini Focuser. If you try another compatible focuser, please share your results.

**Developer: SongPaHaeChi**

EAFCON is an Android USB-serial app for viewing focuser status and making manual focus adjustments. It has been tested on a Galaxy Fold4 with a Gemini EAF using a CH340/CH34x USB-serial adapter.

## Features

- Manually select a USB serial device, then connect or disconnect from the same button.
- View current position, movement state, temperature, and the device-reported maximum position.
- Set a software safety limit before moving.
- Move to an absolute position or use relative step controls.
- Stop an in-progress move with the STOP button. STOP is available only while the focuser reports that it is moving.
- Use Demo mode to exercise the controls without a focuser.

## Connect and focus

1. Connect the focuser to an Android device that supports USB Host using a USB-C OTG adapter or powered hub as needed.
2. Open EAFCON, scan for USB devices, and select the intended focuser manually. Devices can share USB identifiers, so EAFCON does not auto-select a focuser by VID/PID.
3. Tap **Connect** and grant Android USB permission.
4. Set a software safety maximum that stays within the focuser's mechanical travel.
5. Enter an absolute target or choose a relative step. During a move, use **STOP** to halt it.
6. Tap **Disconnect** when finished.

The app uses 9600 baud, 8 data bits, no parity, one stop bit, and no flow control. A known software maximum is required before movement is enabled. EAFCON does not provide autofocus, and it does not change the device's configured maximum position.

## Compatibility and testing

The serial command set follows MyFocuserPro2-compatible EAF behavior. Other EAFs using that protocol may work, but Gemini Focuser is the only physical focuser tested so far. USB-serial chipsets, firmware variations, and Android device behavior may affect compatibility. Please test cautiously within the focuser's safe travel and report the focuser model, Android device, and results.

The project has also been built and tested with a simulated focuser. Demo mode is not a substitute for checking a physical device.

## Build

The Android Studio project targets Android API 35 and compiles against API 36. Developer setup and build instructions are in [README_DEV.md](README_DEV.md). The version 1.0.0 debug APK is named `EAFCON_1.0.0.apk`.

## Safety

Configure the software safety maximum conservatively and keep focuser travel clear. STOP sends the halt command used by the MyFocuserPro2-compatible driver. Follow your focuser manufacturer's instructions and remain able to remove power if a physical device behaves unexpectedly.

## License

No project license has been selected yet. The USB serial dependency is MIT licensed; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
