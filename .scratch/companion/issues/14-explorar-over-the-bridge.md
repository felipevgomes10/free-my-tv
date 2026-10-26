# 14: Explorar over the bridge

**What to build:** The bridge answers `list` of `trending` (Explorar's day, all kinds), `title` (Explorar's details with Where to Watch and whether a trailer exists), `open` of a service (`Explorar.watch`: the Title in its app, or its Play Store page when not installed, ADR 0014) and `open` of a trailer (the Home opened on Explorar's Trailer screen for that Title, ADR 0009). TMDB pictures as full image URLs. `MissingToken` and `Offline` told apart.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 13 (Em alta hoje and the Title screen)

**Status:** resolved

- [x] Trending and a Title's details are answered from Explorar, with TMDB image URLs
- [x] Opening a service does what the Home's Onde assistir does
- [x] Opening a trailer brings the Home to its Trailer screen and plays it
- [x] No token and offline are answered as such
- [x] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on `companion`.** `addExplorar` answers `trending` with the day's Titles, all kinds, through `Explorar.trending`, which keeps them in Explorar's cache without touching the Home's row; `title` with `Explorar.lookUp`, the same lookup `openDetails` now uses, with the ClassInd rating, the movie's runtime or the series' seasons, the rating to one decimal, whether there's a trailer, and Onde assistir, each service with its app's banner when installed. `open` with `service` calls `Explorar.watch` (the Title in its app, or its Play Store page); `open` with `trailer` puts the Title on Explorar's details (`showDetails`) and opens the Home on its Trailer screen through the new `HomeDestination.Trailer`, so Voltar on the TV lands on the Title's details. TMDB pictures go out as full `image.tmdb.org` URLs. `MissingToken` and `Offline` are answered `missing-token` and `offline`. On the emulator, with the real page and TMDB: Em alta hoje showed the day's Titles, Lupin's screen had its facts and Netflix, and Ver trailer na TV played the trailer on the Home. 5 new tests, 294 in all.
