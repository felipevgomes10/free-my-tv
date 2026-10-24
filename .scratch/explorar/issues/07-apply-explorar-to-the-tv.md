# 07: Apply Explorar to the TV

**What to build:** Put Explorar on the owner's TV with the owner present, after every other Explorar ticket is done. All steps run from the repo root on the Mac, with the TV at `192.168.100.18:5555`.

1. Put the TMDB token in the git-ignored token file. Dry-run Setup to see the redacted token step, then apply it; the owner types "sim".
2. Publish a release with `scripts/release.sh`, and let the TV's Home update itself from Atualizações.
3. Walk the checklist below with the owner.

If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

**Blocked by:** 01, 02, 03, 04, 05, 06 (all of Explorar)

**Status:** resolved

- [x] Preparação shows "Explorar" Ativo, and the token is in no file under version control
- [x] The Home Navigation reaches Explorar, Início and Todos os apps
- [x] Hoje/Semana and the filters show real trending Titles in Portuguese
- [x] A movie's and a series' details look like the designs, with Brazil's rating and cast
- [x] Onde assistir opens an installed service's app
- [x] A trailer plays with the controls, and one that refuses embedding opens in the YouTube app
- [x] The Home stays under 150 MB, janky frames stay under 10% on Explorar, and the TV's free memory recovers after leaving a trailer (0.3.0: 117 MB resident plus 55 MB swapped out during a trailer, 2.9% janky, free RAM 678 → 480 → 580 MB)

## Comments

**2026-10-23/24, applied.** Explorar shipped in v0.3.0: the TV's Home updated itself, then Setup handed it the TMDB token (the step showed `[token]`; the token is in no commit, journal or APK). The owner walked the checklist.

On the way, from the owner's checks:
- **v0.3.1:**
  - The Trailer played sound over a black screen: the player was 960 × 0 because the WebView starts at zero size. It is now sized in pixels (ADR 0009).
  - Hoje and Semana got more room.
  - Each Up Next entry keeps its own picture: pictures sent before the new title are no longer taken for it.
  - One app keeps at most five Up Next entries.
- **v0.3.2:**
  - The Top Shelf shows the picture in a card at its own size. Stretched, YouTube's 640 × 360 artwork went soft.
  - A YouTube entry whose session is gone opens YouTube's search for its title and channel. YouTube names no video id, so this is as close as reopening gets; the video comes up first and resumes at the owner's position.

On the TV, YouTube's metadata carries TITLE, ARTIST, ALBUM, DURATION and a 640 × 360 ART bitmap: no media id and no art link.

