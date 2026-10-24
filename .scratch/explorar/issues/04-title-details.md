# 04: Title details

**What to build:** "Ver detalhes" on the hero, or OK on a card, opens the Title's details, as in `docs/design/explorar-movie-details.png` and `docs/design/explorar-series-details.png`.
- **The top:** the backdrop, an "Explorar" pill and the name in large type.
- **Brazil's age rating** as a ClassInd-colored badge: L green, 10 blue, 12 yellow, 14 orange, 16 red, 18 black.
- **The facts:** kind, year and genre; a movie's duration ("2 h 08 min"), or a series' "48 min/episódio" and "2 temporadas"; the audience rating ("8,4 / 10 Avaliação do público").
- **Below:** the full synopsis, and the first four of the cast with their characters.
- Fields TMDB doesn't have are left out.
- The details come in one TMDB request per Title.
- Back, or the "Explorar" pill, returns to Explorar on the card the owner left.

**Blocked by:** 03 (Explorar highlights)

**Status:** ready-for-agent

- [ ] A movie and a series show their details as in the designs
- [ ] Brazil's rating comes from release dates for movies and content ratings for series; a missing one hides the badge
- [ ] Unknown duration, episode length, seasons or cast are left out, never shown as 0 or empty
- [ ] Back returns to Explorar with focus on the same card
- [ ] Tests cover the rating rules, the duration and season texts, and missing fields
