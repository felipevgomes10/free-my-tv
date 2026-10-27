# 03: Open the overlay from the authenticated bridge

**Status:** resolved

**Blocked by:** [Home 02: Overlay presentation](02-overlay-presentation-and-lifecycle.md), [Controller 01: Protocol and fake bridges](../../../../free-my-tv-remote-controller/.scratch/app-switcher-overlay/issues/01-protocol-and-fake-bridges.md).

**What to build:** Register the `open-app-switcher` companion request and await the overlay operation from ticket 02. Use the [spec](../spec.md)'s additive protocol-2 contract and Controller-owned shared examples. Do not invoke the old `openHome(AppSwitcher)` route.

- [x] Only Paired Phones on `ready` connections can invoke it; existing unready/revoked rules still hold.
- [x] Each request gets one same-ID reply: `done` after navigable display/already-open, or the specified unavailable/busy/error failure.
- [x] Failure cleans partially created UI and keeps the prior TV surface. A bounded operation completes/fails before the 5-second page timeout and cannot open later from an abandoned queue.
- [x] Repeated requests, including from two phones, use one overlay without resetting focus or turning the request into a toggle.
- [x] Protocol tests consume the Controller's bundled examples with `-PwebRemoteZip=<path>` while unreleased; verify no Home launch or injected Home key.
- [x] Bridge and multitasking suites pass; older page controls and existing double-Home/Shortcut Button actions retain their behaviour.

## Comments

**2026-10-26:** Implemented and verified in source. See the feature validation results for the local bundle, shared-example tests and remaining physical TV acceptance. No release published.
