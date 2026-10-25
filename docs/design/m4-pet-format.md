# M4 Sprite Format Discovery

Status: the pinned v2 importer and Android sprite renderer are implemented in
0.4.1-m4-preview. See [device evidence](../../tests/m4-pet-evidence.md) for the exercised
PNG/WebP, rejection, state-rendering and UI restoration scope.

## Inspected upstream

Reference: `ysyyhhh/dsh-pet` revision
`a17346b9c41efb4220f387216008928bba652e0e`.

- [PetContract.ts](https://github.com/ysyyhhh/dsh-pet/blob/a17346b9c41efb4220f387216008928bba652e0e/src/renderer/codex-pet/PetContract.ts)
  fixes cells at 192x208 pixels and eight columns. V1 has nine rows; V2 has eleven.
  The module explicitly describes an externally observed convention, not an official
  OpenAI format specification.
- [PetLoader.ts](https://github.com/ysyyhhh/dsh-pet/blob/a17346b9c41efb4220f387216008928bba652e0e/src/renderer/codex-pet/PetLoader.ts)
  reads `id`, optional `displayName`/`description`, `spriteVersionNumber`, and
  `spritesheetPath`. It requires the fixed atlas width and a minimum height.
- [codex-import.ts](https://github.com/ysyyhhh/dsh-pet/blob/a17346b9c41efb4220f387216008928bba652e0e/src/codex-import.ts)
  locates `pet.json` inside a ZIP, selects the referenced sheet, and validates through
  the loader. Its 80 MiB unpacked limit is an upstream policy, not this client's limit.
- [LICENSE](https://github.com/ysyyhhh/dsh-pet/blob/a17346b9c41efb4220f387216008928bba652e0e/LICENSE)
  is MIT. The row timings are adapted into `PetSprite.java` with the full notice in
  the APK. The initial importer used no upstream character artwork. Artwork rights require
  separate provenance; the repository license alone is not blanket asset authorization.

## Initial implementation choice

Start with the pinned V2 layout only: a 1536x2288 atlas, `pet.json`, and the referenced
PNG/WebP sheet. Reject unsupported sprite versions explicitly. Preserve the original
manifest as provenance and create a normalized appearance-only record. Do not import
optional behavior/configuration files or execute package content.

The nine activity rows are idle, running-right, running-left, waving, jumping, failed,
waiting, running, and review; the final two rows hold look directions. Initial Android
rendering needs activity rows, not desktop pointer tracking. Frame counts/timings must
follow the pinned contract and retain its MIT notice if ported into code.

Proposed semantic mapping: idle -> idle, listening -> waiting, thinking -> review,
working -> running, speaking -> waving, waiting -> waiting, error -> failed. These
are design mappings, not evidence of lip sync. No mouth tracking is claimed.

The internal package contract remains authoritative for path rejection, declared
files/digests, expanded-size bounds, inert staging, and atomic activation. Reject unsafe
archive entries rather than silently ignoring them. Select exact byte/pixel limits
when implementing against the existing 8 MiB artifact handoff and API 29 BitmapFactory.
Test with original redistributable diagnostic art before importing character artwork.

## Implemented import boundary

Choose **Menu -> Character appearance -> Import ZIP** through Android's document
picker. No broad storage permission is requested. The app copies only the selected
stream, checks it off the UI thread, and offers an explicit preview/activation step.
The built-in appearance can be selected again without changing conversation or voice.

The accepted ZIP32 subset contains exactly one `pet.json`, its relative PNG/WebP sheet,
optional directory entries, and optional README/LICENSE/LICENCE/NOTICE `.txt`/`.md`
files. A single enclosing folder is supported. Other payloads are rejected rather
than executed or silently installed. Paths must be relative, traversal-free and have
no backslashes, colon or control characters; duplicate case-folded paths are rejected.
Symlinks, special files, encrypted ZIP and multipart/ZIP64 are unsupported. The bounded
central-directory inspection supplements Android Java ZipFile's missing Unix-mode API;
file decompression itself uses ZipFile and verifies CRC and actual byte count.

Limits: 8 MiB compressed input, 16 MiB declared/actual expanded data, 32 entries,
64 KiB manifest or individual notice, exact 1536x2288 decoded PNG/WebP. Bounds are
checked before bitmap allocation. Up to eight immutable imported versions are retained;
this preview does not yet provide version deletion/collection UI. Reimporting an
identical archive reuses the validated stored version.

Storage uses app-private `appearances/<archive-sha256>/` with fixed generated filenames.
Archive paths never become extraction destinations. The original `pet.json` and notices
are retained. [appearance-v1.schema.json](../schemas/appearance-v1.schema.json) describes
the normalized generated record; native code validates input, payload hashes and active
references. This is not a general JSON Schema engine or a native-package ZIP importer.
The source archive hash provides version identity, not publisher authentication.

Staging finishes before an explicit activation writes an Android AtomicFile pointer.
Failed imports/validation do not change the pointer or active bitmap. Startup verifies
saved payload hashes before decoding; a damaged selection is reported and the built-in
placeholder remains available. Full crash/recovery orchestration remains M5 work.

## Renderer behavior

The importer detects nontransparent pixels in each activity row. A missing row falls
back to the imported idle row, then the built-in vector. Nine activity rows are retained;
seven semantic state mappings and upstream frame durations drive display. Look-direction
rows are preserved in the sheet but not consumed. There is no lip sync or gaze tracking.

Canvas draws the source cell in the existing character layer, between background traces
and foreground information. Near/far composition remains independent of the skin.
Imported animation pauses while reading, behind a modal, or when hidden, and resumes
without advancing across the hidden interval. Android's disabled-animator setting is
respected by the imported sprite. The static built-in placeholder is unchanged.

## Original fixtures and provenance

`tests/make-pet-fixtures.py` generates a geometric robot sheet and malformed ZIPs using
only Python's standard library. This diagnostic art is CC0-1.0 and uses no third-party
character likeness or voice. It proves format/renderer behavior, not compatibility with
all external packages or artistic completion of the intended Silver Wolf character.
Generated archives stay in ignored `artifacts/pet-fixtures/`. A valid sample is left at
`Download/SliverWolfExamples/circuit-companion-v2.zip` on the test phone.

## Default appearance update

Version 0.4.5 replaces the original built-in placeholder with the user-selected
Silver Wolf v1 activity atlas. See [provenance and license](../../apps/android/DEPENDENCIES.md#bundled-silver-wolf-artwork).
The placeholder remains an error fallback; imported v2 appearances remain selectable.

## Calm playback policy (0.4.6)

Activity animation plays at half the source timing speed. Idle runs for 2.2 seconds,
then rests on frame zero for four seconds. State changes start the new row immediately;
pause/resume does not catch up hidden time. This presentation policy applies to the
built-in and imported atlases. Source timing tables and assets remain unchanged.
See [reference review](../research.md#local-android-reference-review-and-reuse-plan).
