# 09: Notifications

**What to build:** The Home replaces the notification UI that the Stock Launcher used to provide. When a notification arrives, a Notification Banner appears in the top-right corner of the Home for 5 seconds. A bell with the count appears in the Status Bar only while notifications exist; selecting it, or holding Home (which Android delivers as the "toggle notification panel" request), opens the Notification Panel: the list of current notifications, newest first, each opening its app on OK, plus "Limpar tudo". When there are no notifications the panel says so and offers nothing to clear.

Reading notifications requires the notification listener access that Setup (13) grants; without it the bell never appears and the panel explains how to enable it.

Visual reference: `docs/design/notification-panel.png`.

**Blocked by:** 01 (Minimal Home with App Tray)

**Status:** ready-for-agent

- [x] New notifications show a 5-second Notification Banner
- [x] The bell appears only when notifications exist, with their count
- [x] Selecting the bell opens the Notification Panel (holding Home moved to 11: the system's long-press intent only reaches system apps, ADR 0005)
- [x] OK on a notification opens it; "Limpar tudo" dismisses all clearable notifications
- [x] Without listener access the feature degrades gracefully with an explanation
- [x] Tests cover banner timing, bell visibility, clear-all and the missing-permission path
