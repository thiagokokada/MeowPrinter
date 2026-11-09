# Meow Printer

<p align="center">
  <img src="docs/play-store-icon-rounded.svg" alt="Meow Printer icon" width="128" height="128">
</p>

Android app for BLE thermal printers using the reverse-engineered Cats / Meow protocol, with experimental MXW01 support.

## Features

- Auto-connects to the saved BLE printer while the app is in the foreground
- Image printing with crop / rotate, dithering, and configurable print energy
- Compose tab for printable documents with Markdown text blocks, image blocks, and QR code blocks
- Save and load composed documents with the Android file picker
- Built-in test page
- Paper advance and retract controls on supported printers
- Session logs and printer diagnostics

## Requirements

- Android 13 or later
- A compatible Cat / Meow BLE printer or the experimental MXW01 model described below
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

### Experimental MXW01 support

The WAGGS ITER03 / MXW01 has been physically tested for 384-dot monochrome
printing, test pages and raw battery queries. It uses a different `22 21`
protocol with bulk image data on `AE03`, not the original Cat `51 78` protocol.
Automatic selection currently requires the advertised name `MXW01`; exposing
`AE30` alone does not identify the protocol.

Image and Compose printing reuse the existing rendering path and settings.
MXW01 intensity is capped at the tested value 150 (UI 100). The speed setting
adjusts BLE transfer pacing, not motor speed. Battery readings are **raw values**,
not verified percentages; tap the printer name in Image to refresh.

Manual paper movement and end-of-job feed settings are disabled for MXW01.
4bpp/HD modes and other V5X models are not supported by this experimental profile.
See [MXW01 protocol notes and validation checklist](docs/MXW01.md).

### Other compatibility notes

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
