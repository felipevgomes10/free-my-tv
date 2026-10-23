# 05: APK Drop

**What to build:** "Enviar APK" in All Apps lets the owner install an APK from a phone or computer.
- **The screen:** it shows the TV's address on the home network, a QR code for it, and a short code.
- **The page:** it opens in any browser and asks for the code and the file. A wrong code is refused, and the code changes after a few failures. The page only exists while "Enviar APK" is open.
- **What arrives:** the TV shows the app's name and version, and says whether it is a new app, an update, or signed differently from the installed one. An invalid file, or one too big for the free storage, gets a clear message.
- **Installing:** Android asks its usual confirmation. The new app appears in All Apps only, never pinned. The received file is deleted after the install or after a cancel.

Start by designing the "Enviar APK" screen in `design.pen`, in the style of Settings and the existing dialogs, and export it to `docs/design/`.

**Blocked by:** 01 (Install permission in Setup)

**Status:** ready-for-agent

- [ ] The "Enviar APK" screen matches its new design, with address, QR code and code
- [ ] From a phone on the same network, sending an APK with the right code shows its name and version on the TV and leads to Android's confirmation
- [ ] A wrong code is refused; repeated failures change the code; leaving the screen stops the page
- [ ] Updates and signer mismatches are told apart from new apps; invalid and oversized files get a message
- [ ] The received file is gone after install or cancel
- [ ] Tests cover the session lifecycle, the code rules, the APK report kinds and the clean-up
