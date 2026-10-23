# Phase 2: the Apple TV polish

**Status:** ready-for-agent

## Problem Statement

Phase 1 made the TV usable every day, but the Home still feels bare next to an Apple TV, and a few chores still need the Mac:

- The top half of the Home is an empty backdrop. It shows nothing about the app in focus.
- There is no "Continuar assistindo". To get back to a show, the owner has to open the app and look for it there.
- Installing an app that isn't on the Play Store needs a pen drive, a file manager, or the Mac with ADB.
- Apps installed from an APK never update. Updates lists them with their version and nothing more.
- The Home itself only updates when the owner runs a command on the Mac.

## Solution

**Up Next Tray ("Continuar assistindo").** A Tray below the App Tray lists what the owner started in other apps and didn't finish.
- Android keeps the apps' own "continue watching" lists out of reach of a Home like ours (ADR 0007). So the Home builds the list from what it sees playing on the TV.
- Each entry shows the title, the artwork and how much is left. Selecting one goes back to it.
- An app that doesn't say what is playing (Netflix, Prime Video, Globoplay) gets a single entry for the app: "Continuar no Netflix".

**Top Shelf.** Focusing an app in the App Tray fills the top of the Home with what the owner last watched in that app: artwork, title, and the app's name.
- If the app has more than one entry, the Top Shelf cycles through them, with dots marking the position.
- If there is nothing for that app, it shows the app's banner, enlarged and blurred once ahead of time.

**APK Drop ("Enviar APK").** In All Apps, "Enviar APK" shows an address, a QR code and a short code. The owner opens the address on a phone or computer on the same network, types the code and sends the APK.
- The TV shows the app's name and version.
- Android asks for its usual confirmation, and the app is installed.
- The page only exists while "Enviar APK" is open.

**Update Sources.** In Updates, each app installed from an APK can be given an Update Source: a GitHub repository.
- The Home checks that repository's releases.
- When a release is newer than the installed version, it shows "Atualizar para X". The Home downloads and installs it after Android's confirmation.

**Home self-update.** The Home lists itself under "Apps instalados por APK", with this project's GitHub repository as its fixed Update Source.
- A newer release is downloaded and installed by the Home itself.
- After the first update the Home owns its own installs, so later updates go through without asking.

## User Stories

### Up Next Tray

1. As the owner, I want a "Continuar assistindo" Tray on the Home, so that I can get back to what I was watching without hunting through apps.
2. As the owner, I want each entry to show the title and the app's artwork for it, so that I recognise it from the couch.
3. As the owner, I want each entry to show how much is left ("Episódio 4, faltam 18 min"), so that I can choose something that fits the time I have.
4. As the owner, I want a progress bar on each entry, so that I see at a glance how far along I am.
5. As the owner, I want the most recently watched entry first, so that the thing I just stopped is the easiest to reach.
6. As the owner, I want selecting an entry to take me back to that title where I stopped when the app allows it, so that resuming is one click.
7. As the owner, I want selecting an entry to at least open its app when the app can't resume that title directly, so that the click is never useless.
8. As the owner, I want an entry to leave the Tray on its own once I finish it, so that the Tray only holds unfinished things.
9. As the owner, I want an entry to leave the Tray after a long time without being watched, so that abandoned shows don't clutter it forever.
10. As the owner, I want to remove an entry myself through Edit Mode ("Remover da lista"), so that I control what stays.
11. As the owner, I want an app that doesn't say what is playing to appear once as "Continuar no <app>", so that it still has a quick way back.
12. As the owner, I want watching the same title again to update its entry instead of adding a second one, so that the Tray has no duplicates.
13. As the owner, I want the Tray hidden when it is empty, so that the Home doesn't show an empty row.
14. As the owner, I want the Tray to keep its entries when the TV restarts, so that the history survives a power cut.
15. As the owner, I want the Tray to drop entries for apps I uninstall, so that it never offers an app that is gone.
16. As the owner, I want music and short sounds kept out of the Tray (Spotify, system sounds), so that it only holds movies and episodes.
17. As the owner, I want the Tray to follow the Tray design (title "Continuar assistindo", landscape artwork tiles, the focused one enlarged), so that it looks like the rest of the Home.
18. As the owner, I want the Home to tell me in Settings → Preparação if it can't see what is playing (no notification access), so that I know why the Tray is empty.

### Top Shelf

19. As the owner, I want the top of the Home to show what I last watched in the app I'm focusing on, so that the Home feels alive like an Apple TV.
20. As the owner, I want the Top Shelf to show the app's name, the title and how much is left, so that I know what it is showing.
21. As the owner, I want the Top Shelf to cycle through that app's entries when there are several, with dots for the position, so that I see more than one thing.
22. As the owner, I want the app's banner, enlarged and blurred, when the app has nothing to show, so that the top of the Home is never empty.
23. As the owner, I want the Top Shelf to change quickly as I move across the App Tray, so that it never lags behind my focus.
24. As the owner, I want the Top Shelf to shrink and dim when I move down to the Up Next or Input Tray, so that the Trays get the room.
25. As the owner, I want the Top Shelf to keep the Home under its memory budget, so that the TV stays fast (ADR 0004: images at 1080p at most, blur done once per image).

### APK Drop

26. As the owner, I want an "Enviar APK" button in All Apps, so that I can install an app from my phone without a pen drive.
27. As the owner, I want the TV to show an address and a QR code, so that I can open the page on my phone quickly.
28. As the owner, I want a short code on the TV that the page asks for, so that nobody else on the network can push apps to my TV.
29. As the owner, I want the page to work in any phone or computer browser without installing anything, so that any device I have will do.
30. As the owner, I want to see on the TV the name and version of the APK I sent, so that I know it's the right file.
31. As the owner, I want Android's own install confirmation before anything is installed, so that nothing installs without my say.
32. As the owner, I want to be told when the APK is an update of an app I have, or is signed differently from it, so that I'm not surprised by a failed update.
33. As the owner, I want a clear message when the file isn't a valid APK or is too big for the free storage, so that I know what went wrong.
34. As the owner, I want the page to stop working as soon as I leave "Enviar APK", so that the TV doesn't keep a door open on the network.
35. As the owner, I want a newly installed app to appear in All Apps only, never pinned automatically, so that my App Tray stays as I left it (Phase 1 rule).
36. As the owner, I want the received APK file deleted after the install finishes or is cancelled, so that it doesn't eat storage.

### Update Sources

37. As the owner, I want to associate a GitHub repository with an app installed from an APK, so that the Home can find its new versions.
38. As the owner, I want to type the repository as "owner/name" or paste its URL, so that it is easy to enter with the remote or a keyboard.
39. As the owner, I want the Home to check that the repository's releases have an APK before accepting it, so that a wrong repository is caught right away.
40. As the owner, I want each sideloaded app to show its Update Source under its name, or "Sem origem associada", so that I know which ones update.
41. As the owner, I want "Atualizar para X" when a newer release exists, so that I know an update is waiting.
42. As the owner, I want the Updates icon in the Status Bar to show the attention dot when any app has an update, so that I notice without opening Updates.
43. As the owner, I want the Home to check for new releases on its own, about once a day, so that I don't have to remember.
44. As the owner, I want the update downloaded and handed to Android's installer when I select "Atualizar para X", so that updating is one click plus Android's confirmation.
45. As the owner, I want the right APK picked when a release has several (TV, ARM 32-bit, universal), so that the update actually runs on this TV.
46. As the owner, I want to change or remove an app's Update Source, so that I can fix a mistake.
47. As the owner, I want a clear message when GitHub can't be reached or the download fails, so that I know to try later.
48. As the owner, I want Update Sources kept when the Home updates or the TV restarts, so that I set them once.

### Home self-update

49. As the owner, I want the Home itself listed under "Apps instalados por APK" with its version, so that I see which Home I'm running.
50. As the owner, I want the Home's Update Source fixed to this project's repository, so that I never have to set it.
51. As the owner, I want "Atualizar para X" on the Home when a newer release exists, so that I get fixes without the Mac.
52. As the owner, I want later Home updates to install without Android asking each time, once the Home has installed itself once, so that keeping it current is effortless.
53. As the owner, I want the Home to come back on its own after it updates itself, so that the TV never sits without a Home.
54. As the owner, I want a release script on the Mac that builds the release APK, signs it with the same key and publishes it on GitHub, so that publishing a Home update is one command.
55. As the owner, I want a Home update with a different signature refused by Android rather than replacing the Home, so that a bad release can't break the TV.

### Setup and Restore

56. As the owner, I want Setup to allow the Home to install apps (the "install unknown apps" permission), so that APK Drop and updates work without digging through Android Settings.
57. As the owner, I want Restore to take that permission back, so that Restore still returns the TV to how it was.
58. As the owner, I want Settings → Preparação to show whether the Home may install apps, so that I see when a system update took it away.

## Implementation Decisions

- **One seam.** `TvPlatform` stays the only seam between the Home and Android. Every rule lives on the Home's side, and the Android adapter only translates. New capabilities on the port:
  - **What is playing:** a stream of the TV's current media sessions, each with package, playback state, position, duration when known, title, subtitle and artwork when the app gives them, and whether the session can reopen its content.
  - **Resume:** reopen an app's content through its live session, falling back to launching the app.
  - **Up Next storage:** load and save the Up Next history, plus storing an entry's artwork once, downscaled to the Top Shelf's size.
  - **APK files:** read an APK's package, label, version and signer; ask Android to install it (with its confirmation, or silently where the system allows); delete a received file.
  - **The APK Drop server:** start and stop a small HTTP server on the local network that hands each received upload, with the code typed, to the Home. Report the TV's local address.
  - **GitHub releases:** the latest release of a repository, with its tag and its APK assets; download an asset to a file.
  - **Update Source storage:** load and save each package's Update Source.
- **Up Next Tray** (a new state holder, in the shape of `NotificationCenter` and `InputTray`):
  - It follows the stream of what is playing and turns sessions into entries, keyed by package plus title. Untitled sessions are keyed by package alone, and those make the "Continuar no <app>" entry.
  - It ignores sessions without video: music apps by the media type they report, and anything shorter than a few minutes.
  - Finished means position ≥ 95% of duration, or less than 2 minutes left, so credits count as finished. Entries untouched for 30 days drop.
  - At most 20 entries. Selecting one goes through Resume.
  - Edit Mode on this Tray offers only "Remover da lista" (decided in Phase 1's design).
- **Top Shelf** (a new state holder):
  - It takes the focused App Tray item and the Up Next history and yields the Up Next entries for that app, in recency order, or the banner fallback.
  - It cycles every few seconds while focus rests, and debounces focus moves so that fast scrolling doesn't load artwork for every tile passed.
- **Artwork budget:** artwork is decoded once at 1080p at most and kept on disk. The Top Shelf blur is computed once per image and cached, never live (ADR 0004).
- **APK Drop** (a new state holder):
  - It owns the session: generating the short code, accepting only uploads with the right code, a few attempts before the code changes, and stopping the server when the screen closes.
  - It inspects the received APK and reports it as new, an update, or a signer mismatch.
  - It hands the APK to Android's installer, and deletes the file afterwards.
  - The server listens only while "Enviar APK" is open, on a fixed port, and serves a single page with a file field and a code field.
- **Updates** (extends `UpdatesStateHolder`):
  - It adds Update Sources and release checks. A source is accepted only if its latest release has at least one APK asset.
  - Versions compare by the release tag (a leading "v" is ignored) against the installed version name, part by part numerically.
  - Asset choice prefers names mentioning "tv", then "armeabi-v7a"/"arm32", then "universal", then the only APK.
  - It checks once a day while the Home runs, and on opening Updates. The Status Bar attention dot also lights for available app updates.
- **Home self-update:**
  - The Home is no longer excluded from the sideloaded list. It is listed with its fixed Update Source.
  - It installs through a package installer session that asks Android not to require user action, which Android honours once the Home is the installer of record of itself. The first self-update after an ADB install still shows Android's confirmation.
  - After an update the system restarts the Home as HOME.
  - **Release tooling:** a script on the Mac bumps the version, builds the release APK signed with the existing debug key, and publishes a GitHub release with the APK attached, using the `gh` CLI. No CI: the key stays on the Mac.
- **Setup/Restore:**
  - Setup grants the Home the `REQUEST_INSTALL_PACKAGES` app-op. Restore resets it.
  - Settings → Preparação gains a check for it, and the Setup Watchdog announces losing it, like the other checks.
  - The Home declares `REQUEST_INSTALL_PACKAGES`, plus `INTERNET`, which it already uses for weather.
- **Glossary and ADRs:** Up Next Tray, Top Shelf, APK Drop and Update Source are as defined in `CONTEXT.md`. ADR 0007 records why Up Next comes from media sessions.

## Testing Decisions

- **What a good test is.** It drives a state holder through `FakeTvPlatform` and asserts what the owner would see: the state the screen renders, and the platform calls made (resume, install, start or stop the server). It never asserts on internal fields or call order that the owner can't observe. Time is controlled with the coroutine test scheduler, as the System Monitor and Weather tests already do.
- **Up Next Tray tests:**
  - a playing session creates an entry; replaying updates it rather than duplicating it;
  - the 95% and 2-minute finish rule at its boundaries;
  - the 30-day expiry and the 20-entry cap;
  - untitled apps collapse to one entry; music and short media are ignored;
  - uninstalling an app drops its entries;
  - "Remover da lista";
  - selecting resumes through a live session, or launches the app.
- **Top Shelf tests:** the entries for the focused app in order, the banner fallback, cycling while focus rests, and the debounce while focus moves.
- **APK Drop tests:**
  - the server runs only while open; a wrong code is refused, and the code changes after repeated failures;
  - new, update and signer-mismatch reports; an invalid file and one too big for free storage;
  - the file is deleted after install or cancel.
- **Updates tests:**
  - a source is accepted only with an APK asset;
  - version comparison: "v" prefix, numeric parts, and longer versions;
  - the asset choice order; the daily check;
  - the attention dot; offline and download failures;
  - the Home listed with its fixed source.
- **Setup/Restore tests:** in `test_tvsetup.py`, the new grant appears in the Setup plan and its inverse in Restore, against `FakeTv`.
- **Prior art:** `HomeStateHolderTest` and `AppTrayEditModeTest` (Trays and Edit Mode), `NotificationCenterTest` (state from a platform stream), `UpdatesStateHolderTest` (installer split and the 6-month rule), `SystemMonitorTest` (timing with a slow platform), `SetupWatchdogTest` (new Setup checks), `test_tvsetup.py` (plans).
- **Adapter checks on the TV, not in JVM tests:**
  - which apps give titles and artwork (re-measured from the media session dumps);
  - the APK Drop page from a real phone;
  - a self-update from a real GitHub release.

## Out of Scope

- Reading the TV provider's Watch Next rows or preview channels, and the Engage SDK: not possible for a non-system Home (ADR 0007).
- Suggestions the owner never started (new episodes, recommendations), since the Home only knows what played.
- Live previews of an Input in the Top Shelf.
- Update Sources other than GitHub releases (F-Droid, direct URLs), and private repositories.
- Listing pending Play Store updates (no public API; Phase 1 decision).
- Building or signing releases in CI.
- Parental controls and a configurable wallpaper (left out in the Phase 1 design).
- The Home's own app icon and the Figtree font (Phase 1 leftovers, tracked separately).

## Further Notes

- The coverage measured on 2026-10-23 sets expectations. YouTube, Disney+ and Crunchyroll get titled entries. Netflix, Prime Video and Globoplay get "Continuar no <app>". Apps may change what they report in later versions, and the Tray picks that up without a code change.
- The Home's install of itself goes through the package installer, so the first self-update moves the installer of record from ADB to the Home. Restore is unaffected: it never uninstalls the Home.
- APK Drop opens a port on the home network only while its screen is open, and every upload needs the code shown on the TV. Android's confirmation stays the final gate for every install from an APK, except the Home's own updates after the first one.
- Visual references: the Top Shelf and the Up Next Tray in `docs/design/home-app-tray.png` and `docs/design/home-input-tray.png`; Update Sources in `docs/design/updates.png`. The APK Drop screen and the Update Source dialog have no design yet. They follow the Settings and dialog styles already in `design.pen`.
