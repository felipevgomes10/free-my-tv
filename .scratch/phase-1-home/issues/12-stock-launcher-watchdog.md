# 12: Stock Launcher watchdog

**What to build:** A system update can re-enable the Stock Launcher (ADR 0003). The Home checks whether Setup is still in effect — the Stock Launcher disabled and the Home's extra permissions granted — at boot and periodically. When something has reverted, the owner gets a notification ("O launcher original voltou") through 09, and Settings → Preparação shows which parts of Setup are missing and how to re-run Setup from the computer. When everything is in place, Preparação says Setup is active.

**Blocked by:** 07 (Settings), 09 (Notifications)

**Status:** ready-for-agent

- [ ] Detects the Stock Launcher re-enabled and each missing permission individually
- [ ] Raises one notification per regression, not one per check
- [ ] Settings → Preparação lists what is missing and the re-run instruction, or confirms Setup is active
- [ ] Tests cover each regression kind and notification de-duplication
