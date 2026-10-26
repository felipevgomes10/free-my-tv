# 07: Keys from a Paired Phone reach the TV

**What to build:** The bridge's key path, from a Paired Phone to the TV Remote Service.
- **The connection:** one WebSocket per open Web Remote, authenticated with the Paired Phone's credential. It opens with a hello carrying the protocol version. A page whose version the bridge doesn't speak is told to reload.
- **The state:** the bridge tells the page whether it is ready, not linked or revoked.
- **The keys:** each intention and direction is passed to the TV Remote Service through `TvPlatform` and acknowledged. Nothing is queued or replayed.
- **Revoking:** revoking a Paired Phone closes its open connection at once.
- **Concurrency:** two Paired Phones can be connected at the same time.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 06 (The Home serves the pinned page; Parear aparelho and Paired Phones)

**Status:** ready-for-agent

- [ ] A key from a Paired Phone reaches `FakeTvPlatform` with its direction, and is acknowledged
- [ ] A connection without a valid credential is refused, and sends nothing
- [ ] An unknown protocol version is told to reload
- [ ] The page learns "not linked" when the Home isn't linked, and "revoked" when its phone is revoked
- [ ] Revoking closes that phone's connection; the other phone's stays open
- [ ] Tests read every key and state JSON example from free-my-tv-remote-controller
- [ ] On the emulator, keys from the page move the focus on the Home (if the emulator has the TV Remote Service, per 01)
