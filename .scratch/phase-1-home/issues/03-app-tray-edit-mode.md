# 03: App Tray Edit Mode

**What to build:** Holding OK on an App Tray tile enters Edit Mode for the App Tray: the chosen tile lifts, the other tiles dim, and left/right arrows move it along the tray; OK drops it in place and Back leaves Edit Mode. The menu button opens the item's actions — Move, Unpin, Uninstall — reusing the behaviour from 02. The new order persists.

Edit Mode is built once so the Input Tray (04) can reuse it with its own actions.

Visual reference: `docs/design/home-edit-mode.png`.

**Blocked by:** 02 (All Apps with Pin, Unpin, Install and Uninstall)

**Status:** ready-for-agent

- [ ] Holding OK on a tile enters Edit Mode; Back leaves it without changes beyond moves already dropped
- [ ] Left/right moves the lifted tile; OK drops it; the order survives a Home restart
- [ ] The actions menu offers Move, Unpin and Uninstall, behaving as in 02
- [ ] "Todos os apps" always stays last and cannot be moved
- [ ] Edit Mode is generic over the Tray and its item actions, so another Tray can supply different actions
- [ ] Tests cover moving to each end, dropping, cancelling, and Unpin from Edit Mode
