# 07: Home self-update

**What to build:** The Home updates itself from this project's GitHub releases.
- **In Updates:** it lists itself under "Apps instalados por APK" with its version and a fixed Update Source (this repository), which the owner can't change or remove. A newer release shows "Atualizar para X" like any other app.
- **Installing:** the Home installs its update through a package installer session that asks Android not to require the owner's action. The first self-update after an ADB install still shows Android's confirmation; after that the Home is its own installer of record and later updates go straight through. After an update the system brings the Home back as HOME. A release with a different signature is refused by Android, never installed.
- **Publishing:** a release script on the Mac bumps the version, builds the release APK signed with the existing key, and publishes a GitHub release with the APK attached, using `gh`. No CI.

**Blocked by:** 06 (Update Sources)

**Status:** ready-for-agent

- [x] The Home appears in "Apps instalados por APK" with its version and fixed source
- [ ] The release script publishes a release whose APK installs over the running Home (dry run checked; the first real release is in 08, as it pushes to GitHub)
- [x] On the emulator, a published newer release updates the Home; the first time asks, the second doesn't
- [x] After the update the Home is back in front as HOME
- [x] Tests cover the Home's fixed source and that a self-update requests the no-action install
