# MeowPrinter Agents Guide

## Project shape
- `app/src/main/java/com/github/thiagokokada/meowprinter/ui`: activity, fragments, and UI helpers.
- `app/src/main/java/com/github/thiagokokada/meowprinter/ble`: BLE scanning, permissions, and Nordic BLE manager integration.
- `app/src/main/java/com/github/thiagokokada/meowprinter/document`: compose document model, codec, and rendering.
- `app/src/main/java/com/github/thiagokokada/meowprinter/image`: image preparation and dithering.
- `app/src/main/java/com/github/thiagokokada/meowprinter/print`: printer protocol, print energy, and built-in test page generation.
- `app/src/main/java/com/github/thiagokokada/meowprinter/data`: persisted settings, document draft state, managed compose image storage, and session log state.
- `README.md`: end-user project overview, features, and build/test commands.
- `LICENSE`: GPL-3.0-or-later project license text.

## Working rules
- Keep the app flow minimal. Avoid adding new screens or abstractions unless they remove clear duplication.
- Prefer immutable values and `buildList`/`listOf` over mutable collections unless mutation is required by the API.
- Treat `.idea/*` changes as user-local unless the task explicitly asks for IDE config updates.
- Keep BLE behavior conservative. Connection logic should remain compatible with the Nordic Android BLE library already in use.
- Reuse the foreground `BlePrinterManager` whenever possible. Do not add throwaway temporary BLE connections for saved-printer actions like test page or paper advance/retract.
- Saved-printer actions that need to recover connection state should reconnect the foreground manager, not create a one-off manager for a single command.
- Keep the Image / Compose / Settings structure consistent unless the task explicitly changes navigation.
- Reuse the shared document render path for compose preview and printing instead of adding separate rendering flows.
- Keep `Print image` / `Print document` button gating strict: they should reflect real foreground connection readiness, not silently reconnect on tap.
- Shared import flow should stay layered:
  - `SharedImportRequestParser` handles Android `ACTION_SEND` parsing
  - `SharedTextImportSuggester` handles text-vs-QR import suggestions near the document/QR domain
  - `MainActivity` should only coordinate chooser UI and dispatch explicit import actions
- Prefer explicit shared-import action types over anonymous chooser callbacks when the flow has multiple destinations.
- Prefer the shared Android-Iconics icon packages for UI icons instead of hand-authored vector paths when a suitable icon already exists.
- The app assumes Android 13+ behavior; do not add pre-Tiramisu compatibility branches unless explicitly requested.
- Image cropping uses CanHub `CropImageView` hosted inside `ImageCropActivity`, not the deprecated contract/activity wrapper flow.
- Crop outputs are temporary cache files exposed through `FileProvider`; if crop output format changes, keep the file extension aligned with the compressor format.
- Compose document image blocks must be persisted into app-managed files via `DocumentImageStore`, not left pointing at transient crop-cache URIs.
- Keep the draft format and export format distinct:
  - app draft/state stays lightweight and may reference managed `imageUri` values
  - exported compose files must be self-contained and embed image bytes
- Canvas document codec versioning is parser-based:
  - `CanvasDocumentCodec` only dispatches by version
  - `CanvasDocumentCodecParserV1` owns the current schema
  - add new versions by introducing a new parser class, not by growing conditionals inside the dispatcher
- Compose QR blocks are structured content, not image blocks:
  - persist typed QR payload fields in the document schema
  - generate QR bitmaps at render time with `QrBitmapGenerator`
  - do not route QR blocks through photo-style preprocessing or dithering
- Compose image previews and Image Print previews must use the same rasterization assumptions:
  - dither at printer-width basis first
  - then downscale for display using the shared `PreviewBitmapScaler`
  - avoid separate preview pipelines that can drift visually
- Compose has two preview levels:
  - inline block previews may stay lightweight for editor responsiveness
  - the explicit document preview should use the same prepared print pipeline as actual printing
- In `TextFragment`, prefer small document-mutation helpers over repeating direct `CanvasDocumentEditor.*(currentDocument, ...)` calls throughout the UI code.
- Image resizers and ditherers are interface-based:
  - `ImageResizer.kt` defines the resizer interface, implementations, and registry
  - `ImageDitherer.kt` defines the ditherer interface, implementations, and registry
  - keep `ImagePrintPreparer` as orchestration, not as the home for algorithm implementations
- For custom resizers, bounded decode width matters:
  - do not decode full-resolution camera images just to downscale to printer width
  - use the resizer’s `decodeWidth(...)` policy so custom resizers still get headroom without huge allocations
- Prefer `val` by default:
  - use `var` only when the state is genuinely mutable and forcing `val` would make the code more complicated or less clear
  - real Android/BLE/dialog/lifecycle coordinator state is an acceptable `var` use case
  - prioritize removing one-shot setup vars and duplicated mutable update paths before trying to eliminate necessary mutable state

## Verification
- Unit and build check: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- Instrumentation check: `./gradlew :app:connectedDebugAndroidTest`

## Integration tests
- Prefer instrumentation smoke tests for UI regressions that unit tests cannot catch, especially around:
  - Compose block flows
  - preview dialogs
  - activity launches and navigation
- Favor `ActivityScenario` and direct `onActivity { ... }` assertions for app-owned dialogs and state, instead of fragile Espresso-only dialog matching.
- When a flow is hard to observe from outside, add a narrow test hook in the activity/fragment rather than building a second production code path just for tests.
- Keep integration tests deterministic:
  - use app-managed temporary images/files
  - avoid depending on external BLE hardware state
  - prefer compile-time validation with `:app:compileDebugAndroidTestKotlin` when no device is available
- Renderer and codec changes should usually get both:
  - a focused unit/instrumentation test at the document/image layer
  - a small UI-level smoke test when the feature is user-triggered
