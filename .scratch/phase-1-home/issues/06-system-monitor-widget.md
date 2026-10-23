# 06: System Monitor Widget

**What to build:** The System Monitor Widget sits compact in the Status Bar showing storage used (percentage), RAM in use out of total, the Home's own frame rate, and the display's refresh rate. Selecting it opens the detail screen: storage headline with a meter and the five apps using the most storage, each offering Uninstall (reusing 02); memory headline with a meter and the five apps using the most RAM, each offering Close for apps in the background; and a bar chart of the Home's frame rate over the last 60 seconds with the display refresh rate alongside, frames below target highlighted.

Close uses the platform's "kill background processes" ability, which never affects the app in the foreground. Meters turn to the attention color above 80%.

Visual reference: `docs/design/system-monitor-detail.png`.

**Blocked by:** 02 (All Apps with Pin, Unpin, Install and Uninstall)

**Status:** ready-for-agent

- [ ] Compact widget shows storage %, RAM used/total, Home fps and display Hz
- [ ] Detail lists the top 5 apps by storage with Uninstall, and the top 5 by RAM with Close
- [ ] Close never targets the foreground app or apps playing media
- [ ] The 60-second frame chart updates live while the detail is open and stops measuring when closed
- [ ] A Hidden Widget takes no measurements (verifiable through the fake port)
- [ ] Tests cover ranking, the Close eligibility rule, and the hidden-means-no-measuring rule
