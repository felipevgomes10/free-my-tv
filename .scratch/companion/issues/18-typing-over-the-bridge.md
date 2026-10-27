# 18: Typing over the bridge

**What to build:** The bridge's side of the Teclado, as ADR 0007 decides: typing, clearing and submitting into the TV's open text field, and `text-field` pushed when one opens or closes, with its app and hint when known.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 02 (Prototype: typing on the TV), 04 (The bridge speaks protocol 2), 17 (The Teclado)

**Status:** wontfix

- [ ] Text typed on the phone appears in the Home's own field, YouTube's search and Netflix's search (on the TV in 19)
- [ ] The phone learns when a text field opens and closes
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, cancelled by the Owner.** After accessibility, ADB text injection and physical UHID keyboard experiments failed to provide the specified Netflix typing flow, the Owner chose to leave FMTV Controller without Teclado (“Melhor deixar sem”). Tickets 17 and 18 are `wontfix`; no `text` / `text-field` contract or bridge implementation will be added under this scope. See ADR 0007 in free-my-tv-remote-controller for the measured results and decision. The experimental code was removed, and the TV retains its original Home 0.6.4.
