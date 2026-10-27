---
status: accepted
---

# The Controller opens the App Switcher as an overlay

The FMTV Controller's dedicated Multitarefas button opens the App Switcher above the app currently visible on the TV, with that app still visible beneath it. This entry point must not launch `MainActivity` or send Home first: the current `HomeDestination.AppSwitcher` brings the Home forward and `AppSwitcherScreen` draws an opaque background. Instead the Home owns a separate overlay presentation of the same Open Apps and actions.

The existing accessibility service is the first candidate for hosting a focusable overlay, since it already lives across apps and handles remote keys (ADR 0005). A TV prototype must establish live visibility, key routing and dismissal before that mechanism is adopted. The product decision is accepted; Android window details remain subject to that evidence. A translucent activity that takes over the app, or the still-screen cover used for Force stop (ADR 0010), is not evidence that the live app remains visible.

## Consequences

- Double Home and Shortcut Buttons keep their existing entry points. This decision adds a presentation for the dedicated request only.
- The overlay reuses `Multitasking` for Open Apps and switching; presentation and lifecycle do not belong in the bridge. It uses translucent fills without live blur, within ADR 0004's memory budget.
- Back removes the overlay without launching or restarting the underlying app. Selecting an Open App removes the overlay and switches to it. Home removes the overlay and follows normal Home behaviour.
- While the overlay is open, the app beneath it cannot be closed from the overlay. Existing media, foreground-service and protected-app safeguards still apply. Closing another app must respect ADR 0010 without stranding the owner in Settings or bringing the Home forward.
- Failure to host the overlay is reported to the Controller, never disguised by opening the opaque switcher.

Implementation and acceptance: [spec](../../.scratch/app-switcher-overlay/spec.md). The phone-side decision is [Controller ADR 0008](../../../free-my-tv-remote-controller/docs/adr/0008-a-dedicated-button-opens-the-app-switcher-over-the-app.md).
