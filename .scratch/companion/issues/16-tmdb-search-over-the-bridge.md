# 16: TMDB search over the bridge

**What to build:** TMDB's `search/multi` in `pt-BR` added to the Home's `Tmdb`, movies and series only, with Explorar's token (ADR 0008); the bridge answers `search` with the results in the examples' shape.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 14 (Explorar over the bridge), 15 (Search)

**Status:** resolved

- [x] A search returns movies and series, never people, in Portuguese
- [x] No token and offline are answered as such
- [x] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on `companion`.** `Tmdb.search` asks `search/multi` in `pt-BR` without adult titles and keeps movies and series only, each with its year from the release or first air date, backdrop and poster; it reaches the bridge through `TvPlatform.searchTitles`. `addExplorar` answers `search` with `search-results` (the query as sent, what was typed trimmed before searching), TMDB pictures as full URLs (`w780` backdrops, `w342` posters), and `missing-token` or `offline` when TMDB can't answer. On the emulator, with the real page and TMDB: "duna" listed the films and the series with their years and pictures as typing paused. 2 new tests, 296 in all.
