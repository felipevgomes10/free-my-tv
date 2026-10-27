# The Controller's App Switcher over the current app

**Status:** ready-for-agent

**Decision date:** 2026-10-26. See [ADR 0016](../../docs/adr/0016-the-controller-opens-the-app-switcher-as-an-overlay.md) and the [Controller spec](../../../free-my-tv-remote-controller/.scratch/app-switcher-overlay/spec.md).

## Problem Statement

`HomeDestination.AppSwitcher` starts the Home's `MainActivity`; `AppSwitcherScreen` fills it with an opaque background. Reusing that route for the Controller's dedicated button would hide the app the owner is using. This entry point needs a separate overlay while retaining the existing Open Apps model.

## Required behaviour

1. One tap on the Controller's **Multitarefas** button shows the App Switcher above the app visible when the request is handled, without first opening the Home. The live app stays visible around/through the overlay. Never substitute a screenshot, a black background or an opaque Home screen.
2. Use the existing App Banners, labels, most-recent ordering, count/limit and protected-app indicators. Initially focus the previous Open App when available, as today; otherwise the first card. An empty list still has a dismissible empty state. If the Home is visible, it remains the surface beneath the overlay.
3. Repeated open requests leave one overlay and preserve its focus. Multiple Paired Phones share that one TV surface.
4. Directional keys and OK navigate the overlay and never reach the app beneath. Back is consumed, removes the overlay and restores input to the app without launching or restarting it. Home removes the overlay and follows the existing Home path; it must not leave an invisible window capturing keys. Volume and playback controls retain their existing meaning.
5. Selecting an Open App removes the overlay and switches to it through the existing app action. Selecting the underlying app just dismisses the overlay; it does not need a relaunch. A stale card for an app that disappeared is handled without leaving an orphan overlay or launching an unrelated app.
6. Up closes an eligible app with the existing repeat protection and Force stop safeguards. The app beneath the overlay is not closable from this presentation; label it `Em uso` and omit its close affordance. Media, foreground-service and protected-app rules remain in effect. Closing another app must not leave Settings visible, replace the underlying app with the Home or lose the overlay's remaining selection. Coordinate the return path with ADR 0010 rather than blindly reusing its current return-to-Home path.
7. Opening/dismissing does not deliberately pause, stop or relaunch the app. Record actual playback/focus behaviour on the TV: third-party playback policy is a compatibility question to verify, not something a fake platform test can prove. Protected video must be checked with the actual display; no screenshots or recording of its content are required.
8. Dismiss and release window/UI resources when the hosting service stops, access is revoked, the TV goes to sleep, or an incompatible navigation/action takes over. Reopening must work without stacked windows or retained key capture. A phone disconnect by itself does not dismiss it; the Remote must still be able to dismiss it.

Single/double/held Home and assigned Shortcut Buttons keep their current entry points. The dedicated opening does not change the Open App Limit. Automatic enforcement and external app changes must not leave stale focus or a window stranded over a new surface.

## Shared protocol contract

The Controller owns the executable examples to be added in `protocol/examples/`. Keep these semantics identical to its spec:

```json
{"type":"open-app-switcher","id":42}
{"type":"done","id":42}
{"type":"failed","id":42,"reason":"overlay-unavailable"}
```

- Additive protocol 2 request, using the existing request/key ID counter and authenticated WebSocket. Accept only Paired Phones on `ready` connections; preserve existing ignore/revoke rules otherwise.
- No package or key sequence. Resolve the underlying surface when handling the request. Do not use `platform.openHome(HomeDestination.AppSwitcher)` for this request.
- Answer once with the same `id`. `done` means the overlay is shown and navigable, including when it was already open. Queuing an open is insufficient. No dismissal push is introduced.
- Answer `failed` with `overlay-unavailable` for unavailable service/window or failure to show, `busy` while an incompatible operation such as Force stop is in progress, and `error` for unexpected failures. Remove any partially created overlay and leave the prior surface intact.
- Complete or fail before the page's existing 5-second timeout; do not leave a queued open that can surprise the owner later. A lost response must not cause a second window on a manual retry.
- Older bridges answer `unknown-request`; the page offers an update message. Keep protocol version 2 and ship page/examples through the existing pinned bundle.

## Implementation boundaries

- `Multitasking` remains the source of Open Apps and their actions. Separate overlay presentation state/lifecycle from the existing full-screen presentation so opening, dismissing or switching one cannot leave the other behind.
- First prototype a focusable overlay hosted by the existing accessibility service (`KeyFilterService`). Establish the Android window mechanism on the target TV before committing the production adapter. If it fails, record why and revise the implementation decision; do not silently substitute a screenshot or a Home activity.
- Keep Android window/input operations behind the platform boundary. The bridge dispatches the command and awaits an observable result; it does not manage windows or add a second key injection transport.
- Reuse switcher content with a translucent presentation and an uncovered area; retain existing typography, focus treatment and artwork. Use plain translucent fills, no live blur or screen capture; respect ADR 0004's Home memory budget and release artwork with the overlay.
- Coordinate key filtering with Shortcut Buttons and Force stop on the service's key thread; attach/update/remove UI on the appropriate UI thread without blocking remote-key answers.

## Verification

JVM tests cover request routing, same-ID results, ready-only access, unavailable/busy/error results, one-overlay semantics, retry/focus preservation, close protection and cleanup through a fake platform boundary. Existing `MultitaskingTest`, shortcut and bridge tests must remain green. Consume the Controller's shared JSON examples via its bundle, with `-PwebRemoteZip=<path>` while unreleased.

Emulator checks cover window/input lifecycle. The target TCL and an iPhone are required for acceptance:

| Scenario | Evidence required |
| --- | --- |
| YouTube plus a subscription video app, including protected playback | Live app visible before/during/after opening; record whether video/audio continues or the app pauses itself. A blank protected surface fails the visibility requirement. |
| App menu / paused content | Back returns to the same position; no Home flash or app restart. |
| Arrows, OK, Back from phone and Remote | Keys affect the overlay only; Back dismisses once, then keys reach the app. |
| Switch to another app / select underlying app | Overlay removed; correct app/state shown. |
| Repeated taps and two phones | One overlay; no focus reset, duplicates or accidental dismissal. |
| Empty list / Home already visible | Readable empty state or row, and working dismissal. |
| Close eligible background app / protected app / underlying app | Existing close protections hold; Settings and Home do not replace the intended return surface. |
| Service loss, sleep/wake, disconnect/reconnect, Home and app-changing Shortcut Button | No retained window, trapped input or automatic replay; reopening works. |
| Older bridge / unavailable service / busy Force stop | Phone reports failure/update instruction; current app stays intact. |

Record device/builds, results, visible failures, playback differences and memory before/open/after. Do not claim TV acceptance from emulator/JVM results alone.

## Tickets and boundaries

Tickets restart at 01 per repository; cross-project dependencies name and link the repository explicitly.

1. [01: Prove live overlay and input on the TV](issues/01-prove-live-overlay-on-tv.md).
2. [02: Overlay presentation and lifecycle](issues/02-overlay-presentation-and-lifecycle.md).
3. [03: Bridge command](issues/03-open-app-switcher-command.md).
4. [04: Integrated TV acceptance](issues/04-tv-acceptance.md).

The Controller owns the button, error copy, examples and page bundle. This does not redesign existing full-screen entry points, implement Android system Recents or expand app-closing permissions. These documents plan implementation; no overlay or command is implemented by this documentation change.
