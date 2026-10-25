# M4 Pet Import and Animation Evidence

Date: 2026-10-24. Installed: 0.4.1-m4-preview, versionCode 8, Huawei YAL-L21 Android 10.
M4 remains partial: this increment covers pinned appearance import and rendering,
not combined profiles, persona plugins or the remaining attention scheduler.

## Build identity

Compiled/package-signed with the existing phone-native toolchain and installed using
`adb install -r` over the user-connected USB device. Package manager confirmed code 8.
No DSH restart, upgrade or configuration change occurred.

- APK SHA-256: `c3f91c3b1bb7c52db8ed3f60a68b618da9c1e9aa2fd8e27fbb5f46ead2f05de1`.
- Certificate SHA-256: `4c54026b6fae7deeaa00371911f287b187dd5a583048e365bccb3cd2050fdf35`.
- Host compilation against the pinned API 29 stubs also passed. No dependency upgrade.

## Actual Android runtime checks

Generated original CC0 geometric robot fixtures with `tests/make-pet-fixtures.py`.
Ran `tooling/android-build/check-pets.sh` on the phone using production `PetStore` and
`PetSprite` code, actual ZIP files, Android BitmapFactory, Canvas and AtomicFile.
The script returned `PET_IMPORT_CHECKS_PASS`.

Verified:

- Valid and nested PNG packs decode at the fixed dimensions; import alone does not activate.
- A real Android-encoded WebP sheet imports and decodes successfully.
- Pixel comparisons of the production Canvas output match the appropriate sheet row for
  idle, listening, thinking, working, speaking, waiting and error. Actual animation advances
  to its second cell; focus pause/resume does not catch up across the hidden interval.
- Pinned idle frame boundaries/loop and missing-wave-to-idle fallback pass.
- Traversal, absolute path, duplicate case-folded name, executable payload, unsupported
  version, referenced path escape, invalid image, wrong dimensions, oversized expansion,
  Unix symlink and truncated ZIP are rejected. Restoring after each rejection returns
  the original active version. No path-escape output exists.
- Input exceeding 8 MiB is rejected; tampering with a saved atlas prevents activation.
- Explicit built-in activation persists. These checks use dedicated test storage, not
  the user's installed appearance directory. Synthetic archive inputs are stated here;
  Android execution and decoded/rendered outputs are real, not a service mock.

## Installed UI checks

Used the actual Android document picker to select the valid sample in a dedicated
Downloads subdirectory. The preview identified Circuit Companion; explicit activation
rendered it in the existing habitat. A second picker selection with unsupported v1
showed the rejection message while retaining the imported character.

Force-stopped and relaunched only the assistant client. The imported selection restored
and the appearance dialog still identified Circuit Companion. The native transcript's
session identifier matched the value recorded before the failure/restart; no replacement
conversation was created. Space/character compositions, animated idle rendering and
reading pause were exercised on screen. The geometric robot is diagnostic artwork,
not a new product character or evidence of voice/lip synchronization.

After recording, restored the original built-in appearance and space composition.
Removed only the deliberately invalid test ZIP from Downloads. The valid sample remains
at `Download/SliverWolfExamples/circuit-companion-v2.zip` for user experimentation.
No private pairing token, personal conversation or reference audio was captured.

## Demonstration and scope

Silent actual-device video:
`artifacts/demos/20261024-224553-m4-pet-animation-b4e06d76.mp4`.
Ten-second recording limit; decoded duration 9 seconds at 540x1168. Inspected a decoded
video frame and the controlled screen. The clip shows sprite animation, near/far
composition and reading interaction. It is not acoustic or background-execution evidence.

P01 and P02: verified within the pinned v2/original-fixture and rejection scope above.
No universal external-pet compatibility claim; v1, video packs and look-direction rows
are outside this implementation. Live seven-state transition coverage beyond the
renderer-level checks is still limited to the exercised UI conditions.
P03 was NOT RUN in this appearance-only increment; see the subsequent
[combined-profile evidence](m4-profile-evidence.md) for the current result.
UI01: remains PARTIAL as recorded in the previous increment.

## References

- [Pinned geometry/timings](https://github.com/ysyyhhh/dsh-pet/blob/a17346b9c41efb4220f387216008928bba652e0e/src/renderer/codex-pet/PetContract.ts).
- [Android document selection](https://developer.android.com/training/data-storage/shared/documents-files).
- [Bitmap bounds-first decoding](https://developer.android.com/reference/android/graphics/BitmapFactory.Options#inJustDecodeBounds).
- [AtomicFile commit/recovery API](https://developer.android.com/reference/android/util/AtomicFile).
- [PKWARE ZIP specification, central directory](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT), section 4.3.12.
