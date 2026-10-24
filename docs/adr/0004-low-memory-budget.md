# Design for a 2 GB, 32-bit TV

The target TV (TCL G09, Amlogic, `armeabi-v7a`) has 2 GB of RAM with roughly 580 MB free under normal use, so the Home must stay under 150 MB in use. This is why the UI has no live background blur: translucent "glass" surfaces are plain translucent fills, and Explorar's backdrops are darkened and faded once per image, ahead of time, so a frame draws a single picture; the Top Shelf shows the apps' small artwork in a card at its own size rather than stretched. Top Shelf images are decoded at 1080p even though the panel is 4K.
