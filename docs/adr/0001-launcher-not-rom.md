# Ship a replacement launcher, not a custom ROM

"Custom OS" here means a Home app that replaces the TCL/Google launcher, plus ADB-applied tweaks (disabling the stock launcher, granting extra permissions), not a reflashed firmware. TCL TVs ship with locked bootloaders and no community ROMs, so flashing risks bricking the TV and losing updates, while everything we want can be built as an app. Features that need ADB-granted permissions must degrade gracefully when the permission is missing, so the app can later run on TVs that were never touched by ADB.
