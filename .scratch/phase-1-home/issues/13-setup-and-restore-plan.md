# 13: Setup and Restore plan

**What to build:** Setup and Restore are run from the owner's computer over ADB. Each first builds a plan — the exact list of ADB commands it will run — and can print that plan without running it (dry run). Setup disables the Stock Launcher and the owner-approved bloatware with `disable-user` (never uninstall), and grants the Home its extra access: usage history, notification listener, the accessibility service for Shortcut Buttons, and secure-settings writing. Restore re-enables exactly what Setup disabled and revokes what it granted.

Grants discovered while building the Home (all verified on the emulator):

- `pm grant com.freemytv.home android.permission.DUMP` and `pm grant com.freemytv.home android.permission.PACKAGE_USAGE_STATS`, plus `appops set com.freemytv.home GET_USAGE_STATS allow` — System Monitor rankings and Open Apps (ADR 0006).

- `cmd notification allow_listener com.freemytv.home/com.freemytv.home.android.HomeNotificationListener` — Notification Panel and Banner.

- `pm grant com.freemytv.home android.permission.WRITE_SECURE_SETTINGS` — lets the Home turn its key filter on and back on; on its first start it enables `com.freemytv.home/com.freemytv.home.android.KeyFilterService` itself, so Setup only needs to grant the permission (Restore must also remove the service from `enabled_accessibility_services`).

A protected-package list is hard-wired and can never appear in a plan: Chromecast (`com.google.android.apps.mediashell`), AirPlay (`com.tcl.airplay2`, `com.amlogic.airplay`), Miracast (`com.tcl.miracast`), TV inputs and player (`com.tcl.tvinput`, `com.tcl.tv`), `com.tcl.systemserver`, `com.tcl.guard`, the screensaver (`com.google.android.apps.tv.dreamx`), Assistant (`com.google.android.katniss`), the system updater and Play Store.

The ticket also produces the candidate bloatware list for the owner to approve package by package in 14; the approved list is an input to Setup, not hard-coded.

Per the owner's rule, nothing in this ticket is run against the TV except read-only commands; the plan is verified in dry run and against the emulator.

**Blocked by:** 01 (Minimal Home with App Tray)

**Status:** ready-for-agent

- [ ] Setup and Restore both support dry run, printing the full plan
- [ ] No plan ever touches a protected package, whatever the approved list says
- [ ] Every package change is `disable-user`; no plan uninstalls anything
- [ ] Restore's plan is the exact inverse of the Setup that was applied
- [ ] The candidate bloatware list is produced from the TV's read-only package listing, with a one-line reason per package
- [ ] Tests cover the protected-list rule, the disable-only rule and Setup/Restore symmetry
