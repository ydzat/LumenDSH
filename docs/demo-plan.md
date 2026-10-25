# User-facing demo

## Deliverable

A roughly three-minute walkthrough of everyday use, with Chinese captions and
chapter markers. Record the current UI on the real device using controlled demo
content. Keep recordings under ignored `artifacts/demos/`; publication is a
separate step.

## Storyboard

| Chapter | What the viewer sees | Capture |
| --- | --- | --- |
| Meet your companion | Animated habitat, pinch-to-zoom, character touch and its real reply | Agent-operated UI |
| Talk naturally | Owner says the wake phrase, asks a question, hears the reply, then asks a follow-up | Owner speaks; agent operates recording |
| Continue in text | Expand the transcript, type a follow-up, copy a reply, return home | Agent-operated UI |
| Keep it nearby | Floating character over a neutral screen; owner wakes it without tapping | Owner speaks; agent operates recording |
| Remember something | Save an explicitly fictional demo preference and retrieve it using different wording | Agent-operated UI and real backend |
| Act at the right time | Create a short demo reminder, show delivery and its result; show autonomous activity controls | Agent-operated UI and real backend |
| Share and personalize | Share neutral text into the assistant; preview appearance and voice settings | Agent-operated UI |

Allow more time for real replies when needed. Cut idle waits with an explicit time
jump. Use captions to explain actions; do not present captions or scripted overlays
as actual recognition, memory retrieval or Agent output. Restore settings and remove
only records created for this demonstration.

## Existing footage

The directory contains milestone recordings from multiple UI versions. Preserve
their paths because development evidence links to them. Treat them as development
archives rather than the main public walkthrough.

Candidates for frame-by-frame review:

- `20261025-152949-m7-semantic-habitat-ea8c325c.mp4`: habitat/memory context.
- `20261025-142630-m7-companion-policy-bbbb366a.mp4`: autonomous activity controls.
- `20261025-002050-m45-schedule-result-0d265d55.mp4`: reminder result.
- `20261025-001139-m45-share-review-0302b7f4.mp4`: share flow.

These are candidates selected by filename, not footage approved for publication.
Review visible content and UI consistency before reuse. Prefer a fresh coherent
walkthrough over mixing old interfaces. Build, repair, model configuration and
diagnostic recordings belong in developer documentation.

## Owner participation and audio

The owner-protected wake-word path needs the enrolled owner to speak. Reuse the
existing enrollment. Prepare two short prompts and one follow-up; no new voiceprint
enrollment is part of this recording task. Device unlocks or system identity prompts,
if encountered, also require the owner.

The existing `record-demo.py` produces silent video. For an audible walkthrough,
prepare and verify a separate audio capture route for the owner's speech and phone
playback, then synchronize it with the screen recording. Owner participation alone
does not make the screen recording contain audio. A captioned silent walkthrough is
also possible, but does not demonstrate the sound of the interaction.

## Before capture

- Verify the live ADB connection and inspect the screen.
- Inspect the selected conversation and use only controlled demo content.
- Frame out personal notifications, credentials and unrelated content.
- Resolve the source/installed branding difference: the documentation handoff renamed
  source labels to LumenDSH, while the installed 0.7.46 build predates those edits.
- Capture the actual results before describing a chapter as complete.

## Owner-assisted capture: 2026-10-25

- Enabled and verified the existing auto-send/read-reply option before the second take.
- Captured two foreground voice turns: introduction and a follow-up about planning a day.
- Captured owner wake-up from the launcher, a spoken request and a return to standby.
  The owner confirmed both sequences completed. Screen inspection confirmed replies
  and the final standby state.
- Recorded phone video with scrcpy 5.0.1 and separate 48 kHz mono audio from the PC
  microphone. Both edited previews contain AAC audio and pass full-file decoding.
  Original audio has no samples at the digital clipping ceiling. Subjective listening
  quality and fine audiovisual synchronization remain pending playback review.
- Discard the first take from the edit: auto-send was disabled. Retain it locally as
  a setup take. Existing owner enrollment was reused.

Local previews (ignored, not published):

- `artifacts/demos/user-demo-voice-preview.mp4`: 1:45, status bar cropped.
- `artifacts/demos/user-demo-wake-preview.mp4`: 1:00, launcher weather/location area cropped.
- `artifacts/demos/user-demo-wake-short-v2.mp4`: 0:28, selects the complete successful
  wake attempt after an earlier recognition failure (Android code 7). Wake phrase,
  command, response waiting and return to standby retain normal speed. The reply
  opens at normal speed, uses 3x in the middle and closes at normal speed, with
  matching audio tempo and speed labels. Introductory and trailing idle footage is removed.
  The failed attempt remains in the original preview. Full-file decoding passed.
- `artifacts/demos/user-demo-voice-short.mp4`: 0:44, shortened foreground edit.
  Questions and reply openings remain at normal speed; the first reply continues
  at 3x, and the second uses 4x before returning to normal speed for its closing
  question. Waiting segments use 5x. On-screen labels identify speed changes.
  Audio follows the same cuts with pitch-preserving tempo changes. Inter-turn idle
  time and the trailing hold are trimmed; the final ready state remains visible.
  The original previews and raw captures are retained. Full-file decoding passed.

These previews use continuous excerpts, with microphone high-pass filtering, gain
and peak limiting. Raw video and WAV tracks are retained for synchronization and
editing. The foreground response is long and the UI still exposes development-style
English status text; account for these in the final user-facing edit.

## Completed local edit: 2026-10-25

- `artifacts/demos/user-demo-complete.mp4`: 2:34, the complete everyday-use tour.
- `artifacts/demos/user-demo-features-short.mp4`: 1:22, the new non-voice chapters.
- The two owner-approved voice edits retain their cuts and speed changes in the
  complete tour. Those chapters contain microphone audio; the additional chapters
  are silent with Chinese captions. No music or synthesized narration was added.
- New footage covers animated habitat, real two-finger zoom, character touch and
  its DSH reply, expanded conversation and text selection/copy, memory write and
  recall, a one-shot reminder and its reply, autonomous activity controls, incoming
  text sharing and its reply, and appearance/voice configuration options.
- Memory recall uses a labeled, cropped real-device screenshot of the actual
  result. Other result close-ups use real video. Idle waits are cut; the reminder
  result explicitly labels the skipped wait. These are edited demonstrations,
  not continuous acceptance recordings.
- Configuration footage previews options without saving a replacement profile.
  Autonomous activity footage shows controls, not a completed autonomous run.
  Reminder footage shows delivery into DSH and the reply, not a notification banner.
- The installed build still uses the Silver Wolf label in settings. It was not
  rebuilt or replaced for filming; captions identify the product as LumenDSH.

Approximate chapters in the complete tour:

| Start | Chapter |
| --- | --- |
| 0:00 | Animated character, zoom and touch |
| 0:22 | Two foreground voice turns |
| 1:06 | Owner wake phrase and background voice reply |
| 1:34 | Conversation view and copy |
| 1:50 | Memory write and semantic recall |
| 2:01 | Reminder and autonomous activity controls |
| 2:15 | Shared text and configuration options |

The fictional memory was written and retrieved through the installed Mnemon tools,
then soft-deleted by its exact ID. The demonstration reminder was canceled after
delivery; the pre-existing scheduled entry was retained. The original selected
conversation was restored. The temporary gesture/text instrumentation package was
uninstalled. Controlled demo conversations and original footage remain available
locally for review.

Both final MP4 files passed full-file decoding. Contact sheets were visually
reviewed for framing, readable results and unrelated content. The edit manifest is
`artifacts/demos/user-demo-features-edit.json`; the local reproduction script is
`.local/edit-feature-tour.py`. Recordings, intermediate clips and the script remain
ignored. No Git commit, push or publication was performed.

Status: recording and local editing complete; final viewer review and publication
remain separate steps.
