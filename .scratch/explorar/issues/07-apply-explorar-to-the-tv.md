# 07: Apply Explorar to the TV

**What to build:** Put Explorar on the owner's TV with the owner present, after every other Explorar ticket is done. All steps run from the repo root on the Mac, with the TV at `192.168.100.18:5555`.

1. Put the TMDB token in the git-ignored token file. Dry-run Setup to see the redacted token step, then apply it; the owner types "sim".
2. Publish a release with `scripts/release.sh`, and let the TV's Home update itself from Atualizações.
3. Walk the checklist below with the owner.

If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

**Blocked by:** 01, 02, 03, 04, 05, 06 (all of Explorar)

**Status:** ready-for-human

- [ ] Preparação shows "Explorar" Ativo, and the token is in no file under version control
- [ ] The Home Navigation reaches Explorar, Início and Todos os apps
- [ ] Hoje/Semana and the filters show real trending Titles in Portuguese
- [ ] A movie's and a series' details look like the designs, with Brazil's rating and cast
- [ ] Onde assistir opens an installed service's app
- [ ] A trailer plays with the controls, and one that refuses embedding opens in the YouTube app
- [ ] The Home stays under 150 MB, janky frames stay under 10% on Explorar, and the TV's free memory recovers after leaving a trailer
