# 06: The Home serves the pinned page; Parear aparelho and Paired Phones

**What to build:** The Home's side of Pairing, with the Web Remote served from the TV.
- **The page:** the build downloads the Web Remote release pinned in `gradle.properties`, checks its checksum, and puts it in the APK (ADR 0002 in free-my-tv-remote-controller). A wrong checksum fails the build.
- **The bridge:** it serves the page on the home network for as long as the Home is alive, on a fixed port distinct from APK Drop's, within the memory budget (ADR 0004).
- **Parear aparelho:** a screen in FMTV Controller showing the TV's address as a QR code and a short Pairing Code. The Pairing session exists only while the screen is open, and the code changes after a few wrong tries. A successful Pairing shows the phone's name on the TV. The screen advises reserving the TV's IP in the router.
- **Aparelhos pareados:** the list of Paired Phones by name in FMTV Controller, each revocable.

Start by designing Parear aparelho and the Paired Phones list in `design.pen`, in the style of APK Drop.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 02 (Linking in Settings), 04 (Release the page as a versioned zip), 05 (Pairing on the page): it pins a page release that has Pairing

**Status:** resolved

- [ ] The build fetches the pinned page and fails on a checksum mismatch (the mismatch is checked; fetching from GitHub waits for the first page release, 0.1.0, which needs the owner's go-ahead to publish)
- [x] Safari on a phone on the same network opens the page from the QR code (checked with curl on the emulator; a real iPhone in 09)
- [x] Pairing works only while Parear aparelho is open; wrong codes are refused and change the code after a few tries
- [x] A Paired Phone appears by its name, and revoking removes it
- [x] The screens match their new design
- [x] Tests cover the Pairing session lifecycle, the code rules and the Paired Phones list, in the manner of `ApkDropTest`, and read the Pairing JSON examples

## Comments

**2026-10-26, done but for the GitHub fetch.** `webRemotePage` in the app's build unpacks the pinned zip's `page/` into the APK's `assets/web-remote/` and its `protocol/` into the test resources; `webRemoteVersion` is empty until the first page release, so `main` builds without the page (the bridge then answers 503), and `-PwebRemoteZip=<path>` builds with a local zip. A wrong checksum fails the build and deletes the download.

`WebRemoteServer` starts with the Home on port 8766 and stays up: the page from the assets (hashed `assets/` cached for a year, everything else `no-cache`), `POST /pair`, and `/remote` upgraded to a WebSocket (closed at once until ticket 07). `WebRemotePairing` opens only with Parear aparelho, gives a 4-digit code that changes after 3 wrong tries and after each phone paired; `PairedPhones` keeps names and credential hashes in the Home's preferences. 9 new tests read the protocol's examples from the zip; 254 in all.

On the emulator, through `adb forward tcp:8766`: the page, manifest, service worker and icons were served; Pairing was refused with the screen closed (409); the browser pane paired "iPhone de teste" with the TV's code, the TV said "iPhone de teste foi pareado." and changed its code, and Settings listed it; revoking emptied the list. Revoking took the focus with the row and dropped it on Widgets; the focus now moves to Parear aparelho first.
