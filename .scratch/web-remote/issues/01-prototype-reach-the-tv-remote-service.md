# 01: Prototype: the Home reaches the TV Remote Service

**What to build:** A throwaway prototype, on the owner's real TV with the owner present, that answers the Web Remote's riskiest question: can the Home send keys to other apps through the TV Remote Service (ADR 0001 in free-my-tv-remote-controller)?
- **Connect:** the Home opens a connection to the TV Remote Service at `127.0.0.1`.
- **Link:** Android shows its code on the TV, and the owner types it with the Remote into a bare dialog. The Home keeps the credential.
- **Send:** a debug-only trigger sends a few keys (right, OK, Back) while Netflix or YouTube is on screen.
- **Report:** write the findings in this file's Comments: what worked, the service's package name, how long a key takes to arrive, whether Linking survives a Home restart and a TV restart, and whether the Android TV emulator has the service.

If the TV refuses the connection or Linking, stop. ADR 0001 is reopened before any other ticket starts.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The Home connects to the TV Remote Service at `127.0.0.1` on the real TV
- [ ] Linking succeeds with the code Android shows, typed with the Remote
- [ ] A right, an OK and a Back sent by the Home act inside Netflix or YouTube
- [ ] The findings, including the emulator's answer, are written under Comments
- [ ] The prototype code is removed or kept only behind a debug-only switch
