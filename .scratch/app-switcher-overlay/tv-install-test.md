# TCL installation and Controller smoke test — 2026-10-26

## Build and installation

- Source: Home `5a5d9a6`, Controller `5ddb508`.
- Controller: fresh `npm test` (**99 passed**) and `npm run build`.
- Home: JVM suite (**317 passed**) and release build with the new local page zip.
- Installed on the owner's TCL G09 / Smart TV Pro, Android 12, using the existing ADB connection.
- Installed version: **0.6.4-multitarefas**, versionCode 31. This is an identifiable local test build, not a published release.
- The signing certificate matched the previous APK; `adb install-multiple -r` preserved the existing data and installed the matching `.dm` file.
- Android reports `speed-profile`, reason `install-dm`.
- The TV's HTTP server returned the newly built page and `/assets/index-B8N2Y7qe.js` byte-for-byte. The served script contains `open-app-switcher` and the Multitarefas button.

The original APK and the test APK/profile/mapping are under `build/tv-multitarefas-test/`. `install-verification.json` there records APK hashes and the post-test state. The Controller bundle is `../free-my-tv-remote-controller/release/web-remote-multitarefas-tv-test.zip`.

## User-confirmed test

The owner reopened FMTV Controller on the iPhone, tapped Multitarefas and then Voltar. Asked whether Disney+ remained visible underneath and Back dismissed only the switcher, the owner answered **“Sim, funcionou”**.

After that test, ADB confirmed:

- Disney+ was the resumed app, in its original task 14124.
- No App Switcher window remained.
- The Home process was alive, with no fatal exception in its current process's log.
- One post-dismissal measurement was **118,853 KiB PSS**, approximately **116 MiB**. This is one reading, not a before/open/after memory study.

This passes the basic real-iPhone → TV overlay visibility/dismissal smoke test. It does not establish protected-video playback continuity, since the test did not separately measure a playing protected surface. The rest of ticket 04's matrix remains open: YouTube/active subscription playback, background close on the TCL, multiple phones, service loss/sleep, and the complete memory sequence.

## Rebuild

Gradle resolves `webRemoteZip` relative to the app module, so use an absolute zip path:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew \
  :app:testDebugUnitTest :app:assembleRelease \
  -PwebRemoteZip=/Users/felipe_gomes/Documents/repos/free-my-tv-remote-controller/release/web-remote-multitarefas-tv-test.zip \
  -PhomeVersionName=0.6.4-multitarefas
```
