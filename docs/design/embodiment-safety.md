# Embodiment and change-safety model

Status: living document, 2026-10-28. This is the model the agent uses to judge an
action's consequences on its own body (the phone) before taking it. It replaces
blanket "ask the user" with a self-review, and it keeps a few hard floors.

## 1. What the body is

| Layer | What it is | Failure mode |
| --- | --- | --- |
| Brain | DSH host on 3080 (slots 3082/3083 behind the relay) | process death, upgrade breakage |
| Motor bus A | adb over `127.0.0.1:5555` (Termux client) | disappears after reboot until `adb tcpip 5555` runs from an external adb |
| Motor bus B | app bridge `ws://127.0.0.1:3084/device-tools` (`assistant_screen_*`) | screen service disabled (`enabled:false`) until accessibility + capture are granted |
| Senses | screenshots, UIAutomator dumps, notifications, sensors, `termux-*` | blocked by a disabled bus or a locked screen |
| Reachability | Wi-Fi / mobile data (ZeroTier over Wi-Fi), Bluetooth | losing Wi-Fi cuts SSH, web, model access |
| Identity/state | `~/.dsh`, repo checkout, sessions, memories | destroying them loses continuity |

Current channel status is reported by `tooling/body-check.mjs`; bridge methods by
`tooling/bridge-probe.mjs`.

## 2. Severity rubric (CVSS-inspired, simplified)

Score each change on five axes (0 = harmless, 5 = fatal), then take the maximum
axis and the sum as a sanity check.

| Axis | Question |
| --- | --- |
| **Loop** | Does it risk cutting the agent's own control/observation channels? |
| **Reach** | Does it risk losing network/model/SSH reachability? |
| **Reversible** | Can it be undone without external help? (5 = needs a PC or reboot) |
| **Loss** | Could it destroy data, memory, sessions, or credentials? |
| **Disruption** | Could it disturb the user's real life (calls, payments, messages, alarms)? |

Decision bands:

| Band | Score | Action |
| --- | --- | --- |
| trivial | 0–1 | act freely (e.g. read settings, screenshot) |
| low | 2–3 | act, then restore the original state and record evidence (e.g. toggle Bluetooth with no device paired) |
| medium | 4–6 | self-review in the turn, prefer a reversible variant, take a snapshot first |
| high | 7–9 | do not act autonomously; propose it to the user with the score |
| critical | 10+ or any Loop/Reach = 5 | hard floor: never, even with approval |

Hard floors (always critical):
1. Wi-Fi / mobile data off, or airplane mode on.
2. Revoking USB debugging, developer options, or this app's permissions.
3. Killing the DSH host, relay, supervisor, or adb server.
4. Deleting `~/.dsh`, the repository, sessions or memory stores.
5. Any action that could send money, place calls, or message real contacts.

## 3. Worked examples

- Toggle Bluetooth with no paired device: Loop 0, Reach 1 (ZeroTier is over
  Wi-Fi), Reversible 0, Loss 0, Disruption 1 → **low**. Allowed; restore the
  previous state and log it.
- Turn Wi-Fi off "to save battery": Loop 3, Reach 5, Reversible 5 → **critical**
  (Reach=5 floor). Never.
- Enable airplane mode to test a setting: Reach 5 → **critical**. Never.
- `adb tcpip 5555` (re-enabling the motor bus): Loop 0, Reach 0, Reversible 0,
  Loss 0, Disruption 0 → **trivial/low**. Allowed when an external adb is
  available.
- Kill a stale `dsh` process: Loop 4, Reversible 4 → **high**; must go through
  the relay/supervisor path, never `kill`.
- Uninstall the client app: Loop 5 (loses the bridge) → **critical**.

## 4. Self-review routine for connectivity changes

1. Read the current state (`tooling/body-check.mjs`, settings, paired devices).
2. Score the five axes; write the score and the reason in the run record.
3. If low: apply, verify the intended effect, restore the original state, verify
   restoration.
4. If medium: take a restorable snapshot (e.g. record the setting value) first.
5. If high or critical: do not act; surface a proposal with the score and a safer
   alternative.
6. After any change, re-run the body check and record it as evidence.

## 5. Current known gaps

- adb drops after every reboot until an external adb runs `adb tcpip 5555`.
- The app's accessibility/MediaProjection channel exists but is disabled, so the
  adb-independent motor path is not yet usable.
- No automatic "safe-home" recovery after a failed physical task yet; the runner
  currently only presses Home at the end. `integrations/phone/ui-loop.mjs` now
  provides `safeHome`, `waitFor` and `withRecovery`, but the eval runner does not
  wrap its tasks with them yet.
