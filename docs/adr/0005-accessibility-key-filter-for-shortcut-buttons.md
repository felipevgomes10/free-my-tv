# Shortcut Buttons go through an accessibility key filter

On this TV the Netflix, Prime Video and YouTube buttons are handled by `launcherx` (which ADR 0003 disables), while Globoplay, Disney+ and Input are handled by TCL's system policy (`TclShortCutKeyManager`). To keep every Shortcut Button working after Setup, and to let the owner reassign them, the Home runs an accessibility service that filters these key events before the system handles them. Setup grants `WRITE_SECURE_SETTINGS` so the Home can re-enable that service if the system turns it off.

This does not contradict ADR 0003: we still don't intercept Home through accessibility, because Home has a clean path (the HOME intent, and long-press Home's `TOGGLE_NOTIFICATION_HANDLER_PANEL` intent, which the Home answers by opening the Notification Panel).
