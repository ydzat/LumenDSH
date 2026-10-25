# M4.5 conversation, gesture and icon evidence

## Build and scope

Installed `0.4.26-m45-preview`, version code 33, on the existing Huawei YAL-L21,
Android 10, by signed in-place upgrade. Final APK SHA-256:
`2c0548145bfa8dd2913cbba69e49a7aea5cff730b65a0c0384de5992915a645b`.
Host Java/API 29 compilation, phone-native build and signature verification passed.
The signing certificate is unchanged. No DSH restart, upgrade, credential access,
owner enrollment, audio export, remote configuration or publication occurred.

## Source findings and implementation

- Installed rc.7 API Proxy `prompt` routes `mode: queue` to `agent.followup`, while
  steer is distinct. Character touch reuses SessionController.send and its pending
  journal, same-session selection and no-blind-retry behavior.
- The previous habitat used a single OnClick callback. Android GestureDetector and
  ScaleGestureDetector replace it as one input path, with confirmed single taps,
  multi-pointer cancellation and disabled double-tap quick scale.
- The existing upstream-derived scroll/follow-tail behavior is reused. Both compact
  and expanded views read Conversation.text(); the compact region fades after idle,
  stays tappable, and shows the Expand icon with the text. Expanded chat retains
  selection/copy and text entry, opens at the tail, and uses back/send icons.
- Menu, Expand and voice are icon-only, with accessible names and long-press hints.
  Lucide 0.468.0 assets, hashes, transformations and license are recorded in
  apps/android/DEPENDENCIES.md. No icon runtime library was added.
- Preset generation and its visible editing/weight controls are retired. Existing
  presentation fields remain accepted for file/history compatibility. Legacy weighted
  queues no longer order the chronological compact conversation.

## Device observations

1. F14 input/presentation scope: `HabitatInteractionChecks` exercised the actual
   installed Activity and native recognizers. Four body-region single taps produced
   the expected head/shoulder/waist/feet callback; outside taps, double taps and pinch
   produced no touch callbacks. Pinch enlarged and shrank the existing composition.
   Idle fade, tap-to-reveal, expanded chat, visible composer and selectable history
   passed. The callback was captured during this check, so it is UI/input evidence,
   not model-delivery evidence. Original zoom was restored.
2. The first pinch fixture ended at a 440 px span and did not enlarge. Inspection
   returned a device `config_minScalingSpan` of 435.223 px: the fixture stopped just
   as recognition began. The corrected gesture crosses that threshold and moves
   from 200 to 840 px span and back; assertions passed without weakening them or
   changing production gesture thresholds.
3. F14 real backend scope: a single controlled, tool-free 40-line request was sent
   to the existing selected conversation. While the backend reported running, an
   actual screen tap hit the character's head. Authoritative history contained
   exactly one touch user message and two completed turns. The touch user event
   followed the original turn/end; the second turn contained a real assistant reply.
   No task cancellation, steering, duplicate touch or new session occurred. The
   temporary ADB HTTP forward was removed afterward. User conversations were not
   exported into tracked evidence.
4. F03/F13 scoped layout: final portrait and landscape screens were inspected.
   Character and conversation regions are separate. The full chat settled at the
   actual latest touch reply with input and send icon visible. Screens are saved
   locally as `artifacts/m4/m45-icons-*.png` and `m45-chat-icons-stable.png`.
5. F11 icon scope: `TextInput -e action icon-voice-stop` started actual foreground
   recognition through the microphone icon, observed the stop action, stopped it,
   and observed the start action again. No spoken command or retained recording was
   supplied. This is not a new wake, speaker-model or acoustic quality test.
6. AndroidRuntime inspection showed no crash in the inspected buffer. After testing,
   portrait rotation and existing wake/overlay opt-ins were restored. Foreground
   WakeVoiceService and CompanionOverlayService were observed. The temporary test
   package was removed; owner voiceprint and application data were preserved.

## Demonstration and remaining gate

A 9.9594-second silent device clip is saved locally:
`artifacts/demos/20261025-022049-m45-icons-conversation-4bf6014a.mp4`.
The screen was inspected before recording; local decoded frames show the hidden
conversation region at second 4 and expanded controlled chat at second 9. The clip
shows fade/reveal and icon expansion, not a new backend or acoustic acceptance run.

F03/F11/F14 observations above are scoped results. Full live interruption, fault,
lifecycle and accessibility matrices remain as previously documented. F13 final
visual acceptance is still pending the user's review; M4.5 is not declared closed.

## Foreground height refinement (0.4.27)

Installed code 34, APK SHA-256
`3e4b4e4b167709043747615753a2e6e6d7afe79524bbadf12a7c982cacd58f49`.
Host Java compilation, phone-native signing/build and in-place installation passed.
The conversation region is now an elevated foreground sibling of the scene/layout,
expanding upward toward the scaled feet while retaining its lower control boundary.
The underlying layout reservation preserves the previous character sizing.

Scoped F03/F14 regression: the existing installed-View gesture/fade/expand checks
passed again, including both pinch directions. Portrait and landscape screenshots
were inspected: `artifacts/m4/m45-taller-strip-final.png` and
`artifacts/m4/m45-taller-strip-landscape.png`. The larger portrait region uses the
former gap beneath the character and exposes more of the existing conversation.
This run did not repeat backend queue or acoustic tests. Original zoom/portrait
orientation and enabled wake/overlay services were restored; the test package was
removed. No new video was recorded for this small layout refinement; the preceding
stage demo remains evidence only for 0.4.26. F13 user review remains open.
