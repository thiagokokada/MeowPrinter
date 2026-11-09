# Experimental MXW01 support

## Scope and evidence

The physically tested unit is a **WAGGS ITER03 / MXW01** advertising `MXW01`.
The contributor reports working discovery, GATT connection, A1 status queries,
384-dot monochrome image transfer through AE03, test-page printing and AB battery
queries. UI intensity 100, which sends A2=150, was also tested successfully.
These are hardware observations from the contributor, not results of unit tests.
The changes preparing this PR still need a hardware regression pass.

The BLE service/characteristics are AE30 / AE01 (control), AE02 (notifications)
and AE03 (raster data). The original AF30 service fallback is retained, but was
not separately verified on this unit. AE30 is shared with older Cat printers and
must not be used alone to select a protocol.

Only the exact advertised model name `MXW01` (case-insensitive, trimmed) selects
this experimental profile. Other names retain the existing Cat selection
behavior; that fallback does not establish their compatibility. There is no
active protocol probing or automatic V5X alias catalogue.

## Preserved print parameters

- Commands: `22 21`, opcode, `00`, payload length u16le, payload, CRC8, `FF`.
- CRC8 covers only the payload: polynomial `07`, init `00`, no reflection,
  xorout `00`. The published reference calls this Dallas/Maxim, but its numeric
  parameters and implementation are the authority used here.
- Monochrome: 384 pixels / 48 bytes per row, black=1, leftmost pixel in bit 0.
- Minimum buffer: 4320 bytes / 90 rows, padded with white rows.
- A9 announces the padded height, followed by `30 00` for monochrome.
- Sequence: A2, A1 response, A9 acceptance (`00`), AE03 data, AD, AA completion.
- AE03 chunk size: MTU minus 3, bounded to 20..244 bytes; rows may span chunks.
- AE03 inter-chunk delay: existing profile row delay plus 20 ms, bounded to
  8..50 ms. Current Fast/Balanced/Safe values are 20/25/34 ms.

Intensity keeps the pre-existing UI conversion, including its truncation, to
avoid silently recalibrating tested output. Light/Medium/Dark send 73/96/111;
Custom 100 sends 150. This is an application policy, not an equivalence between
Cat's 16-bit energy and MXW01's intensity byte. The wire encoder accepts a byte,
but the UI does not expose the unverified range above 150.

## Responses and session safety

Print and battery transactions share a session mutex. Waiters are registered
before commands are sent, and A9/AA use separate waiters. Unsolicited responses
are not saved for future jobs. Each connection has its own session state.

The notification parser checks the preamble and requires the full declared
payload. Public implementations disagree on optional trailing CRC/footer bytes;
these bytes are deliberately not enforced until real firmware captures establish
their format. The unknown header byte is not required to be zero. Parsing assumes
one complete frame per notification; fragmented/concatenated frames are not
reassembled. A1/A9/AB need a non-empty payload; AA's payload remains undocumented.

A1 is currently diagnostic: its firmware-dependent status fields are not decoded
as a reliable paper/temperature/readiness check. A9 acceptance is required. AA
confirms completion, but its payload has no invented success/error interpretation.

Requests (including their writes) have a 10-second timeout, raster writes have a
10-second timeout each, and AA is awaited for 60 seconds after AD. GATT failures,
timeouts and active transaction cancellations invalidate and close the session.
Reconnect explicitly before retrying; jobs are never automatically resent.
There are no wire transaction IDs, so arbitrary late same-ID duplicates within
an otherwise successful connection cannot be reliably distinguished.

AB's first byte is displayed unsigned and **raw**, never as a percentage. Reported
decimal readings include 78 while charging and 68 unplugged. These observations
do not establish scale or accuracy. The UI label is simply `Battery: 68`, without
`(raw)` or `%`; its accessibility description explains the unconfirmed scale.
An initial query runs after connection work
finishes; tap the printer name in Image for a manual refresh. Settings and Compose
display that same reading without implying a separate refresh action.

Manual advance/retract and end-of-job paper settings are not implemented for
MXW01. Their UI is disabled and the action checks capabilities after reconnection.
4bpp/HD modes, unverified movement commands, and broad V5X support are out of scope.
Opening the image editor retains the app's existing disconnect/reconnect behavior.
After selecting/editing another image, Refresh may be needed if image preparation
interrupts reconnection. The contributor confirmed that once connected, changing
dithering on the same selected image no longer leaves the UI loading or disconnects
the printer. Improving reconnection after image selection is a separate follow-up.

## Verification before submitting the PR

Automated checks:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
# With an Android device/emulator:
./gradlew :app:connectedDebugAndroidTest
```

Hardware regression checklist (record Android version, advertised name and MTU):

- [x] Once connected, change dithering on the same image without a stuck loading
  indicator or a printer disconnection (confirmed by the contributor).
- [ ] Connect and observe the automatic raw battery query; manually refresh it.
- [ ] Start a print immediately after a manual battery refresh; neither should
  time out or consume the other's response.
- [ ] Print the built-in page, an image and a Compose document containing text/QR.
- [ ] Check short jobs around 90 rows and a longer image for missing rows.
- [ ] Confirm existing presets and Custom 100 still produce expected output.
- [ ] Confirm paper controls cannot send commands to MXW01.
- [ ] Cancel during transfer, reconnect, and print a fresh test page.
- [ ] Turn the printer off during a query/print; verify errors and recovery.
- [ ] Regression-test a supported original Cat printer, including paper controls.

For notification evidence, capture `MeowMXW01` Logcat entries (`RX:` contains the
complete received bytes), e.g. `adb logcat -s MeowMXW01`. Save A1/A9/AA/AB frames
with their lengths and MTU. Do not interpret hexadecimal `64` as decimal 64:
`0x64` is decimal 100. The in-app battery log is decimal.

## References and attribution

- [MXW01 protocol and reference implementation (MIT)](https://github.com/jeremy46231/MXW01-catprinter/tree/0744587459fbe9644b4a2295c9a3ecb06b12d5cc)
- [Home Assistant observations (AGPL-3.0-or-later)](https://github.com/jphein/ha-mxw01/tree/11d40797fc5a3677c68a247bebd3a143f66ce025)
- [OpenTLP V5X family evidence](https://github.com/opentlp/opentlp/blob/b21036a918fd2cbf210e588b9456b60679ecc47e/packages/hardware/families/catprinter-v5x.md)

The public protocol description explicitly warns that it is incomplete. Family
evidence is not a claim of testing every V5X model. No Home Assistant implementation
code is incorporated by these PR-preparation changes. MeowPrinter's project
license is unchanged. The MIT reference's notice is retained below for attribution.

### MXW01-catprinter MIT notice

MIT License

Copyright (c) 2021 rbaron

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
