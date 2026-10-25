# DSH Android migration tooling

These scripts support the [2026-10-25 feasibility record](../../docs/dsh-upgrade-investigation.md).
The production deployment now runs rc.3 only; see the
[current compatibility record](../../integrations/dsh/compatibility-rc3.md).
The commands below reproduce the initial isolated preparation, not an instruction
to reinstall an old runtime or rerun the completed migration.

```sh
candidate="$HOME/sliver-wolf-dsh-upgrade/0.2.1-rc.3"
mkdir -p "$candidate/runtime" "$candidate/home" "$candidate/workspace"
cd "$candidate/runtime"
# Initialize a private package.json here before installing.
npm install --ignore-scripts --no-audit --no-fund --save-exact @deepseek-ai/dsh@0.2.1-rc.3
npm install --ignore-scripts --no-audit --no-fund --save-exact @img/sharp-wasm32@0.35.5
GYP_DEFINES="android_ndk_path=$PREFIX" npm_config_nodedir="$PREFIX" npm rebuild node-pty --foreground-scripts
bash /path/to/build-flock.sh "$candidate"
node /path/to/patch-candidate.mjs "$candidate"
node /path/to/probe.mjs "$candidate" --write-probe
node /path/to/probe.mjs "$candidate" --read-only
```

`probe.mjs` starts and stops only the candidate on loopback port 3082. It handles
the generated launch token in memory, reports sanitized results, and never imports
the active credentials or conversations. The write probe sends a controlled prompt
without configuring a model; it tests persistence, not LLM completion. The second
run must list the controlled conversation to demonstrate restart persistence.
Reported RPC errors are investigation findings, not passing assertions.

`patch-candidate.mjs` adapts official MIT DSH package code and the BSD-3-Clause
system addon's loader. `build-flock.sh` compiles the unmodified C source shipped
with `@deepseek-ai/node-addon-system@0.1.2`. Android exclusive-copy publication is
based on the existing rc.7 port described by
[Deepseek-harness-mobile](https://github.com/ChongYep/Deepseek-harness-mobile).
Keep upstream notices in installed packages. All runtime downloads, native outputs,
generated credentials, logs and test conversations stay outside Git.

## Explicit production operations

- `migration-probe.mjs` tests copies of the old sessions before cutover. Its old
  endpoint preflight belongs to the completed migration and cannot run unchanged
  against the current deployment.
- `cutover.mjs` performs the explicitly authorized one-time migration: checks idle
  state, stops the old service, snapshots data/configuration, installs the new
  profile and launchers, and copies `serve.mjs` into the pinned runtime directory.
  Model credentials remain in place for the official provider to migrate.
- `serve.mjs` launches only rc.3 and pairs its browser cookie with the Android app
  through the existing authenticated device bridge. It does not export model keys.
- `retire-old.mjs` verifies the new deployment and removes the exact old runtime
  and old backup dependency trees. Data/configuration backups remain private.
- `check-client.sh` compiles and runs parser checks on Android. Its optional
  `--live` mode uses the production Java transport and actual DSH; model/tool
  checks create controlled conversations and can consume provider usage.

These are scoped migration tools, not general unattended installers. Runtime
downloads, private pairing state and data backups must remain outside Git.
Phone-specific durability limits, including exclusive-copy atomicity, are recorded
in the compatibility report. The old executable deployment is no longer retained.
