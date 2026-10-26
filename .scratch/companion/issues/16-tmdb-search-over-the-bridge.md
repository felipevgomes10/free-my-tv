# 16: TMDB search over the bridge

**What to build:** TMDB's `search/multi` in `pt-BR` added to the Home's `Tmdb`, movies and series only, with Explorar's token (ADR 0008); the bridge answers `search` with the results in the examples' shape.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 14 (Explorar over the bridge), 15 (Search)

**Status:** ready-for-agent

- [ ] A search returns movies and series, never people, in Portuguese
- [ ] No token and offline are answered as such
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
