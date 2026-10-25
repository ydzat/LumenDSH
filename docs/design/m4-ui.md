# M4 UI Design Review 01

Status: retained as a secondary conversation-page option. The primary page direction
is now the [switchable habitat prototype](m4-habitat.md). This is not implemented Android UI or UI01
acceptance evidence. Open [the interactive local preview](m4-ui-preview.html).
The preview has no network requests, persistence, microphone capture, or DSH actions.
Its messages and decisions are explicitly illustrative.

## Proposed direction

- Graphite surfaces, silver text, restrained lavender accents; no full-screen artwork.
- Conversation is the main surface. Use distinct user bubbles and readable assistant
  paragraphs. Put tool progress in compact rows; keep raw transport details out of chat.
- The header identifies the assistant and connection. A menu contains session selection,
  explicit creation, connection details, reconnect/disconnect, pairing and preferences.
- Keep the composer above the keyboard. Separate Send, Speak and local Stop actions.
- Show a dedicated foreground listening state. Automatic submission remains opt-in;
  the control must show the actual setting, not silently enable it after redesign.
- Put pending decisions in conspicuous cards with tool identity, relevant arguments,
  and explicit Reject / Allow once controls. Question forms retain required answers.
- Preserve the uncertain-delivery review affordance, task cancellation, history paging,
  and server-owned conversation selection. Do not lose working M1 behavior for styling.
- Use a small reserved companion area with an original placeholder until a validated
  appearance pack is selected. Character rendering must not cover approvals or input.

## Implementation and validation

Keep the existing native Java/API 29 stack. Extract reusable view styling and message
rendering from MainActivity as needed; no new UI framework dependency is proposed.
Target 360 dp minimum width, 16 dp margins, 15-16 sp message text, primary touch
targets of at least 44-48 dp, and system font scaling.

After user review, implement against real controller state. Test keyboard-open layout,
long history, long tool arguments/questions, connection loss, rotation, and voice stop
on the Huawei phone. Capture the stage demo only after actual UI behavior is verified.
The current browser preview was rendered and visually inspected at 1200x1000; this
is design review evidence only, not Android layout validation.
