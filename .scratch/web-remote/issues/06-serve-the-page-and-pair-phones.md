# 06: The Home serves the pinned page; Parear aparelho and Paired Phones

**What to build:** The Home's side of Pairing, with the Web Remote served from the TV.
- **The page:** the build downloads the Web Remote release pinned in `gradle.properties`, checks its checksum, and puts it in the APK (ADR 0002 in free-my-tv-remote-controller). A wrong checksum fails the build.
- **The bridge:** it serves the page on the home network for as long as the Home is alive, on a fixed port distinct from APK Drop's, within the memory budget (ADR 0004).
- **Parear aparelho:** a screen in Controle web showing the TV's address as a QR code and a short Pairing Code. The Pairing session exists only while the screen is open, and the code changes after a few wrong tries. A successful Pairing shows the phone's name on the TV. The screen advises reserving the TV's IP in the router.
- **Aparelhos pareados:** the list of Paired Phones by name in Controle web, each revocable.

Start by designing Parear aparelho and the Paired Phones list in `design.pen`, in the style of APK Drop.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 02 (Linking in Settings), 04 (Release the page as a versioned zip), 05 (Pairing on the page): it pins a page release that has Pairing

**Status:** ready-for-agent

- [ ] The build fetches the pinned page and fails on a checksum mismatch
- [ ] Safari on a phone on the same network opens the page from the QR code (checked with curl on the emulator; a real iPhone in 09)
- [ ] Pairing works only while Parear aparelho is open; wrong codes are refused and change the code after a few tries
- [ ] A Paired Phone appears by its name, and revoking removes it
- [ ] The screens match their new design
- [ ] Tests cover the Pairing session lifecycle, the code rules and the Paired Phones list, in the manner of `ApkDropTest`, and read the Pairing JSON examples
