# M4.5 unboxed narration correction

## Scope and artifact

The user rejected the 0.4.24 foreground capsule during F13 review. Version
`0.4.25-m45-preview` (code 32) moves ordinary foreground text into a separate
region below the character and above the voice controls. This supersedes the
default floating-card presentation described in the previous closure evidence.

APK SHA-256: `428a425f3ba2a01b977e990604a1842b78a74b3aebf11d040acf66394ffb386c`.

## Implementation and verification

- Host Java compilation against API 29 passed. The existing phone-native build,
  signing verification and in-place installation succeeded on Huawei YAL-L21,
  Android 10. No dependency or DSH deployment changes were made.
- F03/F13 scoped device inspection: portrait near/far compositions show unboxed
  text below the entire character, separate from the controls. Landscape places
  text in the right column and the character in the left. Screens are under
  ignored `artifacts/m4/m45-unboxed-*.png`.
- An existing controlled DSH reply was used; no new task or conversation was
  submitted for this check. A 10.0617-second silent real-device demonstration is
  saved as `artifacts/demos/20261025-024439-m45-unboxed-narration-343b1e83.mp4`.
  Locally decoded frames at 2 and 8 seconds show progressively revealed text,
  followed by scrolling within the lower region, without obscuring the character.
  The inspected frames contain only the controlled layout-check reply.
- The implementation preserves the existing queue, source labels and explicit
  reading mode. Reveal advances by Unicode code point; appended text preserves
  the visible prefix. Hidden/unfocused views pause and reduced motion skips reveal.
  These code paths were inspected; this run did not repeat the complete live
  streaming, interruption, accessibility or long-duration matrices.
- The original portrait rotation and far composition were restored. Existing wake
  standby was re-enabled after the client update. Owner enrollment was preserved;
  no voice samples were recorded or exported by this verification.

F13 remains pending user review of this revised result. This scoped correction
does not close the other outstanding M4.5 acoustic and fault-matrix gates.
