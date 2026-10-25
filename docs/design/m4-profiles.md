# Local appearance and voice compositions

The M4 local profile editor combines an installed appearance version with settings
for the initialized Android TTS engine. It does not install speech engines, clone
voices, change the DSH persona/model, or change microphone/auto-send authorization.
The recognition language is forwarded to the existing Android recognizer; recognizer
availability and acoustic accuracy remain device/provider-dependent.

`AssistantProfile` validates the generated records described by
[voice schema](../schemas/voice-profile-v1.schema.json) and
[composition schema](../schemas/assistant-profile-v1.schema.json).
These are local records, not a new external ZIP importer. The editor offers zh-CN
and en-US, rates 0.75/1/1.25, and pitches 0.85/1/1.15. The validator accepts rates
0.5..1.5 and pitches 0.75..1.25. These bounds are project choices.

Appearance versions are the imported archive hash or `builtin-v1`. Voice versions
are SHA-256 of the normalized voice JSON emitted by this Android implementation.
The composition's filename is SHA-256 of its exact UTF-8 JSON bytes. This is an
internal encoding, not a cross-language canonical-JSON signature format.

Saving creates an immutable file in app-private `assistant-profiles/`. Activation
loads/verifies the appearance, checks the initialized TTS engine, language availability,
rate and pitch support, then atomically replaces the existing appearance pointer with
one record referencing both appearance and composition. Pending microphone/speech use
blocks activation. Speech requests are held across the pointer commit; settings are
applied by the shared TTS owner before actual playback. Failed validation or commit
keeps the previous active composition. A valid but unactivated candidate may remain
in the saved list. An appearance-only selection preserves the current voice settings.

Startup restores the referenced composition and waits for TTS initialization before
enabling profile-dependent playback. An unavailable or changed provider produces an
explicit restoration error; it is not silently replaced by another provider. The
legacy appearance-only pointer remains readable. The first version provides creation
and selection; external composition import, behavior profiles and agent-published
configuration patches remain follow-ups.

Android references: [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
for engine/language/rate/pitch APIs and
[AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)
for the activation pointer. Evidence is in [M4 profiles](../../tests/m4-profile-evidence.md).


Menu -> Resource management is implemented. It lists immutable
saved profiles and imported appearances with explicit activation and removal actions.
Removing a profile cannot remove the active profile. Removing an appearance cannot
remove the current appearance or one referenced by any saved profile; delete unused
profiles first. Removal operates only inside the generated app-private store and
never deletes the source ZIP chosen from Documents/Downloads. The removal/reference checks and actual UI flows passed the
[consolidated M4 gate](../../tests/m4-stage-evidence.md).


## External composition import (0.7.45)

The combination settings page accepts a user-selected `assistant-profile` JSON
through Android's document picker. The existing parser validates exact component
versions, voice digest and supported fields; input is strict UTF-8 and at most
8 KiB. The appearance must already be installed at its declared hash (or builtin).
The preview displays appearance and voice settings before reusing the existing
activation transaction and TTS validation. Cancellation leaves the active pointer
unchanged. No new ZIP format, engine installer or script loader is introduced.

The actual phone build and installation passed. Android ProfileChecks exercised
exact import references, malformed UTF-8, oversized input, unknown fields and the
existing activation/recovery checks. The real system document picker returned a controlled JSON into the preview. The preview was cancelled and the existing composition retained; atomic activation is covered by the reused Android transaction checks.
