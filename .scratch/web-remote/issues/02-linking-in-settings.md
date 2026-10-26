# 02: Linking in Settings → Controle web

**What to build:** A new **Controle web** section in the Home's Settings where the Owner links the Home to the TV Remote Service, once.
- **The section:** it says whether the Home is linked, and offers "Vincular" (or "Vincular de novo" when linked).
- **Linking:** Android shows its code on the TV. The Owner types it with the Remote into a Home dialog. A wrong or expired code is told apart from success, with a way to try again.
- **The seam:** the TV Remote Service sits behind `TvPlatform`: link, report whether linked, and send a key with a direction (press, hold start, hold end). `FakeTvPlatform` plays its part in tests.
- **Setup:** the TV Remote Service's package joins the packages Setup refuses to disable, and the Home's Setup status gains a check that it is present and enabled.

Start by designing the Controle web section and the Linking dialog in `design.pen`, in the style of Settings and APK Drop, and export them to `docs/design/`.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 01 (Prototype: the Home reaches the TV Remote Service)

**Status:** ready-for-agent

- [ ] The Controle web section and the Linking dialog match their new design
- [ ] Linking from Settings succeeds and Settings shows the Home as linked
- [ ] A wrong or expired code shows its own message and lets the Owner try again
- [ ] Setup refuses to disable the TV Remote Service, even when it is in the approved extras list
- [ ] Setup status reports the TV Remote Service missing or disabled
- [ ] Tests cover Linking's outcomes against `FakeTvPlatform` and Setup's protected package
