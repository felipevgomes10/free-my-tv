# 05: Where to Watch

**What to build:** The details show "Onde assistir": the streaming services that offer the Title in Brazil, as in the details designs.
- **The list:** "Incluído na assinatura" services come first, then "Alugar ou comprar". A service appears once, under its strongest offer, with its variants merged ("Netflix" and "Netflix com anúncios").
- **Selecting a service:** it opens the service's app on the TV, or its Play Store page when the app isn't installed. A service with no TV app is shown but can't be selected.
- **The note:** "Abre a obra no app do streaming. Pode exigir assinatura ou pagamento."
- When no service offers the Title in Brazil, the section is hidden.
- **Mapping:** a table in the domain maps TMDB's services to the TV's packages, by id with a name-prefix fallback. It covers Netflix, Prime Video, Disney+, Globoplay, Max, Apple TV, Crunchyroll, Paramount+, YouTube and Google Play Filmes.

**Blocked by:** 04 (Title details)

**Status:** ready-for-agent

- [x] Services are ordered subscription first, merged by variant, each shown once
- [x] Selecting an installed service opens its app; a missing one opens its Play Store page; an unmapped one isn't selectable
- [x] The section is hidden when there are no offers in Brazil
- [x] Tests cover ordering, merging, the mapping, and what selecting each kind of service does
