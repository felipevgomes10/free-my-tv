# 04: Input Tray

**What to build:** Below the App Tray, the Input Tray lists the TV's Inputs: HDMI 1–3 and the antenna. Live channels provided by apps (Globoplay live, TCL Channel+, Google TV previews) are never Inputs. An Input shows its HDMI-CEC device name when one is known ("PS5"), otherwise its port name; an HDMI port with nothing connected is dimmed with "Nada conectado". Pressing OK switches the TV to that Input. In Edit Mode (from 03) an Input can be moved, renamed and hidden; hidden Inputs can be shown again from the same menu on any Input. When focus scrolls down to the Input Tray, the top of the Home recedes so all trays fit.

On the owner's TV the Inputs are exposed by `com.tcl.tvinput` (`TvPassThroughService/HW15..17` for HDMI, `TunerInputService/HW2` for the antenna); an extra `HDMI300004` input exists whose meaning is unknown — treat it as a CEC device on an HDMI port if it resolves to one, otherwise leave it out.

Visual reference: `docs/design/home-input-tray.png`.

**Blocked by:** 03 (App Tray Edit Mode)

**Status:** ready-for-agent

- [x] Only physical Inputs (HDMI ports and the antenna) appear; app-provided channel inputs never do
- [x] CEC device names are used when available; disconnected HDMI ports are dimmed
- [x] OK switches to the Input through the platform port
- [x] Edit Mode supports Move, Rename and Hide for Inputs; names, order and hidden state persist
- [x] Tests cover the input filtering rule, CEC naming, rename, hide and unhide
