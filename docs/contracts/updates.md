# Update Contract v0.1

Status: M5 presentation/profile releases verified within U01-U04. Native PackageInstaller integration is documented in [M6 native releases](../m6-native-releases.md). See [M5 evidence](../m5-content-releases.md).

See [unified agent iteration](../updates.md) for identity and lifecycle principles.

## Targets and activation

| Target | Delivery | Activation |
| --- | --- | --- |
| Appearance/profile/configuration | Full versioned package, or later a verified delta | Hot load or component reload at a safe boundary |
| Supported interpreted extension | Deferred until an extension runtime is selected | Runtime-specific reload; no generic promise |
| Android native client | Signed APK | Android in-place installation and reconnect |
| DSH engine/plugins | Outside client self-update scope | Separate explicitly scoped backend change |

A source-code patch is a development artifact, not automatically an installable update. A binary delta is an optional distribution optimization, not the first implementation requirement. If introduced, verify the base hash and reconstructed target hash; never patch live files in place.

## Release record

Required concepts: `schemaVersion`, `releaseId`, `componentId`, `baseVersion` (or explicit initial-install state), `targetVersion`, payload size/hash, compatibility requirements, provenance/authentication, activation mode, verification evidence, migration requirements, and recovery strategy.

The release envelope authenticates a payload hash obtained through a trusted local workflow. An attacker-controlled manifest with matching hashes is not a trusted release. Final encoding and signing library selection are M5 tasks.

## State machine

`received -> validating -> staged -> activating -> verifying -> active`

Failed validation ends in `rejected` without touching the active version. Failed activation moves through `recovering` to `recovered` or an explicit `recovery-required` state. Store state before irreversible steps. Resume interrupted operations after process death. Serialize activations per component and reject an outdated base version if another update has already won.

Stage in app-private storage, validate schema/resources/compatibility, then switch the active pointer using storage semantics verified on the actual filesystem. Keep the previous working version until the candidate passes health checks. Recovery includes detecting a crash on next launch, not only catching an exception during activation. Retention/cleanup policy must not delete the only recovery version.

A profile update during speech waits for a suitable boundary. Native APK installation may stop the app; persist operation/session state first. A running DSH task must survive a client-only upgrade.

## Identity, signing, and migration

Native APKs must retain a compatible package/signing identity. The initial development flavor uses a persistent developer-owned key stored outside Git. The user-facing model for locally generated native releases remains a gating decision: no shared production private key in distributed apps, and no assumption that a locally signed APK upgrades an official signed build.

APK recovery may require a higher-version repair release, not a downgrade. User data and credentials stay outside replaceable content. Reject automatic migration until its recovery behavior is specified and tested.

Self-publication means local artifact production and activation, not posting publicly. Respect explicit Android installer actions and existing user policy; successful packaging does not prove successful installation.

## References and scope

- [TUF](https://theupdateframework.io/docs/overview/): authenticity/trust design reference, not our hot-loader or application rollback engine. Full remote repository infrastructure is deferred.
- [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller): native APK installation path.
- [App signing](https://developer.android.com/studio/publish/app-signing): update identity.
- [Tinker](https://github.com/Tencent/tinker): optional later native hot-fix reference; not selected for the first release.
