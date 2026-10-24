# 01: TMDB token through Setup

**What to build:** Explorar needs the owner's TMDB read access token on the TV, without it ever entering the public repository or the APK the TV updates from (ADR 0008).
- **Setup** reads the token from a git-ignored file in the repository's config folder (or an environment variable) and hands it to the Home in a broadcast that only a sender holding `WRITE_SECURE_SETTINGS` can deliver. That means the ADB shell, or the Home itself.
- **The Home** keeps the token in private storage.
- **Setup's output:** the plan shows the step with the token redacted, and the journal stores no token. When the token file is missing, Setup skips the step, says so, and does the rest.
- **Restore** tells the Home to forget the token.
- **Preparação** gains an "Explorar" check. The Setup Watchdog doesn't announce losing it.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] With the token file present, Setup delivers the token and Preparação shows "Explorar" Ativo; without it, Setup says so and skips only that step
- [ ] The token appears in no printed plan, no journal and no file under version control (the token file is git-ignored)
- [ ] An app other than the shell or the Home can't hand the Home a token
- [ ] Restore makes the Home forget the token
- [ ] Python tests cover the redacted step, the journal without the token, the missing file and Restore's forget; Kotlin tests cover the new check
