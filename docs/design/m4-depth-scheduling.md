# M4 Depth and Occupancy Review 04

Status: local interactive prototype, pending visual review; not Android implementation.
Open [the prototype](m4-habitat-depth-preview.html). Review 03 is retained as history.

## Updated interaction requirement

Removing a card's background does not create a different presentation mechanism.
Different content needs distinct renderers, time behavior, depth, and attention cost.

| Renderer | Depth and behavior |
| --- | --- |
| Task/code stream | Behind the character; types incrementally, advances upward at line boundaries, dims older lines and removes them from a bounded six-line tail |
| Speech bubble | In front of the character; a stable readable surface with an explicit close control |
| Thought bubble | Foreground expressive bubble with a softer/dashed treatment; explicitly authored persona text |
| Memory fragment | Unboxed foreground quotation; appears and departs as a fragment rather than a console stream |
| Important decision | Foreground priority surface; persists until explicitly handled |
| Clock / controls | Reserved anchors; not displaced by arriving information |

The character is an actual stacking layer above the task stream. Transparent sprite
regions reveal the background while the character's painted pixels occlude it.
Foreground content may intentionally overlap the character's lower body, but its face
is reserved. This is not a universal prohibition on overlapping the character.

## Occupancy scheduler in this prototype

- Measure actual DOM rectangles for the header, clock, voice controls and character face.
- Measure each arriving foreground object; choose a fitting candidate location with an
  eight-pixel separation from reserved regions. If no position fits, retain it in queue.
- Use one foreground slot per view in this review. Queue other items instead of stacking
  them arbitrarily. The bounded queue refuses further entries visibly when full.
- Important decisions preempt lower-priority content, retaining the interrupted item.
  A closed decision yields to queued speech, followed by lower-priority fragments.
- While foreground content is displayed, dim and pause the background typewriter.
  Resume from the same position; do not fast-forward accumulated text.
- A transcript/settings sheet reserves the scene and pauses new foreground presentation.
- Recompute positions on size/layout changes. Shared explicit dismissals and expiry
  remove the same event from both views. Decisions never expire automatically.
- The optional occupancy overlay shows reserved rectangles; queue indicators explain
  why content is waiting. These are review aids, not proposed permanent product chrome.

The one-slot attention budget, candidate positions and timing are prototype choices.
Native implementation should retain the separation of content renderer, measured layout
reservation, attention priority and event lifetime. It must also account for the keyboard,
Android insets, accessibility scaling and real event volume. Do not interpret quiet
background presentation as the underlying DSH task having paused.

## Verification

Headless Edge checks passed at 1420x1100 and 420x1200 viewports: speech placement,
reserved-region clearance, memory queueing, decision preemption, interrupted-item
retention, actual typewriter pause, queue draining, typing resumption, background depth
below the character, and withholding new foreground items while a sheet is open.
The combined background-stream/foreground-bubble screenshot was visually inspected.
All content and state transitions remain illustrative; no recording or DSH request occurs.
