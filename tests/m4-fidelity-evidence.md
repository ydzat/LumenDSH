# M4 Review 05 fidelity correction

The user rejected the 0.4.3 visual result as an incomplete implementation of the
approved prototype. The previous functional checks remain useful, but UI01 is
reopened for visual/motion fidelity. This record must not be read as a new pass.

## Source comparison

Compared `docs/design/m4-attention-preview.html`, its saved rendered review image,
and the actual `HabitatView` implementation. The following omissions were confirmed
by source inspection, rather than attributed to a phone performance limitation:

| Review 05 element | 0.4.3 implementation | Correction in source |
| --- | --- | --- |
| Four-second character breathing | Built-in character static | Visible-time breathing offset; reading/decision pauses |
| Seven-second particle drift | Absent | Twelve subtle drifting particles; focus/offline pause |
| Soft aura, beam, horizon and double orbit | Flat fill, heavy grid, one oval | Radial aura, translucent beam, faded perspective grid and double orbit |
| Listening ripple and voice bars | Only task-driven bars in scene | Ripple at the orbit; bars beside the voice control |
| Smooth near/far change | Immediate size change | 700 ms interpolation |
| Attention dimming | Abrupt color changes | 800 ms environment/HUD convergence and character dimming |
| Quiet controls, small title, bounded speech bubble | Large filled navigation and wide controls | Quiet navigation, 17 sp title, 154 dp voice action and bounded bubble |

The prototype's grid itself is static; the perceived life comes primarily from
particle motion, character breathing, ripple, state transitions and foreground/text
arrivals. The correction follows those mechanisms rather than inventing a rotating
or constantly moving grid.

## Verification status

Host javac against the pinned API 29 and existing dependency jars passed. No new
dependency or DSH change was introduced. Phone-side compilation, signature verification,
in-place installation and launch of 0.4.4 (code 11) succeeded after USB authorization.

The first correction APK SHA-256 was
`3f1bbf53d964d4f8d92dadd5b2cc8c0b5eb575287291e54f8fcfd9f70b4abf1e`.
Actual-device clip:
`artifacts/demos/20261024-232824-m4-review05-motion-056c3587.mp4`.
Decoded duration 10 seconds, 540x1168, silent. Inspected decoded frames at 1, 3 and
8 seconds. Before any UI action, the first two frames show different character
vertical positions and particle positions. The final frame shows the close character
composition after the interpolated transition. This is actual renderer motion, not
an imported sprite animation or a browser-prototype recording.

Pinned the real controlled reply into reading mode. Actual screenshots show the
character dimmed, the environment subdued and the foreground excerpt retained;
exiting reading restores the ambient presentation. Returned to space composition.
No microphone capture, new DSH prompt, character pack, or voice setting change was
needed for these visual checks. Listening ripple is implemented but was not exercised
with microphone recognition in this correction run. The final build additionally
adds the prototype's 500 ms foreground bubble arrival; the clip predates that small
addition and does not claim to demonstrate it.

Remaining visual differences must be judged on actual output: native font rendering,
Android status bar, large-font/landscape adaptation, and no CSS backdrop blur.
Prototype-only fictional memories and thoughts remain absent until real sources exist.


Final APK SHA-256:
`0cbec265bd0400314717e1b2f7ea88c3f149f982a180ddefdc7e2db23d74fee6`.
Phone output: `artifacts-build-ZrhEI8n7/sliver-wolf.apk`; the existing developer
certificate and application identity were retained. Visual fidelity remains subject
to comparison with the approved design, rather than being inferred from these checks.
