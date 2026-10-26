# 04: The bridge speaks protocol 2

**What to build:** The Home's side of the plumbing in ticket 03.
- **Protocol 2:** `hello` version 2; requests answered by `id`; requests from connections that aren't `ready` ignored; pushes to every ready connection.
- **Pictures:** `GET /pictures/{id}` on the bridge's server, answered only with a Paired Phone's credential in `Authorization`, from an in-memory cache sized for the Home's budget (ADR 0004).
- Reads the protocol's examples from the pinned zip (a local zip with `-PwebRemoteZip` until the page is released).

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 01 (Prefactor), 03 (The tabs and protocol version 2)

**Status:** resolved

- [x] A version-1 page is told to reload; a version-2 page is answered `ready`
- [x] A request's answer carries its `id`; one from a connection that isn't ready gets nothing
- [x] `GET /pictures/{id}` answers 401 without a Paired Phone's credential, and the picture with one
- [x] Tests read the protocol's examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on the `companion` branch.** `CompanionFeatures` is where each feature registers its lists (`list`), the field of `open` it answers (`open`), and requests of its own (`answer`); a `Refused` becomes `failed` with its reason, anything else `failed` with `error`. The bridge (protocol 2) answers every request by its `id`, ignores requests before the hello or while the Home isn't linked, and `push(of)` sends a list again to ready connections only. `WebRemotePictures` serves `GET /pictures/{id}` from per-prefix sources, 401 without a Paired Phone's credential, keeping rendered PNGs least-recently-used within 4 MB. 8 new tests, 274 in all, against page 0.2.0's examples built locally (`-PwebRemoteZip`). On the emulator, the protocol-2 page served by the Home showed its tabs and was ready, a list request got its `id` back, and pictures answered 401 to a missing or unknown credential.

The work lives on `companion` until a page with protocol 2 is released and pinned: `main` still pins page 0.1.0, whose examples are protocol 1.
