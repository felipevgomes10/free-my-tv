# Up Next reads Android TV's Watch Next, with media sessions as the fallback

Supersedes ADR 0007.

ADR 0007 concluded that other apps' Watch Next programs were out of reach: reading them seemed to take `ACCESS_ALL_EPG_DATA`, which is `signature|privileged`. Android 12's TV provider says otherwise. A caller without that permission but holding `READ_TV_LISTINGS` gets its own rows **or any row marked searchable**, and Watch Next programs are searchable by default. `READ_TV_LISTINGS` is an ordinary runtime permission, so Setup grants it with `pm grant` like the others. The shell holds no such permission, which is why `content query` returned nothing before.

A probe in 0.4.4 counted what the owner's TV lists (2026-10-24): Globoplay 14 programs, Disney+ 13, Crunchyroll 11, Max 4, Netflix 4; YouTube and Prime Video none. These are the apps whose media sessions said least (Netflix and Globoplay name nothing), and Watch Next has what sessions never had: the episode, the app's own picture, and a link that opens the title itself, all kept by the app even while it is closed.

So the Up Next Tray now reads Watch Next whenever the Home shows. An app that lists anything there is told by Watch Next alone: its media-session entries are dropped and no longer recorded. Programs the owner is watching or whose next episode is up count, not watchlists or new episodes. Apps that list nothing (YouTube, Prime Video) still come from media sessions, as ADR 0007 described.

- **Opening** goes through the app's link (`intent_uri`), made safe first: it must open the app that listed it, grants none of the Home's URIs and carries no selector. If it doesn't open, the app opens instead.
- **Pictures** are fetched once per address, downscaled to 640 px and kept in the Home's files; those no program uses any more are deleted.
- **Removing** an entry can't touch the app's own list, so the Home remembers the removal and hides the entry until the app lists it as watched again since.
