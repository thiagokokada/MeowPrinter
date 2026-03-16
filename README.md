# Meow Printer

<p align="center">
  <img src="docs/play-store-icon-rounded.svg" alt="Meow Printer icon" width="128" height="128">
</p>

Android app for BLE thermal printers that use the reverse-engineered Cats / Meow protocol.

## Features

- Auto-connects to the saved BLE printer while the app is in the foreground
- Image printing with crop / rotate, dithering, and configurable print energy
- Compose tab for printable documents with Markdown text blocks, image blocks, and QR code blocks
- Save and load composed documents with the Android file picker
- Built-in test page
- Paper advance and retract controls
- Session logs and printer diagnostics

## Requirements

- Android 13 or later
- A compatible BLE printer that uses the Cat / Meow protocol
- Bluetooth permissions granted to the app

## Compatible Printers

This app targets BLE thermal printers that use the reverse-engineered Cat / Meow protocol family, typically exposed over the `ae30/ae01/ae02` GATT service and characteristics.

Known working / commonly reported Cat / Meow protocol models:

- `GB01`
- `GB02`
- `GB03`
- `GT01`
- `YT01`
- `MX05`
- `MX06`
- `MX08`
- `MX10`
- `C9`

Known non-compatible family:

- `YHK-xxxx` / `WalkPrint` printers that use Classic Bluetooth instead of the Cat / Meow BLE GATT protocol

If your printer is sold under a different storefront name, the model identifier shown in Bluetooth advertising is usually the useful compatibility clue.

Compatibility references:

- NaitLee `Cat-Printer` known supported models: <https://github.com/NaitLee/Cat-Printer>
- YHK Classic Bluetooth incompatibility note: <https://github.com/abhigkar/YHK-Cat-Thermal-Printer>

## Build

```sh
./gradlew :app:assembleDebug
```

## Test

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
