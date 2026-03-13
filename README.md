# Meow Printer

Android app for BLE thermal printers that use the reverse-engineered Cats / Meow protocol.

## Features

- Auto-connects to the saved BLE printer while the app is in the foreground
- Image printing with crop / rotate, dithering, and configurable print energy
- Compose tab for printable documents with Markdown text blocks and image blocks
- Save and load composed documents with the Android file picker
- Built-in test page
- Paper advance and retract controls
- Session logs and printer diagnostics

## Requirements

- Android 13 or later
- A compatible BLE printer that uses the Cat / Meow protocol
- Bluetooth permissions granted to the app

## Build

```sh
./gradlew :app:assembleDebug
```

## Test

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
