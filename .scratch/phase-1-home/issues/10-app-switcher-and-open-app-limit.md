# 10: App Switcher and Open App Limit

**What to build:** Pressing Home twice opens the App Switcher: a row of the Open Apps, most recent first, as App Banners with name and how long ago each was used; focus starts on the previous app, so Home twice then OK flips between the last two apps. Pressing up on a focused card closes that Open App: it leaves the row and its process is ended. Apps playing media are labelled as such.

The Open App Limit keeps the TV responsive: whenever there are more Open Apps than the limit (default 3, adjustable from 2 to 5 in Settings), or free memory falls below 300 MB, the least recently used Open App is closed, one at a time, until both conditions hold again. Apps playing media or running a foreground service, system services and Cast Receivers are never closed and never count towards the limit. The App Switcher header states the current count against the limit.

Open Apps are derived from usage history (access granted by Setup) and closing uses the platform's background-process kill, so the foreground app is never closed by the Home.

Visual reference: `docs/design/app-switcher.png`.

**Blocked by:** 07 (Settings)

**Status:** ready-for-agent

- [ ] Home pressed twice within a short interval opens the App Switcher; a single press still goes to the Home
- [ ] Focus starts on the previous app; OK switches to the focused app
- [ ] Up on a card closes that app and removes it from the row
- [ ] The limit (2–5, default 3) is adjustable in Settings and persists
- [ ] Exceeding the limit or dropping below 300 MB free closes the least recently used eligible app
- [ ] Apps playing media, foreground services, system services and Cast Receivers are never closed nor counted
- [ ] Tests cover LRU ordering, both triggers, all exemptions, and double-press detection
