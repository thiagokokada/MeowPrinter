# MeowPrinter Agents Guide

## Project shape
- `app/src/main/java/com/github/thiagokokada/meowprinter/ui`: activity, fragments, and UI helpers.
- `app/src/main/java/com/github/thiagokokada/meowprinter/ble`: BLE scanning, permissions, and Nordic BLE manager integration.
- `app/src/main/java/com/github/thiagokokada/meowprinter/document`: compose document model, codec, and rendering.
- `app/src/main/java/com/github/thiagokokada/meowprinter/image`: image preparation and dithering.
- `app/src/main/java/com/github/thiagokokada/meowprinter/print`: printer protocol, print energy, and built-in test page generation.
- `app/src/main/java/com/github/thiagokokada/meowprinter/data`: persisted settings, document draft state, and session log state.
- `README.md`: end-user project overview, features, and build/test commands.
- `LICENSE`: GPL-3.0-or-later project license text.

## Working rules
- Keep the app flow minimal. Avoid adding new screens or abstractions unless they remove clear duplication.
- Prefer immutable values and `buildList`/`listOf` over mutable collections unless mutation is required by the API.
- Treat `.idea/*` changes as user-local unless the task explicitly asks for IDE config updates.
- Keep BLE behavior conservative. Connection logic should remain compatible with the Nordic Android BLE library already in use.
- Keep the Image / Compose / Settings structure consistent unless the task explicitly changes navigation.
- Reuse the shared document render path for compose preview and printing instead of adding separate rendering flows.

## Verification
- Unit and build check: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- Instrumentation check: `./gradlew :app:connectedDebugAndroidTest`
