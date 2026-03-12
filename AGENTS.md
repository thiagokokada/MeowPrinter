# MeowPrinter Agents Guide

## Project shape
- `app/src/main/java/com/github/thiagokokada/meowprinter/ui`: activity and small UI helpers.
- `app/src/main/java/com/github/thiagokokada/meowprinter/ble`: BLE scanning, permissions, and Nordic BLE manager integration.
- `app/src/main/java/com/github/thiagokokada/meowprinter/image`: image preparation and dithering.
- `app/src/main/java/com/github/thiagokokada/meowprinter/print`: printer protocol, print energy, and built-in test page generation.
- `app/src/main/java/com/github/thiagokokada/meowprinter/data`: persisted settings and session log state.

## Working rules
- Keep the app flow minimal. Avoid adding new screens or abstractions unless they remove clear duplication.
- Prefer immutable values and `buildList`/`listOf` over mutable collections unless mutation is required by the API.
- Treat `.idea/*` changes as user-local unless the task explicitly asks for IDE config updates.
- Keep BLE behavior conservative. Connection logic should remain compatible with the Nordic Android BLE library already in use.

## Verification
- Unit and build check: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- Instrumentation check: `./gradlew :app:connectedDebugAndroidTest`
