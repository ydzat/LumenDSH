# Silver Wolf default appearance evidence

Version: 0.4.5-m4-preview (12), 2026-10-24.

## Source inspection and implementation

User-selected fan-art resource is pinned and attributed in
[dependencies](../apps/android/DEPENDENCIES.md#bundled-silver-wolf-artwork).
The original lossless WebP header reports 1536x1872, matching the nine activity
rows already rendered by PetSprite. The bundled loader validates those dimensions;
external ZIP import remains v2-only. Original upstream files are unchanged and
ignored in Git. The phone build downloads and validates all three fixed hashes.
Built-in profile references now resolve to Silver Wolf without rewriting profiles,
voice settings, imported selections, or backend conversation state.

## Verification

- Host Java compilation against API 29 and pinned dependencies passed.
- Termux native build passed, including all asset hashes and APK signature verification.
- APK SHA-256: f613a41f7520acc5935a2f10e5c1b89fc5cfe293c8bafc88a0bd23e8bbc1cac2.
- Existing signing certificate retained; adb install -r succeeded on YAL-L21 Android 10.
- Cold launch displayed the requested Silver Wolf and restored the controlled
  completed conversation (M4_GATE_DONE), without a new submission.
- Eight-second silent real-device clip decoded successfully at 540x1168;
  inspected frames at one and three seconds show differing character poses and
  ambient presentation. No synthetic session events were injected.
- Local clip: artifacts/demos/20261024-233534-silver-wolf-default-480bc024.mp4.

Scope: P01 rendering/default selection and UI01 appearance smoke check. This is
not a rerun of every semantic animation state, P02/P03 import/profile tests, or
closure of the broader M4 visual acceptance review. Voice assets are not changed.

## Calm playback correction (0.4.6, code 13)

User reported excessive animation speed. Inspection established a 1100 ms idle
source loop replayed continuously. Presentation now doubles frame durations and
adds a 4000 ms idle rest, without delaying state transitions. Source timing data
and image assets are unchanged. The Android reference review and reuse candidates
are recorded in docs/research.md; no reference application code was copied in this fix.

On-device check-pets.sh passed PET_IMPORT_CHECKS_PASS and
PROFILE_AND_ATTENTION_CHECKS_PASS, including new half-speed boundary/idle-rest
assertions and the actual Canvas frame/pause-resume check updated for half speed.
Phone compilation/signature verification and in-place installation succeeded.
APK SHA-256: ae3b41df1f76f677a1d4583de38cc5363ee663c956b9c7c580274fb0025c13c1.
Controlled completed conversation restored. Silent ten-second clip:
artifacts/demos/20261024-233900-silver-wolf-calm-936e7021.mp4.
Decoded duration/resolution verified (10 s, 540x1168), frames at 1 and 5 seconds
inspected. Subjective pacing still depends on user review; the code/device checks
establish timing and rendering, not user satisfaction.

## Current habitat reference adaptation (0.4.7, code 14)

Adapted the pinned Android reference's breathing, tap rebound and long-press pulse
into HabitatView Canvas rendering. Full MIT notice and local changes are recorded
in the APK notices and dependencies. Host Java compile and native phone build passed.
APK SHA-256: 2fa9ca5a5ff37e1ecbe04ca37a5b645cd90a3039cf4fda0fdea9e8baab2ba2fb.
In-place installation and cold launch succeeded with the controlled conversation.
Actual character taps were exercised during a ten-second silent recording:
artifacts/demos/20261024-234109-character-touch-a0992edb.mp4.
The video decoded at 540x1168 and its character scene frame was inspected.
A 700 ms press on the character reached the existing Android speech recognizer;
it reported code 7 (no recognized match) during the silent check. Stop voice was
then invoked. This verifies entry routing, not a successful speech recognition
round trip. No new voice-answer acceptance is claimed. Background hit testing and
reduced-motion behavior were inspected in code, not separately device-tested here.
