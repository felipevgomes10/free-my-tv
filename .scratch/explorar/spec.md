# Explorar

**Status:** ready-for-agent

## Problem Statement

The owner has a pile of streaming apps on the TV but no single place to see what is worth watching. Finding something new means opening Netflix, then Prime Video, then Globoplay, scrolling each app's own feed, and still not knowing whether a title everyone talks about is on any of the subscriptions. Checking a trailer, the age rating or the cast means picking up the phone. The Home itself only knows the apps and what was already watched in them; it says nothing about what is out there.

## Solution

A new section of the Home, **Explorar**, reached from a **Home Navigation** row under the Status Bar (Início, Explorar, Todos os apps).

**Highlights.** Explorar shows TMDB's trending **Titles**:
- **Hoje** or **Semana**, filtered to **Todos**, **Filmes** or **Séries**.
- A large hero at the top: the focused Title's backdrop, name, kind, genre, rating and synopsis, and "Ver detalhes".
- A row of highlights below ("Destaques de hoje", "Destaques da semana"). Moving across it changes the hero.

**Details.** Selecting a Title opens its details:
- Brazil's age rating, kind, year and genre.
- Duration for a movie; minutes per episode and number of seasons for a series.
- The audience rating, the synopsis and the cast with their characters.
- **Onde assistir**: the streaming services that offer the Title in Brazil, each marked "Incluído na assinatura" or "Alugar ou comprar". Selecting one opens its app on the TV, or the Play Store when the app isn't installed.
- **Assistir trailer**.

**Trailer.** The Trailer plays inside the Home (ADR 0009), full screen, with the design's controls:
- "Voltar aos detalhes", the title and "Trailer oficial".
- A progress bar with the elapsed and total time.
- 10 s back, play/pause and 10 s forward.

A Trailer YouTube won't let be embedded opens in the YouTube app instead.

**Data.** Everything comes from TMDB, in Portuguese and for Brazil, with the owner's read access token. Setup hands the token to the TV (ADR 0008). It is never in the repository or in the APK the TV updates from.

## User Stories

### Home Navigation

1. As the owner, I want a row with Início, Explorar and Todos os apps under the Status Bar, so that I can move between the Home's sections.
2. As the owner, I want the section I'm in highlighted, so that I always know where I am.
3. As the owner, I want pressing up from the App Tray to reach the Home Navigation, so that switching sections is a couple of presses.
4. As the owner, I want "Todos os apps" in the Home Navigation to open All Apps, so that it's reachable without scrolling to the end of the App Tray.
5. As the owner, I want Back from Explorar to return to Início, so that Back behaves as everywhere else on the Home.
6. As the owner, I want the Home to keep opening on Início when I press Home, so that my everyday apps stay one press away.

### Highlights

7. As the owner, I want Explorar to show what's trending today, so that I discover what people are watching right now.
8. As the owner, I want to switch to the week's trending titles, so that I see what stood out over more than a day.
9. As the owner, I want to filter to movies only or series only, so that I see what fits my mood.
10. As the owner, I want a large hero with the focused Title's backdrop, name, kind, genre, rating and synopsis, so that I can judge it without opening it.
11. As the owner, I want the highlight row's title and subtitle to follow the choice ("Destaques de hoje", "Filmes e séries para descobrir" / "Destaques da semana", "As obras mais vistas nesta semana"), so that it's clear what I'm looking at.
12. As the owner, I want each card to show the backdrop, the name, the kind, the main genre and the rating, so that I can scan the row quickly.
13. As the owner, I want the hero to follow my focus along the row without lagging, so that browsing feels smooth on this TV.
14. As the owner, I want "Ver detalhes" on the hero and OK on a card to open the Title's details, so that both paths lead to the same place.
15. As the owner, I want a hint "Use as setas para explorar. Pressione OK para ver os detalhes." under the row, so that I know how to use the screen.
16. As the owner, I want everything in Portuguese (titles, synopses, genres), so that it reads like the rest of the Home.
17. As the owner, I want titles without a Portuguese synopsis to still show, without an empty synopsis, so that nothing looks broken.
18. As the owner, I want a clear message when the TV is offline, with the last highlights if there are any, so that Explorar never shows an empty screen without a reason.
19. As the owner, I want a clear message when the TMDB token is missing that points to running Setup, so that I know how to fix it.
20. As the owner, I want highlights loaded once and reused for a while, so that going back and forth doesn't hit TMDB or the network every time.

### Details

21. As the owner, I want the Title's name in large type with its backdrop behind it, so that the details screen looks like the design.
22. As the owner, I want Brazil's age rating as a colored badge (L, 10, 12, 14, 16, 18), so that I know whether it suits who's watching.
23. As the owner, I want the kind, year and genre, so that I place the Title at a glance.
24. As the owner, I want a movie's duration ("2 h 08 min"), so that I know if it fits my evening.
25. As the owner, I want a series' minutes per episode and number of seasons, so that I know the commitment.
26. As the owner, I want the audience rating out of 10 ("Avaliação do público"), so that I get a sense of quality.
27. As the owner, I want the full synopsis, so that I know what it's about.
28. As the owner, I want the first four of the cast with their characters, so that I recognize who's in it.
29. As the owner, I want "Onde assistir" listing the services that offer it in Brazil, so that I know where to watch it.
30. As the owner, I want each service marked "Incluído na assinatura" or "Alugar ou comprar", so that I know if it costs extra.
31. As the owner, I want a service's variants merged ("Netflix" and "Netflix com anúncios"), so that the list has no duplicates.
32. As the owner, I want selecting a service to open its app on the TV, so that I can go watch it.
33. As the owner, I want a service whose app isn't installed to open its Play Store page, so that I can install it.
34. As the owner, I want a service that has no TV app to be shown without being selectable, so that I don't hit a dead end.
35. As the owner, I want the note "Abre a obra no app do streaming. Pode exigir assinatura ou pagamento.", so that I know what to expect.
36. As the owner, I want "Onde assistir" hidden when no service offers the Title in Brazil, so that the screen doesn't show an empty section.
37. As the owner, I want fields TMDB doesn't have (age rating, duration, cast) simply left out, so that nothing reads "null" or "0 min".
38. As the owner, I want Back, or the "Explorar" pill, to return to Explorar where I was, so that I don't lose my place.

### Trailer

39. As the owner, I want "Assistir trailer" on the details, so that I can watch the trailer without picking up my phone.
40. As the owner, I want the Portuguese official trailer when there is one, then an English one, then a teaser, so that I get the best preview available.
41. As the owner, I want "Assistir trailer" hidden when the Title has no trailer, so that it never leads nowhere.
42. As the owner, I want the trailer full screen with the title and "Trailer oficial", so that it looks like the design.
43. As the owner, I want play/pause, 10 s back and 10 s forward with the remote, so that I control it like any player.
44. As the owner, I want a progress bar with elapsed and total time, so that I know how long it is.
45. As the owner, I want the controls to hide after a few seconds and come back on any key, so that they don't cover the video.
46. As the owner, I want Back, or "Voltar aos detalhes", to stop the trailer and return to the details, so that I can keep browsing.
47. As the owner, I want a trailer that can't play inside the Home to open in the YouTube app, so that I can still watch it.
48. As the owner, I want the trailer to stop when I press Home or the TV sleeps, so that it doesn't keep playing behind other apps.
49. As the owner, I want the trailer player to free its memory when I leave it, so that the TV stays fast.

### Setup, Restore and the token

50. As the owner, I want to keep the TMDB token in a file on my Mac that Git never sees, so that it never ends up in the public repository.
51. As the owner, I want Setup to hand the token to the TV, so that Explorar works without my typing anything with the remote.
52. As the owner, I want Setup never to print the token or write it to its journal, so that it doesn't leak through the terminal or the journal file.
53. As the owner, I want Setup to tell me plainly when the token file is missing, and still do the rest, so that Explorar is the only thing waiting on it.
54. As the owner, I want Restore to make the Home forget the token, so that Restore still returns the TV to how it was.
55. As the owner, I want Settings → Preparação to show whether Explorar has its token, so that I know whether Setup ran with it.
56. As the owner, I want the token never inside the APK the TV updates from, so that publishing releases on GitHub doesn't expose it.
57. As the owner, I want no other app on the TV able to hand the Home a token, so that nobody can point Explorar at another account.

## Implementation Decisions

- **One seam.** `TvPlatform` stays the only seam between the Home and Android. New capabilities on it:
  - **Trending Titles** for a window (day or week) and a kind (all, movies, series): each with id, kind, name, synopsis, genres, rating and backdrop. Null when offline. A distinct answer when there is no token.
  - **A Title's details**: name, kind, year, genres, synopsis, rating, Brazil's age rating, a movie's duration or a series' episode length and season count, the cast with characters, the videos and the Brazil watch offers. All in one request per Title, appending ratings, videos, credits and watch providers to the details call.
  - **Opening an app's Play Store page** by package (there is already opening an app).
  - **The TMDB token**: whether the Home holds one.
- **Explorar state holder** (in the shape of `UpdatesStateHolder` and `WeatherWidget`):
  - It owns the window, the kind filter, the highlighted Title and the load state: loading, shown, offline with or without what was shown last, missing token.
  - Highlights are cached per window and kind for an hour while the Home runs.
  - The hero follows the focused card with the same short settle used for the Top Shelf, so running along the row doesn't load every backdrop.
- **Title details holder**, with its rules:
  - **Trailer choice:** an official YouTube "Trailer" in Portuguese, then any Portuguese trailer, then English, then a teaser.
  - **Age rating:** Brazil's rating for movies (from release dates, the theatrical or first non-empty one) and for series (from content ratings). The badge colors follow ClassInd: L green, 10 blue, 12 yellow, 14 orange, 16 red, 18 black.
  - **Duration text:** "2 h 08 min", "48 min/episódio" and "2 temporadas", each left out when unknown.
  - **Where to Watch:** subscription offers first, then rent and buy, a service appearing once, under its strongest offer.
- **Streaming services → TV apps.** A small table in the domain maps TMDB's services to the TV's packages, by provider id with a name-prefix fallback so that ad-supported variants merge:
  - Netflix → `com.netflix.ninja`; Prime Video → `com.amazon.amazonvideo.livingroom`; Disney+ → `com.disney.disneyplus`; Globoplay → `com.globo.globotv`; Max → `com.wbd.stream`.
  - Apple TV → `com.apple.atve.androidtv.appletv`; Crunchyroll → `com.crunchyroll.crunchyroid`; Paramount+ → `com.cbs.ca`; YouTube → `com.google.android.youtube.tv`; Google Play Filmes → `com.google.android.videos`.
  - A service without a mapping is shown but not selectable.
- **Images** come from TMDB's image CDN at the size each place needs:
  - The hero and the details are backdrops baked once, darkened and faded, like the Top Shelf (ADR 0004).
  - Cards are decoded near their size.
  - Only the visible screen's bitmaps are kept.
- **Home Navigation.** A row under the Status Bar on Início and Explorar, as in `docs/design/home-navigation.png`. Início is today's Home. Todos os apps opens All Apps. Pressing up from the App Tray, or from Explorar's row of highlights, reaches it. Explorar's Back returns to Início.
- **Trailer player** (ADR 0009):
  - A screen with a WebView running YouTube's IFrame Player API, with YouTube's controls off. The design's controls are drawn in Compose over it and drive the player through its JavaScript API: play, pause, seek ±10 s, and current time and duration read twice a second.
  - The controls hide after 4 s without a key.
  - Player errors 101 and 150 (embedding refused), or a WebView failure, hand the video to the YouTube app and close the screen.
  - The WebView is created on entering the screen and destroyed on leaving, and paused when the Home stops.
  - Debug builds may log the player's state; nothing about it goes through `TvPlatform` except opening the YouTube app.
- **Token** (ADR 0008):
  - Setup reads the token from a git-ignored file in the repository's config folder (or an environment variable) and sends it in a broadcast to a receiver guarded by `WRITE_SECURE_SETTINGS`.
  - The Home keeps it in private storage and sends it as a bearer header, never in a URL.
  - Setup's plan and journal show the step redacted and store no token. Restore's inverse is a broadcast telling the Home to forget it.
  - With no token file, Setup skips the step and says so.
  - Preparação gains an "Explorar" check; the Setup Watchdog doesn't announce it, since it can't be lost to a system update.
- **Glossary and ADRs:** Home Navigation, Explorar, Title, Where to Watch and Trailer are as defined in `CONTEXT.md`. ADR 0008 covers the token, ADR 0009 the trailer player.

## Testing Decisions

- **What a good test is.** As in Phases 1 and 2: drive the state holders through `FakeTvPlatform` and assert what the owner would see, and which platform calls were made (opening an app, the Play Store, the YouTube app). Time is controlled with the coroutine test scheduler.
- **Explorar tests:**
  - the window and kind choices and the headings they bring;
  - the hero following the focus after the settle, and not loading the cards passed;
  - the one-hour cache;
  - offline with and without earlier highlights;
  - missing token;
  - Portuguese titles without a synopsis.
- **Details tests:**
  - trailer choice in order, and no trailer;
  - Brazil's rating for movies and series, and missing;
  - duration and season text, with unknown values left out;
  - Where to Watch: merged variants, subscription before rent or buy, mapped and unmapped services;
  - selecting an installed app opens it, and a missing app opens the Play Store.
- **Trailer tests:** the choice of video, and the fallback to the YouTube app on embedding errors, at the state-holder level. The WebView itself is checked on the emulator and on the TV, not in JVM tests.
- **Setup/Restore tests** in `test_tvsetup.py`:
  - the token step appears redacted in the printed plan and is absent from the journal;
  - Restore sends the forget broadcast;
  - a missing token file skips the step with a message;
  - the token never appears in any command the plan prints.
- **Prior art:** `UpdateSourcesTest` (network lookups with offline cases), `TopShelfTest` (settle timing), `UpNextTrayTest` (state from the platform), `SetupWatchdogTest` (a check that isn't announced), `test_tvsetup.py` (plans and journal).

## Out of Scope

- Searching TMDB, lists by genre, recommendations or "because you watched".
- Opening a Title directly inside a streaming app: TMDB offers no deep links, so services open their app's home.
- Watchlists, ratings by the owner, or syncing with a TMDB account.
- Playing anything other than trailers; extracting YouTube streams (ADR 0009).
- Linking Explorar with the Up Next Tray or the Top Shelf.
- Typing the token with the remote, and any server or proxy holding the token.
- Other regions or languages than Brazil and Portuguese.

## Further Notes

- Checked against TMDB with the owner's token on 2026-10-23:
  - trending returns 20 Titles per page, with Portuguese synopses (some empty);
  - details with appended data return Brazil's ratings ("18" for a movie, "16" for a series), YouTube trailers in Portuguese or English, cast, and Brazil's watch offers (Lupin: Netflix and "Netflix Standard with Ads");
  - a series may have no episode run time.
- The owner's TV has Google's WebView 155, which plays the IFrame player. Its renderer process costs RAM outside the Home's 150 MB, so the trailer screen must not keep it alive.
- Visual references in `docs/design/`: `home-navigation.png`, `explorar-today.png`, `explorar-week.png`, `explorar-movie-details.png`, `explorar-series-details.png` and `explorar-trailer.png`.
- TMDB's terms ask for attribution. Settings → Sobre gains "Dados de filmes e séries: TMDB", with TMDB's logo.
