# M4 Habitat Design Review 02

Status: composition prototype retained for reference; the subsequent
[semantic-space review](m4-semantic-space.md) refines information presentation.
Not implemented Android
UI or acceptance evidence. Open [the local prototype](m4-habitat-preview.html).
The earlier [conversation design](m4-ui-preview.html) remains a secondary-page option.

## Confirmed product direction

The primary page presents the agent and its digital habitat. Voice is the main
interaction channel; the transcript is secondary. Information surfaces, can be pinned,
and otherwise recedes. The user requested both switchable compositions:

- Space: a small character in a larger environment, with surrounding information.
- Character: a larger character with information arranged around its margins.

Both share the agent identity, selected conversation, voice/task state and pinned
information. Appearance resources remain independent of layout, voice and behavior.
The project name refers to the user's fondness for Silver Wolf from Honkai: Star Rail;
the prototype uses original geometric pixel artwork, not official character material.

## Prototype scope

Two synchronized views show the same inline SVG pixel placeholder in a dark habitat
with a perspective grid, a floor projection and sparse particles. Controls switch
between comparison/space/character layouts and idle/listening/working presentation.
The floating note fades after eight seconds unless pinned. Transcript and appearance
information open as secondary sheets. No network, microphone, persistence, model call,
or actual pack import occurs. State changes and text are explicitly illustrative.

Animations establish composition and transition intent; they are not the final pet
renderer. The SVG placeholder scales smoothly during composition exploration. Actual
raster pet implementation still needs integer-scale/pixel alignment and bounded atlas
decoding, as described in the appearance work. No new production dependency is added.

## Review performed

Rendered in a separate headless Edge profile at 1420x1040; inspected working-state
comparison screenshot. Browser DOM interaction checks passed seven assertions:
shared listening state, shared pin state, single-layout selection, state preservation
across layout changes, secondary transcript opening, local stop presentation, and
shared working state. These are prototype checks, not Android/device acceptance.

Next: review character scale, space atmosphere, floating-information placement and
pixel-art direction with the user before implementing the native primary surface.
