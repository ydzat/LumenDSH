# Device Check Helpers

These helpers are separate from the assistant APK. They exercise real Android UI/runtime
behavior; parser checks use explicitly synthetic inputs and cannot pass integration gates.

Inside the existing Termux build environment, with `ANDROID_JAR` and `SIGNING_DIR` set:

```sh
bash tooling/android-build/check-conversation.sh
bash tooling/android-build/build-device-checks.sh
```

Transfer the reported test APK through authorized ADB and install it as
`local.sliverwolf.assistant.tests`. The client must already be visible. Set an exact test
message from PowerShell without changing the phone IME:

```powershell
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('Hello'))
& $adb -s $endpoint shell am instrument -w -e textBase64 $encoded local.sliverwolf.assistant.tests/.TextInput
```

Require `result=exact-text-set` before using the client's Send button. Failure is not a
successful input. For a streaming observation, run the following in a separate terminal,
then press Send on a prompt long enough to observe streaming:

```powershell
& $adb -s $endpoint shell am instrument -w -e action observe-stream local.sliverwolf.assistant.tests/.TextInput
```

Expected result: `live-streaming-text-observed`, otherwise an explicit timeout/error.
The helper reads only matching client nodes and prints no conversation content. Remove
the test-only package with `adb -s <endpoint> uninstall local.sliverwolf.assistant.tests`
after validation. This does not remove the assistant or its session selection.

Platform reference: [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation#getRootInActiveWindow()).

M3 controls (client visible, idle, empty draft, microphone already granted):

```sh
adb -s <endpoint> shell am instrument -w -e action voice-stop local.sliverwolf.assistant.tests/.TextInput
adb -s <endpoint> shell am instrument -w -e action acquire-focus local.sliverwolf.assistant.tests/.TextInput
```

`voice-stop` requires the actual Listening state, stops locally, and checks that it
stays stopped. `acquire-focus` takes real Android audio focus for two seconds;
run it when `integrations/device-tools/check-audio-focus.mjs` prints PLAYBACK_RUNNING.
That Node check verifies TTS cancellation and absence of automatic replay.
Its `barge-in` mode instead requires tapping Speak after PLAYBACK_RUNNING, then Stop
after MICROPHONE_ACTIVE_AFTER_PLAYBACK_STOP. This tests explicit tap interruption,
not acoustic wake detection. Do not substitute these checks for an actual phone call.

## Appearance importer and renderer checks

Run `python tests/make-pet-fixtures.py` on the host. Copy the resulting ignored ZIPs to
an owned phone test directory. Set `PET_FIXTURES` to that directory and `ANDROID_JAR`
to the pinned stubs, then run `tooling/android-build/check-pets.sh` inside the existing
Termux build environment. This compiles production importer/renderer code to DEX and
runs real Android bitmap/Canvas/storage checks via `app_process`; it needs no test APK.
Expect `PET_IMPORT_CHECKS_PASS`. Test state lives in a fresh build test directory and
does not mutate the installed assistant's active character or conversation.

The conversation check script also runs NotificationChecks and ScheduleChecks in
Android app_process. ScheduleChecks exercises the adapted POSIX atomic registry
with controlled times. Do not run its registry replacement checks as Windows runtime
acceptance: File.renameTo replacement semantics differ. Alarm delivery remains a
separate real-device check recorded in m45-schedule-evidence.md.


`OverlayChecks` starts the already-enabled companion overlay over the controlled
`OverlaySurface`, opens its real system keyboard and asserts panel geometry against
the stationary measurement viewport. It emits only rectangles/insets. Instrumentation
restarts the client process; it is not a microphone-lifecycle test. Optional
`-e restoreX <original x> -e restoreY <original y>` restores only coordinates saved
before an explicit drag test. Run with those previously recorded values, never guesses.


`HabitatInteractionChecks` launches the installed Activity, captures only its touch
callback while testing real Android gesture detection, and restores zoom afterward.
It verifies body regions, outside/double/pinch exclusion, idle fade, reveal and chat
expansion/selection. Callback capture does not establish DSH delivery; pair it with
the controlled live queue check described in `tests/m45-interaction-evidence.md`.
Huawei min scaling span was observed as 435.223 px; the pinch fixture crosses it.
`TextInput -e action icon-voice-stop` checks the actual mic/stop icon state transition
without supplying speech or retaining audio.
