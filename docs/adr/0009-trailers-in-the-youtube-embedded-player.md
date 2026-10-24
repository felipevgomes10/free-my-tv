# Trailers play in YouTube's embedded player inside the Home

TMDB lists trailers as YouTube videos. The Explorar design plays them inside the Home with its own controls (back to details, title, progress, 10 s back, pause, 10 s forward), so they play in YouTube's official embedded player (the IFrame Player API) in a WebView, with the player's own controls hidden and the design's drawn on top, driven through the player's JavaScript API. On the owner's TV the WebView is Google's (version 155 on 2026-10-23).

Pulling the video stream out of YouTube to play it natively (as NewPipe does) was rejected: it breaks YouTube's terms and breaks whenever YouTube changes. Always handing the trailer to the YouTube app was the simpler alternative and remains the fallback: when a video refuses embedding (player errors 101 and 150) or the WebView fails, the Home opens the trailer in the YouTube app.

The WebView renders in its own process, so it doesn't count against the Home's 150 MB (ADR 0004), but it does take the TV's RAM: the Home creates it only on the trailer screen and destroys it on leaving.
