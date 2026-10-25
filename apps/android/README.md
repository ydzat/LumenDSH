# Android client

Application name: **LumenDSH**. Silver Wolf / 银狼 is the customizable default
character. The historical Android package ID is retained for upgrade compatibility.
Current version: **0.7.50-voice-handoff**, package `local.sliverwolf.assistant`,
min/target SDK 29. Built and signed using an independent Termux toolchain.
DSH 0.2.1-rc.3 runs separately behind authenticated phone-local HTTP/WebSocket APIs.

The client implements the character-first habitat, expanded chat, real session and
approval handling, speech/owner-gated wake, overlay, notifications, share intake,
schedules, memory/behavior controls and content/native release recovery. See the
[root README](../../README.md) for current scope and limitations. Background acoustic
and lifecycle evidence remains partial; no broad device-support claim is made.

| Owner | Responsibility |
| --- | --- |
| `DshTransport`, `Rc3Protocol`, `SessionController` | Authenticated sessions, decisions, reconnect and model metadata |
| `Conversation`, `HabitatFeed`, `HabitatView` | Actual conversation events, sourced presentation and character scene |
| `SettingsDrawer`, `InteractionSettings` | Grouped settings, independent home/chat input and reply policy |
| `VoiceInput`, `WakeVoiceService`, `OwnerSpeaker` | System speech, local wake and owner matching |
| `DeviceBridge`, `DeviceOperations`, `ArtifactInbox` | Local MCP and bounded authenticated side effects |
| `PetStore`, `AssistantProfile`, `PresentationSettings` | Appearance import and independent configuration |
| `ContentReleases`, `NativeReleases` | Versioned activation, signed APK handoff and recovery |

Follow the [build guide](../../tooling/android-build/README.md),
[dependency provenance](DEPENDENCIES.md), [data boundaries](../../docs/data-and-publication.md)
and [acceptance evidence](../../tests/acceptance.md). The supported external pet ZIP
subset is described in [the format contract](../../docs/design/m4-pet-format.md).
Downloaded models and bundled default character resources are excluded from Git;
their preparation scripts pin origins, hashes and licensing separately.

## Character voice update (0.7.48)

The shared speech owner now supports the paired, phone-local Silver Wolf engine
in addition to Android system speech. The application plays synthesized PCM audio
and preserves existing focus, stop, reply correlation and operation recovery.
Models and native synthesis remain outside the APK. Setup and real-phone evidence:
[local character speech](../../docs/character-voice.md).

## 0.8.0 speech providers

The settings drawer selects system/local/HTTPS synthesis, with model and voice
discovery or manual IDs. See [setup](../../integrations/voice/providers.md) and
[real-device evidence](../../tests/m8-evidence.md). Models remain outside the APK.
