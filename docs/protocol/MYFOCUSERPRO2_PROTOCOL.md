# MyFocuserPro2 / Gemini protocol catalog

EAFCON 1.2.2.1 separates protocol coverage from device characterization. `MyFocuserPro2 Generic` follows the official firmware 338 source and Protocol 334 documentation. `Gemini Focuser Pro` uses the same protocol family with additional evidence from the manufacturer-supplied `GeminiFocuserProConsole.exe` 2.1.0.0 (April 2024). Selecting Gemini does not turn source evidence into a physical test result.

Evidence priority is: recorded EAFCON Gemini hardware results, Gemini manufacturer console, official MyFocuserPro2 firmware/documentation, then INDI. The console was inspected statically; EAFCON does not bundle or execute it. `VERIFIED` below means previously exercised on the physical Gemini unit. All other implemented commands remain `SOURCE-VERIFIED / HARDWARE-UNVERIFIED` until separately tested.

## Core and diagnostics

| Operation | TX | RX | Evidence | Gemini state |
|---|---|---|---|---|
| Handshake | `:02#` | `EOK#` | EAFCON record, console, firmware | VERIFIED |
| Position | `:00#` | `P<n>#` | all sources | VERIFIED |
| Moving | `:01#` | `I0#` / `I1#` | all sources | VERIFIED |
| Firmware version | `:03#` | `F<n>#` | console, firmware, INDI | SOURCE-VERIFIED |
| Firmware name/version | `:04#` | `F<text>\r\n<version>#` | console, firmware | SOURCE-VERIFIED |
| Absolute move | `:05<n>#` | none | all sources | VERIFIED |
| Temperature | `:06#` | `Z<n>#` | all sources | VERIFIED |
| Set maximum | `:07<n>#` | none; read with `:08#` | console, firmware, INDI | SOURCE-VERIFIED |
| Maximum | `:08#` | `M<n>#` | all sources | VERIFIED |
| Maximum increment | `:10#` | `Y<n>#` | console, firmware | SOURCE-VERIFIED |
| Stop | `:27#` | none | console, firmware, INDI | SOURCE-VERIFIED |
| Relative move | `:64<signed-n>#` | none | firmware 338 | SOURCE-VERIFIED |
| Temperature probe present | `:83#` | `c0#` / `c1#` | console, firmware | SOURCE-VERIFIED |
| Stepper power detected | `:89#` | `90#` / `91#` | console, firmware | SOURCE-VERIFIED |

## Advanced controls

| Operation | Read TX/RX | Write TX | Range / notes |
|---|---|---|---|
| Reverse | `:13#` → `R0/1#` | `:14<0/1>#` | idle write |
| Motor speed | `:43#` → `C<0..2>#` | generic canonical `:150<n>#`; Gemini console canonical `:15<n>#` | firmware parses either as integer 0..2; profile-specific encoder preserves established behavior |
| Backlash IN enable | `:74#` → `4<0/1>#` | `:73<0/1>#` | affects later motion |
| Backlash IN steps | `:78#` → `6<n>#` | `:77<n>#` | firmware byte, EAFCON 0..255 |
| Backlash OUT enable | `:76#` → `5<0/1>#` | `:75<0/1>#` | affects later motion |
| Backlash OUT steps | `:80#` → `7<n>#` | `:79<n>#` | firmware byte, EAFCON 0..255 |
| Temperature compensation enable | `:24#` → `1<0/1>#` | `:23<0/1>#` | can initiate autonomous motion; Dev only |
| Compensation available | `:25#` → `A<0/1>#` | — | probe/firmware dependent |
| Temperature coefficient | `:26#` → `B<n>#` | `:22<n>#` | firmware 338 clamps 0..1000; older doc says 0..400; INDI UI used 0..50. Dev uses firmware range and warns user. |
| Compensation direction | `:87#` → `k<0/1>#` | `:88<0/1>#` | firmware value 0: temperature rise increases target position; value 1: temperature rise decreases target position; Dev only |

Temperature compensation is exposed in Development for the requested hardware test. It is locked in Production because controller-generated motion cannot be bounded by EAFCON's host-side software limit. Test disabled first, then coefficient and direction separately, and enable last.

## Administrative controls

| Operation | Read TX/RX | Write TX | Range / effect |
|---|---|---|---|
| Idle coil power / holding torque | `:11#` → `O0/1#` | `:12<0/1>#` | `0`: coils still energize for motion, then the driver releases them while idle; `1`: coils remain energized while idle for holding torque |
| Controller temperature unit | `:38#` → `b0/1#` (0=F, 1=C) | `:17#` F, `:16#` C | separate from phone display preference |
| Step-size reporting enable | `:32#` → `U0/1#` | `:18<0/1>#` | firmware-dependent |
| Step size | `:33#` → `T<decimal>#` | `:19<decimal>#` | non-negative; firmware-specific upper bound |
| Temperature resolution | `:21#` → `Q<9..12>#` | `:20<9..12>#` | DS18B20 resolution |
| Step mode | `:29#` → `S<mode>#` | `:30<mode>#` | 1,2,4,8,16,32,64,128,256; invalidates coordinates |
| Sync position | — | `:31<n>#` | changes logical coordinates without motion |
| LCD page time | `:34#` → `X<milliseconds>#` | `:35<2..10>#` | seconds on write |
| Display enabled | `:37#` → `D0/1#` | `:36<0/1>#` | only effective on display-equipped builds |
| Home | — | `:28#` | moves hardware; no completion ACK |
| Home switch compiled | `:50#` → `l0/1#` | — | build capability |
| Position update while moving | `:62#` → `L0/1#` | `:61<0/1>#` | display behavior |
| Home switch state | `:63#` → `H0/1#` | — | only meaningful when installed |
| Jog enabled | `:66#` → `K0/1#` | `:65<0/1>#` | can move continuously; Dev only |
| Jog direction | `:68#` → `V0/1#` | `:67<0/1>#` | set direction before enabling; Dev only |
| Delay after move | `:72#` → `3<n>#` | `:71<n>#` | firmware 338 stores a byte, so EAFCON uses 0..255 ms; older notes mentioning 500 conflict |
| Display page options | `:93#` → `l<binary>#` | `:92<binary>#` | display-build dependent, up to 9 bits |

EAFCON 1.2.2.1 provides `°C`/`°F` controller buttons only in Administrative controls. They use `:16#`/`:17#`, verify with `:38#`, and synchronize the phone presentation.

Official firmware 338 initializes Coil Power to `0`. Movement always enables the motor driver regardless of that stored value. When movement finishes, value `0` disables the driver and removes idle holding torque; value `1` leaves the driver energized. Writing `:120#` or `:121#` also applies the driver state immediately. Therefore this setting describes **idle holding behavior**, not whether the focuser is allowed to move. The user observed `O0#` after connecting the physical Gemini; the expected heat, power, and holding-torque effects still require a separately recorded physical test.

## Device Administration

| Operation | TX | RX / behavior | Evidence | Exposure |
|---|---|---|---|---|
| Persist settings to EEPROM | `:48#` | no ACK; writes current settings/position | Gemini console and firmware 338 | Dev only, explicit confirmation |
| Reset controller | `:40#` | Gemini physical test produced the same factory-reset behavior as `:42#`; exact RX not captured | Gemini console, firmware 338, Gemini physical test | catalogued; hidden from UI |
| Restore defaults / Factory reset | `:42#` | Gemini physical test confirmed factory-reset behavior; exact RX not captured | Gemini console, firmware 338, Gemini physical test | Dev only, explicit confirmation |

These frames are no longer UNKNOWN. `:40#` and `:42#` are VERIFIED for the reported Gemini physical outcome, while EEPROM persist remains hardware-unverified. Production does not expose the high-risk writes. Firmware flashing remains out of scope.

## Known source disagreements and intentionally unimplemented frames

- Temperature coefficient ranges differ: Protocol 334 text 0..400, firmware 338 code 0..1000, and INDI UI 0..50. EAFCON documents the conflict and uses the current official firmware range only in Dev.
- Delay-after-move older documentation mentions values beyond 255, while firmware 338 stores `(byte)value & 255`. EAFCON rejects values outside 0..255.
- Protocol documentation mentions a home-switch setter `:99x#`; the inspected common firmware 338 sources do not implement case 99 and the Gemini console only reads status. EAFCON does not encode it.
- Commands 85/86 and 94/95 are reused inconsistently across board/display variants and are absent from the common firmware path inspected. EAFCON does not generate them.
- Display page option syntax depends on the display build. EAFCON has a typed codec but does not provide a normal UI until the actual Gemini display/page count is established.
- Preset commands 90/91 are disabled/commented in common firmware 338. EAFCON presets remain app-local and never use device preset frames.

## Verification rule

Unit tests validate formatting, parsing, bounds, dispatch, and safety gates. They never promote a command to `VERIFIED`. Record selected profile, firmware, exact TX/RX, prior state, resulting state, and observed physical behavior for each individual Gemini test before changing its verification label.
