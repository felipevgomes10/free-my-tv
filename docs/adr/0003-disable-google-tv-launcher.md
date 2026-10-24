# Disable the Google TV launcher instead of intercepting Home

Google TV (Android 12) offers no "default launcher" choice: the Home button always opens `com.google.android.apps.tv.launcherx`. Setup disables it with `pm disable-user` so the Home button lands on our Home, and Restore re-enables it. We rejected intercepting the Home key through an accessibility service because the stock launcher flashes before ours and Google TV kills such services unpredictably.

## Consequences

- Lost with `launcherx`: Google TV's "For you" recommendations, Google TV kids profiles, and its notification panel. The Home provides its own notification listener in its place.
- Unaffected: the screensaver (`com.google.android.apps.tv.dreamx`), Assistant (`com.google.android.katniss`), Chromecast, AirPlay and the system updater, which all live in separate packages.
- Also lost: the Netflix, Prime Video and YouTube remote buttons and long-press Home, which `launcherx` handled; ADR 0005 restores them.
- A TCL or Google system update may re-enable `launcherx`; the Home detects this and tells the owner to re-run Setup.

The screensaver choice is superseded by ADR 0011: the owner can now opt into Weather as the native screensaver, with Setup/Restore preserving the original configuration.
