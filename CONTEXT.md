# Free My TV

A custom Home experience for an Android TV (TCL), in the spirit of Apple TV: clean, focused, and fully under the owner's control.

## Language

### Surfaces

**Home**:
The screen the TV shows when the owner presses the Home button; replaces the manufacturer's launcher.
_Avoid_: Launcher screen, main screen, OS

**All Apps** (Todos os apps):
The screen listing every installed app, whether or not it is on the App Tray; where apps are pinned, installed and uninstalled.
_Avoid_: App drawer, library

**Status Bar**:
The strip at the top of the Home showing time, connectivity and notifications.

**Top Shelf**:
The large area at the top of the Home that shows content from the app currently focused in the App Tray, as on Apple TV: what the owner last watched in that app; it stays dark when there is nothing.
_Avoid_: Hero, banner, featured

**Settings** (Configurações):
The Home's own settings screen (Widgets, Cast, Setup status, About); distinct from Android Settings, which it only links to.
_Avoid_: Preferences, options

**Android Settings** (Configurações do Android):
The system settings app for network, picture, sound and accounts, which the Home opens but does not replace.

**Notification Banner**:
A brief message in the top-right corner of the Home announcing a new notification.
_Avoid_: Toast, popup

**Notification Panel** (Painel de notificações):
The list of current notifications, opened from the Status Bar.

**Edit Mode** (Modo de edição):
A temporary state, entered by holding OK on an item, in which the owner reorders or changes items in a Tray.
_Avoid_: Jiggle mode, customize mode

### Trays

**Tray** (Bandeja):
A horizontal row on the Home holding one kind of item.
_Avoid_: Row, shelf, dock

**App Tray** (Bandeja de apps):
The Tray holding the apps the owner chose to keep on the Home, in the owner's order.
_Avoid_: Favorites, dock

**Input Tray** (Bandeja de entradas):
The Tray holding the TV's physical and broadcast inputs (HDMI, antenna, AV).
_Avoid_: Sources tray

**Input** (Entrada):
A physical signal source the TV can switch to: an HDMI port or the antenna. Live channels provided by apps are never Inputs.
_Avoid_: Source, port, channel

**Up Next Tray** (Continuar assistindo):
The Tray holding movies and episodes the owner has started in other apps and not finished, as the Home saw them play (ADR 0007); an app that doesn't say what is playing gets one entry for the app instead.
_Avoid_: Continue watching, watch next, recents

### App lifecycle

**Pin** / **Unpin** (Fixar / Desafixar):
Adding an installed app to the App Tray, or taking it off; the app stays installed either way.
_Avoid_: Add, remove, favorite

**Install** (Instalar):
Putting a new app on the TV, from the Play Store or from an APK sent from another device. A newly installed app appears in All Apps only, never pinned automatically.

**Uninstall** (Desinstalar):
Removing an app from the TV entirely.
_Avoid_: Delete, remove

**APK Drop** (Enviar APK):
Installing an app by sending its APK from a phone or computer to a page the TV serves on the home network while the owner has it open.
_Avoid_: Upload, sideload server

**Update Source** (Origem):
Where a sideloaded app's new versions come from (a GitHub repository's releases), associated by the owner; the Home itself always has its own repository as its Update Source.
_Avoid_: Repo, feed, channel

**App Banner**:
The 16:9 artwork an app supplies for TV home screens, shown as its tile; apps without one show their icon over a color taken from it.
_Avoid_: Logo, thumbnail

### Upkeep

**Updates** (Atualizações):
The screen gathering everything that keeps the TV current: the system security patch, Play Store app updates, and apps installed from an APK.

**Security Patch** (Patch de segurança):
The manufacturer-issued system update level the TV is running; the Home can report it and open the manufacturer's updater, never produce or install one.

**Stock Launcher**:
The manufacturer's original home screen, which the Home replaces and which system updates may re-enable.

**Setup** (Preparação):
The one-time procedure, run from a computer over ADB, that disables the Stock Launcher and approved bloatware and grants the Home its extra permissions.

**Restore** (Restauração):
The procedure that undoes Setup, returning the TV to its factory launcher and packages.

### Casting

**Cast** (Transmissão):
Another device sending video, audio or its screen to the TV, via Chromecast, AirPlay or Miracast.
_Avoid_: Mirroring, streaming, screen share

**Cast Receiver**:
The software on the TV that accepts a Cast; always a system service or a third-party app, never the Home itself.

### Multitasking

**Open App** (App aberto):
An app the owner has used that is still alive in the background and resumes where it was left.
_Avoid_: Running app, recent app, task

**App Switcher** (Alternador de apps):
The row of Open Apps, most recent first, opened by pressing Home twice; where the owner switches to or closes an Open App.
_Avoid_: Recents, multitasking view, task manager

**Open App Limit** (Limite de apps abertos):
The rule that closes the least recently used Open App whenever there are more than the allowed number of Open Apps or free memory falls below a safe floor; apps playing media are never closed.
_Avoid_: Memory cleaner, task killer

### Remote

**Shortcut Button** (Botão de atalho):
A remote button printed with a service's name (Netflix, Globoplay, Prime Video, Disney+, YouTube); the owner can assign it any app, Input or the App Switcher.
_Avoid_: Hotkey, partner button, app button

### Widgets

**Widget**:
A small, glanceable panel on the Home that shows live information and can be hidden by the owner.
_Avoid_: Card, tile

**Hidden Widget**:
A Widget the owner turned off; it is neither shown nor gathering data.

**Weather Widget** (Widget de tempo):
The Widget showing current weather conditions for the owner's location.

**System Monitor Widget** (Widget de monitor do sistema):
The Widget showing storage used, RAM in use, the Home's own frame rate, and the display's refresh rate.
_Avoid_: Stats widget, performance overlay
