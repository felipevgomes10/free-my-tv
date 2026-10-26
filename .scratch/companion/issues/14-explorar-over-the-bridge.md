# 14: Explorar over the bridge

**What to build:** The bridge answers `list` of `trending` (Explorar's day, all kinds), `title` (Explorar's details with Where to Watch and whether a trailer exists), `open` of a service (`Explorar.watch`: the Title in its app, or its Play Store page when not installed, ADR 0014) and `open` of a trailer (the Home opened on Explorar's Trailer screen for that Title, ADR 0009). TMDB pictures as full image URLs. `MissingToken` and `Offline` told apart.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 13 (Em alta hoje and the Title screen)

**Status:** ready-for-agent

- [ ] Trending and a Title's details are answered from Explorar, with TMDB image URLs
- [ ] Opening a service does what the Home's Onde assistir does
- [ ] Opening a trailer brings the Home to its Trailer screen and plays it
- [ ] No token and offline are answered as such
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
