# 07: Keys from a Paired Phone reach the TV

**What to build:** The bridge's key path, from a Paired Phone to the TV's own ADB (ADR 0005).
- **The connection:** one WebSocket per open Web Remote, authenticated with the Paired Phone's credential. It opens with a hello carrying the protocol version. A page whose version the bridge doesn't speak is told to reload.
- **The state:** the bridge tells the page whether it is ready, not linked or revoked.
- **The keys:** each intention becomes its Android key code (`wake` is `KEYCODE_WAKEUP`) and goes to `TvPlatform`, which runs `cmd input keyevent` on one ADB shell kept open; a `hold-start` is a long press and `hold-end` does nothing. Each key is acknowledged. Nothing is queued or replayed.
- **Revoking:** revoking a Paired Phone closes its open connection at once.
- **Concurrency:** two Paired Phones can be connected at the same time.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 06 (The Home serves the pinned page; Parear aparelho and Paired Phones)

**Status:** resolved

- [x] A key from a Paired Phone reaches `FakeTvPlatform` with its direction, and is acknowledged
- [x] A connection without a valid credential is refused, and sends nothing
- [x] An unknown protocol version is told to reload
- [x] The page learns "not linked" when the Home isn't linked, and "revoked" when its phone is revoked
- [x] Revoking closes that phone's connection; the other phone's stays open
- [x] Tests read every key and state JSON example from free-my-tv-remote-controller
- [x] On the emulator, keys from the page move the focus on the Home and in Android's Settings, and holding OK opens Edit Mode

## Comments

**2026-10-26, done.** `WebRemoteBridge` answers each WebSocket: a hello from a Paired Phone gets `ready` (or `not-linked`, until Linking), an unknown credential `revoked` and a close, another protocol version `reload`. Keys from every phone go through one lock, in order, to `TvAdb.sendKey`, which runs `cmd input keyevent` on the connection Linking opened and keeps (reopened once if it broke; a refused key marks Linking lost and the pages hear `not-linked`). `hold-start` is `--longpress`; `hold-end` only acks. 11 new tests against the protocol's examples; 265 in all.

On the emulator, paired from the browser pane: a swipe down moved the Home's focus two rows; holding OK through the bridge opened Edit Mode on the App Tray; eight presses took 41–101 ms from the page's send to its ack (median 73 ms, through `adb forward`). Two things found on the way:
- The bridge told pages about Linking from the main thread and the Home crashed (`NetworkOnMainThreadException`): its notices now run on `Dispatchers.IO`, and a failed socket write can no longer take the Home down.
- This emulator image can't save "always allow" (`/data/misc/adb/adb_keys: EACCES`), so it asked again on every new connection; Linking now keeps its connection open for keys instead of opening a second one. The real TV, which keeps the Mac's key, is checked in 09.
