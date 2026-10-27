# App Switcher implementation validation — 2026-10-26

## Automated validation

- Home: **317 JVM tests passed**, including dedicated-command authentication/readiness/revocation, shared example replies, independent overlay state, empty/stale lists, underlying/media/visible-app close guards and no relaunch on dismissal/selection.
- Controller: **99 tests passed**, covering one request per tap, pending/disabled states, all error messages, old-bridge `unknown-request`, timeout, disconnect and no replay.
- Debug and release APK builds passed, including release lint/R8/resource shrinking.
- The debug probe requires the sender's existing `WRITE_SECURE_SETTINGS`; it is not present in the release manifest. No new permission is requested by the production manifest.

## Window and browser validation

See [prototype results](prototype-results.md) for the emulator checks, the close-return bug found and its verified correction. The Controller was checked with its fake bridge in the browser at 320 × 740 CSS pixels and in a wider viewport; all primary controls and the Multitarefas action fit. The narrow viewport is a development fixture, not an iPhone/Safari compatibility claim.

## Candidate artifacts

The Controller's `release/web-remote-app-switcher-candidate.zip` contains the built page and shared protocol examples. Build the integrated Home from this repository with:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew \
  :app:testDebugUnitTest :app:assembleRelease \
  -PwebRemoteZip=/Users/felipe_gomes/Documents/repos/free-my-tv-remote-controller/release/web-remote-app-switcher-candidate.zip
```

The candidate APK is `app/build/outputs/apk/release/app-release.apk`. This is an unreleased source build; app/page release versions were not bumped. The default build still uses the pinned Controller 0.2.3 until the normal release/pin workflow is completed; use the local zip to include this button meanwhile.

## Still required before TV acceptance

- Physical TCL: live YouTube and protected subscription playback; visual visibility/continuity, media controls and no Settings/Home flash during closing.
- Actual iPhone/Safari → authenticated bridge → overlay → keys → same underlying app, including an older bridge and unavailable/busy cases.
- Two phones, app-changing Shortcut Buttons, external navigation, service loss/revocation, sleep/wake and memory before/open/after on the 2 GB target.

The initial implementation pass did not publish, pin or install on the physical TV. Subsequently, the owner requested a TV installation: **0.6.4-multitarefas is now installed on the TCL**, and the owner confirmed the basic iPhone opening/Back flow with Disney+ visible underneath. See the [TV installation and smoke test](tv-install-test.md). Ticket 04 remains open for the rest of the matrix above; no public release or pin change was made.
