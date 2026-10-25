# Where to Watch opens the Title itself, through Wikidata's ids, where each app allows

Choosing a service under Where to Watch used to open only its app, leaving the owner to search for the Title. TMDB says where a Title streams but not its id at each service, and its links lead to JustWatch's pages, not to the apps. Wikidata, which TMDB links each Title to (`external_ids.wikidata_id`), keeps those ids for most services. So the Title's details ask Wikidata for them, and Where to Watch tries, in order: the app's own link to the Title, the app's search with the Title's name, then just opening the app.

Which links each app follows was tried on the owner's TV (2026-10-24), with the app open and closed:

| Service | Title link (Wikidata property) | Search link |
|---|---|---|
| Globoplay | works (P12356), after the profile choice | not followed |
| Max | works (P8298, "show/…" or "movie/…") | hangs |
| Apple TV | works (P9586 movies, P9751 series) for Titles in its catalog | opens empty |
| Crunchyroll | works (P11330; P4110 is deprecated and holds old slugs) | hangs on its opening screen |
| Disney+ | ignored: Wikidata's ids (P7595/P7596) are its old ones, and its new links ask for others | works, name typed in |
| YouTube | — | works, name typed in |
| Netflix | ignored in every form tried (P1874) | — |
| Prime Video | ignored (P8055) | opens empty, sometimes not at all |

Only the ones that work are used; the rest open the app as before. A link to an app that is already open only brings it to the front and is never read, so links start the app afresh (`FLAG_ACTIVITY_CLEAR_TASK`), Watch Next's links included. Wikidata knows fewer of the newest Titles (about a third of the week's trending ones), so a search or the app alone remains common for those. Each lookup adds one Wikidata request to a Title's details, which fails silently: without it the app just opens.
