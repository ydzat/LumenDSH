# Settings drawer and interaction policy

Status: implemented for 0.7.49; device evidence is recorded separately below.

## Approved scope

Replace the 21-item flat menu with a modal side drawer. Group daily destinations
(conversations, tasks/inbox, memory), preferences (interaction/sound, character/space,
behavior, notifications/background), and connection/system maintenance. Keep the
character habitat as the primary surface. Show current values and selection marks.

The panel uses Android platform Dialog/Window, ScrollView, Switch, RadioGroup,
EditText, SeekBar, DatePickerDialog and TimePickerDialog. This preserves the verified
API-29 phone build without importing the AndroidX resource/dependency graph. It is
not an AndroidX NavigationView implementation. Window modality and platform widgets
own focus, outside-tap dismissal, accessibility and control gestures; the panel does
not install a competing character pinch/drag recognizer.

References inspected:
- https://developer.android.com/develop/ui/views/components/settings
- https://github.com/material-components/material-components-android/blob/master/docs/components/NavigationDrawer.md
- https://developer.android.com/reference/android/app/Dialog
- https://m1.material.io/patterns/settings.html
- https://www.nngroup.com/articles/progressive-disclosure/

## Independent interaction settings

`interaction` preferences are independent of immutable appearance/voice compositions.
On first migration, home voice automatically sends at end of recognition; chat voice
inherits the legacy auto-send preference. Home replies default to voice plus text;
chat replies follow the input medium. Existing background wake consent is retained;
foreground keyword mode defaults off. The obsolete combined auto-send/read toggle is
removed from navigation. Input recognition language is independent of TTS language.

Both surfaces offer voice/text/follow-input reply policies. Correlated request IDs
select completed replies; changing sessions or explicitly stopping voice cancels
pending playback. Typed messages and character touches use the same policy routing.
No uncertain request is automatically retried.

Home recognition never writes the chat draft. Optional confirmation opens at the
bottom of the current page. Failed sends remain editable there. Chat dictation keeps
its own draft. Existing nonempty chat drafts are never overwritten by recognition.

## One wake and microphone owner

Foreground keyword mode reuses WakeVoiceService and WakeDetector, including the
existing owner voiceprint requirement, model and learning gates. It does not add a
second keyword engine or speaker model. Foreground recognized text is delivered to
the Activity's existing same-session submission path. Background recognition keeps
its existing service submission/reply path. The phrase remains `嗨，<Agent name>`;
the current keyword vocabulary supports Chinese names.

The drawer, dialogs, transcript, enrollment and microphone handoff pause home keyword
capture. Manual recognition waits for the existing detector to release the microphone.
Playback suspends standby capture. Notification Stop disables both foreground and
background keyword settings. Foreground/background consent and overlay visibility
remain separate preferences. Real owner/noise acceptance is not established by UI
instrumentation.

## Configuration controls

- Current session, appearance, voice and saved preset use single-choice controls.
- Voice edits still validate and atomically activate existing immutable compositions.
- Saved presets can capture the currently active appearance and voice without a
  duplicate profile editor. Import, deletion checks and recovery remain available.
- Name has one editable source. The zoom slider and pinch gesture share the same
  persisted value; resource base scale remains an advanced setting. Pacing/lifetime
  use sliders; exact values and
  configuration-file history remain accessible in the advanced presentation form.
- Autonomous behavior retains backend validation and expected-revision checks.
  Expression/activity types are primary; windows use time pickers; numeric budgets
  are secondary. Unsaved edits remain in the local form draft across child pages.
- Scheduled tasks use a date/time picker and once/24-hour/custom interval choice.
  The existing elapsed interval semantics are retained, not relabeled as calendar
  recurrence. Memory reflection remains a task template with its daily minimum.
- Notification and overlay toggles display prerequisites and keep Android permission
  grants separate from the requested preference.

## Verification

See `tests/settings-evidence.md` for the actual build, installation, UI checks and
remaining acoustic checks. Historical M4.5 acceptance waivers remain unchanged.
