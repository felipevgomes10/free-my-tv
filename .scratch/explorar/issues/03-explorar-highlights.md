# 03: Explorar highlights

**What to build:** Explorar shows TMDB's trending Titles in Portuguese, as in `docs/design/explorar-today.png` and `docs/design/explorar-week.png`.
- **Choices:** **Hoje / Semana** picks the window, **Todos / Filmes / Séries** the kind.
- **The hero:** the focused Title's backdrop (baked once, darkened and faded, like the Top Shelf), name, kind, main genre, rating out of 10, synopsis, and "Ver detalhes". It follows the focus in the row after a short settle, so running along the row doesn't load every backdrop.
- **The row:** "Destaques de hoje" or "Destaques da semana" with its subtitle, cards with backdrop, name, kind, genre and rating, and the hint "Use as setas para explorar. Pressione OK para ver os detalhes."
- **Caching:** highlights are kept per window and kind for an hour while the Home runs.
- **Failures:** offline shows a message, with the last highlights when there are any. A missing token shows a message pointing to Setup. A Title without a Portuguese synopsis shows without an empty line.
- **Credit:** Settings → Sobre gains "Dados de filmes e séries: TMDB".

**Blocked by:** 01 (TMDB token through Setup), 02 (Home Navigation)

**Status:** ready-for-agent

- [ ] Hoje/Semana and Todos/Filmes/Séries change the highlights and the row's headings as in the designs
- [ ] The hero follows the focused card after the settle, without loading the cards passed
- [ ] Highlights are reused within the hour and fetched again after it
- [ ] Offline and missing-token states show their messages
- [ ] Settings → Sobre credits TMDB
- [ ] Tests cover the choices and headings, the settle, the cache, offline with and without earlier highlights, the missing token and empty synopses
