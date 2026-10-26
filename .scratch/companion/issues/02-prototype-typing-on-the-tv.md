# 02: Prototype: typing on the TV

**What to build:** A throwaway prototype, on the owner's TV with the owner present, that decides how the Teclado types into the TV (the spec's "prototype first").
- **Two ways:** `cmd input text` through the Home's ADB connection (`TvAdb`), and accessibility's `ACTION_SET_TEXT` on the focused field from the key filter. A debug-only ADB broadcast triggers each, as the navigation probe did.
- **Three fields:** the Home's own text field (renaming an Input), YouTube's search and Netflix's search, typing "ação", "Coração" and "o último trem", then clearing, then submitting.
- **Telling a text field is open:** whether the input method's state (`dumpsys input_method` over ADB) or accessibility can tell the phone a field is open, which app it's in and its hint, and how fast.
- **Write ADR 0007** in free-my-tv-remote-controller with the choice, what each way did in each field, and what accents become; remove the prototype or keep it debug-only.

If neither way types into YouTube and Netflix, stop: the spec's Teclado is revisited before tickets 17 and 18.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Both ways tried in the Home's field, YouTube's search and Netflix's search, with the results written under Comments
- [ ] What accented letters become with each way is written under Comments
- [ ] How the phone can know a text field is open, and which, is written under Comments
- [ ] ADR 0007 is written in free-my-tv-remote-controller
- [ ] The prototype code is removed or kept only behind a debug-only switch, and the TV is left with its released Home
