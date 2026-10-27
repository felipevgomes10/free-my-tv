# 02: Prototype: typing on the TV

**What to build:** A throwaway prototype, on the owner's TV with the owner present, that decides how the Teclado types into the TV (the spec's "prototype first").
- **Two ways:** `cmd input text` through the Home's ADB connection (`TvAdb`), and accessibility's `ACTION_SET_TEXT` on the focused field from the key filter. A debug-only ADB broadcast triggers each, as the navigation probe did.
- **Three fields:** the Home's own text field (renaming an Input), YouTube's search and Netflix's search, typing "ação", "Coração" and "o último trem", then clearing, then submitting.
- **Telling a text field is open:** whether the input method's state (`dumpsys input_method` over ADB) or accessibility can tell the phone a field is open, which app it's in and its hint, and how fast.
- **Write ADR 0007** in free-my-tv-remote-controller with the choice, what each way did in each field, and what accents become; remove the prototype or keep it debug-only.

If neither way types into YouTube and Netflix, stop: the spec's Teclado is revisited before tickets 17 and 18.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Both ways tried in the Home's field, YouTube's search and Netflix's search, with the results written under Comments
- [x] What accented letters become with each way is written under Comments
- [x] How the phone can know a text field is open, and which, is written under Comments
- [x] ADR 0007 is written in free-my-tv-remote-controller
- [x] The prototype code is removed or kept only behind a debug-only switch, and the TV is left with its released Home

## Comments

**2026-10-26, prototype in progress, Owner requested before ticket 17.** Built and installed the debug-only `TypingPrototype`; its broadcast requires the ADB shell's DUMP permission. `scripts/probe-tv-typing.py` invokes it and records each observation. The released Home 0.6.4 APK was saved at `/tmp/fmtv-before-typing-prototype.apk` before installation, for restoration without clearing data.

The Home's Renomear entrada field accepted `ação`, `Coração`, `o último trem` and empty text through accessibility. `ACTION_IME_ENTER` submitted an empty name, restoring the default HDMI 1 label. Raw accents failed over the Home's own ADB key with `InputShellCommand.sendText` / `NullPointerException`; plain `o ultimo trem` worked. Its Compose virtual EditText exposes editable/focused and the set-text / IME-enter actions, but no hint. `findFocus(FOCUS_INPUT)` alone misses it; traversal finds it. The snapshots after set-text can still show the old text; the next read verifies the update.

YouTube's custom search grid accepted ADB ASCII, lowercasing `Coracao`. It exposed no editable node, and `input_method` still reported `inputType=0x0`, no served connection, no hint and no keyboard shown. Accessibility set/clear/submit failed. Enter activated the grid's selected A instead of submitting; Ctrl+A/Delete did not clear; Clear/Search keys did not supply the missing actions. Its initial black screen recovered after reopening the app.

Netflix opened, but ADB screenshots returned no image and its accessibility tree exposed no labels. Waiting for the Owner to open Netflix's search with the Remote before typing into it. A proposed ADR 0007 records confirmed results and the remaining compatibility/scope decision in free-my-tv-remote-controller; no production text contract has been chosen.

The original Home 0.6.4 APK was restored after the experiment, without clearing app data. Both debug and release builds compile; the release APK's DEX contains no typing-prototype broadcast action. To resume the Netflix experiment, reinstall the debug APK, start the Home so its key filter registers the protected probe, then have the Owner open Netflix's search with the Remote. The Owner must confirm the visible text because Netflix supplied neither a usable screenshot nor accessible field labels. Afterward, restore the saved original APK again. Ticket 02 stays open until that test and the keyboard's supported scope are settled.

**2026-10-26, Netflix experiment completed.** The Owner opened Netflix's search and authorized the test. Reinstalling the debug APK brought the Home forward; the first samples showed the Home package and were not treated as Netflix results. `am task focus 14107` returned to the existing Netflix task, preserving the search. The resumed Activity and every actual test's `before.roots` confirmed `com.netflix.ninja`.

Accessibility could not find an editable field: `ação`, `Coração`, `o último trem`, empty text and IME submit all returned false. `dumpsys input_method` showed `inputType=0x0`, `mInputShown=false`, no hint and no served input connection while the search was open. Raw ADB Unicode failed for all three strings with `InputShellCommand.sendText` / `NullPointerException`. ASCII `acao` returned exit 0, but the Owner explicitly confirmed: **“Não apareceu texto.”** Clear and submit are not supported by this evidence. The original Home 0.6.4 was restored again after the experiment.

**2026-10-26, Owner-authorized UHID follow-up.** `scripts/probe-tv-uhid.py` drove the TV's native `/system/bin/hid -` through an interactive ADB stream. No APK or external package was installed. Android registered an external physical alpha keyboard (`FMTV UHID typing prototype`, USB bus, Generic.kl/Generic.kcm). `getevent` confirmed KEY_A and KEY_ENTER down/up; InputDispatcher confirmed Netflix's window had focus. In the search the Owner opened, `acao` did not appear. Enter selected a letter in the grid, as confirmed by the Owner. UHID delivers real events but does not supply this Netflix text-entry flow.

The positive control typed `Coracao` and `o ultimo trem` in the Home's Renomear entrada field. Repeated Backspace emptied it and Enter submitted the empty name, returning to HDMI 1. Ctrl+A was unreliable while TCL's IME was composing; experimental Generic.kcm Alt/dead-key reports for `ação` produced `aao`, so accents remain unvalidated under the current TCL IME. No layout or input-method setting was changed. YouTube was not retested with UHID. The stream was closed, its device disappeared from dumpsys input, the temporary TV UI dump was removed, and the Home stayed on release 0.6.4.

## Answer

Neither original path implements the specified Teclado across the target apps. Accessibility supports the Home's standard field with intact Unicode, clear and IME submit. ADB supports ASCII typing in YouTube, but its custom grid prevents reliable field detection and generic clear/submit. Neither path typed into the Owner's Netflix search, and the subsequent physical UHID keyboard experiment did not type into it either. ADR 0007 records the decision to stop tickets 17/18 under this ticket's explicit condition and revise their supported scope or investigate another integration with the Owner. The prototype is complete; the production keyboard remains unimplemented.

**Final Owner decision, 2026-10-26.** “Melhor deixar sem”: Teclado is out of scope; tickets 17 and 18 are `wontfix`. All typing probe source files, CLI scripts and key-filter hooks were removed. The findings remain documented, and the original Home 0.6.4 is already on the TV. No further typing experiment or integration is pending under this feature.
