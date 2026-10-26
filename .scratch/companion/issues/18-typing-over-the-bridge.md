# 18: Typing over the bridge

**What to build:** The bridge's side of the Teclado, as ADR 0007 decides: typing, clearing and submitting into the TV's open text field, and `text-field` pushed when one opens or closes, with its app and hint when known.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 02 (Prototype: typing on the TV), 04 (The bridge speaks protocol 2), 17 (The Teclado)

**Status:** ready-for-agent

- [ ] Text typed on the phone appears in the Home's own field, YouTube's search and Netflix's search (on the TV in 19)
- [ ] The phone learns when a text field opens and closes
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
