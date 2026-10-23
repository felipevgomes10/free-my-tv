# 02: All Apps with Pin, Unpin, Install and Uninstall

**What to build:** Selecting "Todos os apps" opens All Apps: every installed app in alphabetical order, as App Banners with names always visible, and a pin badge on apps that are on the App Tray. A header shows how many apps are installed and how many are pinned. Holding OK (or the menu button) on an app opens its actions: Pin/Unpin, Uninstall and App info. Pin and Unpin update the App Tray immediately. Uninstall goes through Android's own confirmation, and when it completes the app disappears from All Apps and, if pinned, from the App Tray. The "Instalar app" button opens the Play Store. An app installed while the Home is running shows up in All Apps only, never pinned automatically.

Visual reference: `docs/design/all-apps.png`.

**Blocked by:** 01 (Minimal Home with App Tray)

**Status:** ready-for-agent

- [x] All Apps lists every launchable TV app alphabetically, with a pin badge on pinned apps
- [x] Pin adds the app to the end of the App Tray; Unpin removes it from the App Tray and it remains in All Apps
- [x] Uninstall requests Android's confirmation; only a completed uninstall removes the app from All Apps and the App Tray
- [x] A newly installed app appears in All Apps and is not pinned
- [x] "Instalar app" opens the Play Store
- [x] Tests cover Pin, Unpin, completed and cancelled Uninstall, and a new Install, through the fake port
