# RideBridge: context for Claude Code

Personal Android app (not published, sideloaded via adb) for Gaël. Drafted in a claude.ai chat that could not
compile Android code; this is the hand-off to continue on the Fedora laptop.

## Goal
Zwift Ride shift buttons → taps on MyWhoosh's on-screen virtual-shifting +/- buttons, MyWhoosh running on the
same phone. Only gear up / gear down matter.

## Setup
- Phone: Xiaomi Redmi Note 13 Pro, Android 16, HyperOS 3. Connected over USB with adb.
- Controller: Zwift Ride shifters. Trainer: Jet Black Victory, connected to MyWhoosh on the same phone.
  The app must never touch the trainer.
- MyWhoosh in landscape, virtual shifting enabled.
- Laptop: Fedora, JDK already installed, no Android SDK yet. Gaël is new to Android builds: set up the
  command-line SDK (no Android Studio), the Gradle wrapper, and local.properties for him.

## Decisions
- Kotlin, no third-party dependencies, minSdk 33, targetSdk 36.
- Simulated taps through an AccessibilityService (`dispatchGesture`). MyWhoosh's network-link route was ruled out.
- Calibration overlay (TYPE_ACCESSIBILITY_OVERLAY) with two draggable markers instead of hardcoded coordinates.
- Shift buttons are learned in-app ("press the button for gear up"); both may be on one side.
  Defaults: left shift up / left shift down.
- Foreground service (connectedDevice type) so it survives while MyWhoosh is in front.
- Optional feedback on shift: off / controller vibration / phone vibration. Default controller, for early tests.
- Xiaomi setup checklist in the app: accessibility, restricted settings, autostart, battery.

## Protocol (from BikeControl, github.com/jonasbark/swiftcontrol, lib/bluetooth/devices/zwift/)
- Connect to the left (first payload byte 0x08) or right (0x07) hood, manufacturer id 0x094A. Ride v1 left hood reports both hoods; Ride v2 likely needs one connection per hood (not implemented).
- Service fc82 (or legacy 00000001-19CA-…). Notify on …0002, indicate on …0004, write "RideOn" to …0003.
- Button frames: opcode 0x23 + RideKeyPadStatus protobuf; field 1 ButtonMap, active-low bits (0 = pressed).
- Vibrate: write 12 12 08 0A 06 08 02 10 00 18 20 to …0003 without response.
- Risk: Ride firmware ≥ 1.3.0 hides the Zwift service until Zwift unlocks it (~1 day). Unlock not implemented.
  Ask Gaël for the firmware version shown in Zwift Companion.
- BikeControl's current code is under a non-commercial licence: re-implement, don't copy files.

## Status / next steps
1. Code is drafted but has never been compiled. Plugin versions in build.gradle.kts are unverified.
2. Install SDK, generate the wrapper, `./gradlew assembleDebug`, fix compile errors.
3. Install with adb, then test BLE first: does status reach "Ready" and do button presses show up?
   Use `adb logcat` while debugging.
4. Then calibration and taps in MyWhoosh. Gaël will provide a MyWhoosh screenshot mid-ride.
