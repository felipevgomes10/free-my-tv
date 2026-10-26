# 04: The bridge speaks protocol 2

**What to build:** The Home's side of the plumbing in ticket 03.
- **Protocol 2:** `hello` version 2; requests answered by `id`; requests from connections that aren't `ready` ignored; pushes to every ready connection.
- **Pictures:** `GET /pictures/{id}` on the bridge's server, answered only with a Paired Phone's credential in `Authorization`, from an in-memory cache sized for the Home's budget (ADR 0004).
- Reads the protocol's examples from the pinned zip (a local zip with `-PwebRemoteZip` until the page is released).

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 01 (Prefactor), 03 (The tabs and protocol version 2)

**Status:** ready-for-agent

- [ ] A version-1 page is told to reload; a version-2 page is answered `ready`
- [ ] A request's answer carries its `id`; one from a connection that isn't ready gets nothing
- [ ] `GET /pictures/{id}` answers 401 without a Paired Phone's credential, and the picture with one
- [ ] Tests read the protocol's examples and drive the bridge against `FakeTvPlatform`
