# M4 Attention Choreography Review 05

Status: design direction accepted by the user on 2026-10-24. Native baseline implemented; scoped device acceptance is recorded in [M4 stage evidence](../../tests/m4-stage-evidence.md).
Open [the prototype](m4-attention-preview.html); select a scenario or play the 25-second guided sequence.
The existing two compositions and original placeholder sprite remain available.

## Presentation policy

Screen occupancy and interaction attention are separate inputs. Geometric clearance
alone does not authorize another interruption. This prototype uses explicit interaction
states, not gaze tracking or inferred private mental states.

| Interaction | Presentation policy |
| --- | --- |
| Background work | Slow, bounded typewriter behind the character; foreground arrivals use the existing queue |
| User speaking | Freeze background text at its current position, dim ambient detail, defer all foreground arrivals; show a quiet pending count |
| Agent speaking | Give the character and one stable speech bubble priority; queue unrelated content |
| Reading memory | Keep the selected fragment visible until explicit exit; pause ambient motion and suppress incoming foreground replacements |
| Reviewing approval | Keep the decision actionable and persistent; return to ambient presentation after explicit selection |

The pending indicator can explicitly end the current focus and reveal queued content.
The voice-area control ends listening, speaking or reading in the demonstration.
Changing the composition recalculates available space while retaining the selected
content kind. A preempted item loses temporary focus priority when requeued.
Pausing visual traces never means pausing the underlying DSH task.

The guided sequence illustrates work, listening, response, reading and approval.
It ends on an approval awaiting a click; it never silently approves the example.
All dialogue, memory and work content is fictional, visibly labeled demonstration data.
No audio, microphone, real approvals, network connection or agent reasoning is involved.

## Design basis

- [Models of Attention in Human-Computer Interaction](https://erichorvitz.com/Bayesian_AUI.pdf): weigh information value against interruption cost and use interaction signals.
- [Calm Technology: Periphery](https://calmtech.com/gallery/periphery/): allow information to remain at the edge of attention until it merits focus.

The concrete five-state policy, timings and one foreground slot are project design
choices, not experimentally established universal attention limits.

## Verification scope

Node syntax check and headless Edge interaction checks cover foreground ownership,
listening deferral, reading retention under incoming approvals, reserved-region
clearance, and background text pause/resume at desktop and narrow viewports.
The rendered desktop reading state was visually inspected. These are browser prototype
checks only. UI01 and P01-P03 remain subject to their actual Android acceptance gates.
The interactive sequence remains the reference for reviewing design rhythm. The first
native slice and its separate real-device demo are recorded below.

## Native implementation slice

`HabitatView.java` now provides the primary native scene and persistent near/far
composition. `MainActivity` retains its existing session controller and decision
handlers, presents the transcript as a secondary page, and forwards actual recognition,
TTS playback, session state and completed assistant messages to the scene. A bounded
six-event tool/turn trace types behind the original vector placeholder. It contains
only event names/status, not private chain-of-thought or fabricated search results.

Listening hides incoming reply surfaces; reading pins the current reply excerpt while
new replies wait. The excerpt can be dismissed, and full content remains in the
transcript. Foreground text reserves space below the character face. Pending approvals
are persistent native controls, disabled while listening; tapping opens the existing
server-owned decision form. This slice does not implement a general multi-source
memory/notification queue or persona-generated asides. Imported v2 sprite animation
is covered by the subsequent [appearance increment](m4-pet-format.md).

The shared application theme is dark; voice opt-in and the composition persist in
preferences. Playback activity comes from the existing TTS owner, without creating
another audio engine. Background rendering pauses when its view is not visible.

The first native preview is installed and scoped device checks passed; M4 remains partial.
Validation and installation status: [M4 implementation evidence](../../tests/m4-evidence.md).

## Native reply queue increment

Completed assistant replies are keyed by actual server sequence. Initial history
shows only its latest reply; subsequent events enter an eight-item presentation
backlog. Reconciliation cannot re-enqueue the same sequence. Reading pins the current
item; incoming replies wait behind it. The pending control explicitly advances to
the next item. Unpinned foreground replies expire after 12 seconds of visible,
unblocked time. Hidden pages, lost window focus, listening, speech, decisions and
insufficient stage clearance pause that timer. These timings are design choices.

Listening and decisions suppress the reply bubble. During actual TTS playback the
bubble shows the text owned by the speech operation, including previews and device
tool speech; the reply queue stays paused underneath. End/stop restores queued text.
Overflow is summarized and links to the complete transcript once the small backlog
is consumed. Session changes clear presentation state; within a client lifetime,
reconnect does not reintroduce a dismissed server sequence. Restart may present the
latest historical reply again. There is no fabricated memory or internal reasoning
feed, and no claim that the bounded presentation queue stores the conversation.

[Profile and attention evidence](../../tests/m4-profile-evidence.md) records device
checks independently of prototype checks. UI01 remains partial.


## Native event presentation

`HabitatFeed` projects only inspected rc.7 `turn/start`, `tool/call`, `tool/result`
and `turn/end` records. The current turn retains up to five public activity/result
excerpts, each bounded before presentation. Result text comes from ordinary text or
nested tool-result text blocks; reasoning blocks are excluded. Tool names containing
search/browse/fetch select a retrieval color while the call is pending. This is a
presentation heuristic, not proof of successful search or provider connectivity.

Native layers now include local date/time and an activity heading at the edge,
wrapped typewriter excerpts behind the character, and the existing foreground
speech/reading/decision controls. Background excerpts use a bounded region below
the heading and above foreground reservations, scroll to their latest wrapped rows,
and fade after 18..22 seconds of unblocked visible time. Reading, speech, listening,
decisions and loss of focus pause their progress. Reduced animation settings render
text without typewriter motion. State colors distinguish disconnected, failed,
waiting-for-decision, retrieval-tool, running and idle states. Connection loss means
only the app-to-DSH path is disconnected; provider health remains unknown.

The batch also updates secondary controls/transcript hierarchy and makes long tool
approval details scrollable. Public tool excerpts can contain user task data just
like the transcript; they are not included in Git fixtures or demonstration captures.
Memory, diary, preset and proactive-expression sources remain unconnected until their
separate backend integration has actual provenance and events. No decorative fake
memory or fabricated thought text is shipped.

Stage validation and corrected compact layouts: [M4 baseline evidence](../../tests/m4-stage-evidence.md).


## Review 05 fidelity correction

User feedback reopened M4 visual acceptance after 0.4.3. The prior native grid was
opaque and static, the built-in character had no breathing motion, particles/aura/
beam/listening ripple were absent, and large filled buttons overrode the prototype's
quiet hierarchy. Functional layout/decision tests did not establish visual fidelity.

The correction maps original prototype values to native drawing: four-second breathing,
seven-second particle drift, 700 ms composition interpolation, 800 ms focus dimming,
soft radial aura, translucent beam, low-opacity masked grid, double orbit, and a
two-second listening ripple. It restores a 154 dp voice button, quiet navigation,
17 sp identity, 21 dp clock, 13 sp foreground excerpt and a bounded right-hand bubble.
Close composition enlarges the character while retaining foreground text above its
lower body. Reading/decision focus dims and freezes the character; focused interaction
pauses particles and the work trace. Hidden/unfocused views stop animated scheduling;
system reduced-motion settings suppress drift, breathing and transition animation.

No fictional memory/persona/source text is added to reproduce prototype screenshots.
The functional state sources are unchanged. Native text/font scaling, Android status
bar, landscape adaptation and lack of backdrop blur are explicit platform differences.
Compare motion using a real-device clip, not a single screenshot or a compiler pass.

## Character interaction reuse (0.4.7)

Reference reuse applies to the current character habitat itself, not only future
system overlays. The native implementation now adapts the pinned Android reference's
breathing and touch feedback: 5.2-second breathing cycle, gentle horizontal/vertical
scale, tap squash-and-rebound, and long-press pulse routed to the existing voice
control. Touch hit testing uses the character bounds; touching background text does
not start voice. The regular voice button remains available, and the character has
an accessibility description. Reduced motion suppresses transforms; reading/decision
attention holds the breathing clock. This visual response does not submit an agent
message or invent personality output. The previously accepted habitat composition
and information hierarchy remain authoritative.

Continue to inspect its actual character UI routines before implementing related
habitat behavior. Card arrival/dismissal, gesture handling and state feedback are
reuse candidates for this screen as well as the later overlay. Adapt small routines
with their license and revision rather than transplanting the engine-owning service.


## User correction: unboxed foreground narration (2026-10-25)

The user rejected the 0.4.24 floating reply/preset capsule: it overlapped the Agent
and left the space between character and voice controls unused. Ordinary foreground
text must appear naturally, character by character, in a dedicated region below
the character and above voice controls. It must not default to a floating box.
This supersedes the earlier default capsule/card placement requirement, including
its half-character face-clearance rule: the entire character must remain unobscured.

0.4.25 implements a separate native narration region, without a surface/border or
arrival translation. Existing ScrollView/follow-tail and source/attention ownership
are reused. Short/preset text reveals locally; actual server text deltas append to
the same visible item without replaying its prefix. This visual reveal is not a claim
that preset text came from a streaming backend. Hidden/unfocused regions pause;
reduced-motion settings show complete text. Explicit reading reveals/pins the full
item, and the queue's visible hold starts after reveal finishes. Compact landscape
places narration in the controls column, away from the character drawing plane.
F13 visual review remains open until the user accepts the revised device result.

## Conversation strip and character gestures (0.4.26)

The user replaces the single-item narration queue with a compact view of the same
Conversation adapter used by the expanded chat. New content, task/voice changes,
character gestures, or a tap in the region below the character reveal it. Idle hiding
uses `holdSeconds` (default 12); scrolling/holding postpones hiding. Fade-in/out take
250/600 ms, or apply immediately with reduced motion. The region remains tappable
while hidden. Expand shares its visibility and opens existing text entry, selection,
copy, history paging and decision controls. The preview shows up to the last 6000
UTF-16 units; expanded history is unaffected. Actual server text deltas drive updates;
loaded history is not artificially retyped. Source details remain labeled supplemental
content, distinct from conversation messages.

The bottom transcript, near/far and stop-voice buttons are removed. The voice button
retains start/listening-stop/playback-stop behavior. A Lucide menu icon replaces the
menu text with a 48 dp target and accessibility label.

The old habitat had only an OnClick callback. One native GestureDetector and
ScaleGestureDetector now own character input. Confirmed single taps submit touches;
double taps do nothing. Multi-pointer input cancels taps. Quick/stylus scaling is
disabled. Pinch continuously changes the existing composition parameter (0..1),
persists it locally and submits nothing. No parallel button zoom implementation
remains. Overlay dragging is a separate window operation.

Hit regions are normalized sprite coordinates: head below 46% height, shoulder
46..63%, waist 63..80%, feet 80..100%. Outside hits submit nothing. These are generic
2D regions, not anatomical inference; non-humanoid imports may need future metadata.
Accessibility activation uses a neutral shoulder tap. Accepted touches become explicit
user messages in the selected session via existing queue-mode submission, never
steer/cancel. Active microphone, missing connection or pending delivery produces
non-submission feedback; no automatic retry or new session.

Preset production and its editor/weight controls are removed. Legacy JSON fields
remain parseable and preserved by import/export/history; they no longer order the
conversation view. New defaults contain no phrases. F13 remains pending user review.

Platform references: https://developer.android.com/develop/ui/views/touch-and-input/gestures/scale
and https://developer.android.com/reference/android/view/GestureDetector.SimpleOnGestureListener#onSingleTapConfirmed(android.view.MotionEvent).

The user further requires icon-only primary actions: Expand uses the Lucide expand icon; the circular voice action switches between microphone and stop icons. Menu, Expand and voice retain accessibility labels and long-press tooltips. Status and approval information remain readable text.

## Taller foreground conversation region (0.4.27)

The user requests a taller lower conversation region that reaches toward the scaled
character's feet and renders in front of the character layer. The narration now
belongs to a foreground FrameLayout above the scene/layout content, with explicit
2 dp elevation. Its top tracks the scaled character's lower edge plus a 10 dp gap,
expanding upward into previously unused space; its bottom remains above voice
controls. The former 140 dp reservation stays in the underlying layout so resizing
the text region does not recursively resize or push the character. Breathing does
not move the anchor. Landscape retains the right-hand column. Existing transparent
presentation, icon expansion, idle fade and gestures remain unchanged.
