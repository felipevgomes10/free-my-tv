# 06: Update Sources

**What to build:** In Updates, each app installed from an APK can be given an Update Source: a GitHub repository, typed as "owner/name" or pasted as a URL.
- **Accepting a source:** it is accepted only if its latest release carries an APK. Each app shows its source under its name, or "Sem origem associada". A source can be changed or removed.
- **Checking:** the Home checks the sources about once a day while it runs, and whenever Updates opens. Versions compare by release tag (a leading "v" is ignored) against the installed version, part by part numerically.
- **Updating:** a newer release shows "Atualizar para X", and the Updates icon in the Status Bar gets the attention dot. Selecting it downloads the right APK (preferring "tv", then "armeabi-v7a"/"arm32", then "universal", then the only one) and hands it to Android's installer.
- **Failures:** being offline and failed downloads get a clear message.
- **Persistence:** sources survive restarts and Home updates.

Visual reference: `docs/design/updates.png` (the "Apps instalados por APK" block). The Update Source dialog follows the existing text input dialog.

**Blocked by:** 01 (Install permission in Setup)

**Status:** ready-for-agent

- [ ] Associating a repository with an APK release is accepted; one without is refused with a message
- [ ] A newer release shows "Atualizar para X" and the Status Bar dot; selecting it leads to Android's confirmation
- [ ] The daily check runs while the Home is up
- [ ] Sources can be changed and removed, and persist
- [ ] Tests cover source validation, version comparison, asset choice, the daily check, the dot and offline/download failures
