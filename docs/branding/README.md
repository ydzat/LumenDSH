# LumenDSH branding

Selected on 2026-10-25: **Geometric L**, a silver-white monogram with a violet
accent on graphite. The product logo is independent of selectable Agent characters.

Canonical asset: [lumendsh-logo.png](lumendsh-logo.png). Generated with the built-in
image generation tool and selected by the project owner. Both README languages use
this asset. Android uses an identical copy at
`apps/android/res/drawable-nodpi/ic_lumendsh.png`, referenced by the manifest's
`android:icon` and `android:roundIcon`. This is a bitmap icon, not separate adaptive
foreground/background layers. See Android's [application icon attributes](https://developer.android.com/guide/topics/manifest/application-element#icon).

## Device verification — 0.7.47-branding

- Built on Huawei nova 5T / Android 10 using the existing pinned Termux toolchain.
- APK signature verification passed with the existing signing identity. ADB
  in-place installation succeeded; package versionCode is 47.
- System application details visibly show the geometric L icon, LumenDSH name
  and version 0.7.47-branding. Cold launch succeeded and the home screen reported
  connection to local DSH. No DSH runtime upgrade or restart was performed.
- Build source receipts now include PNG resources, including the new icon.
- No functional behavior change or full milestone revalidation is claimed.

APK SHA-256: `796b65c5217cc86e3bc8c9733d047e9b036cf403c0c7dcf01f93778468a07931`.

Local-only outputs (ignored):

- `artifacts/branding/lumendsh-0.7.47.apk`
- `artifacts/demos/branding-app-info-ready.png`
- `artifacts/demos/20261025-175859-lumendsh-branding-aa1ad594.mp4` — silent device capture.

Only source, logo assets and documentation are published; the APK and capture
remain local.
