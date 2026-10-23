# 05: Weather Widget

**What to build:** The Weather Widget sits compact in the Status Bar: condition icon, current temperature, and today's high and low. Selecting it opens the weather detail: city, current temperature and condition, humidity and chance of rain, the next 12 hours, and the next 7 days with each day's range. Data comes from Open-Meteo (no API key). The location is the city the owner chose, or, when set to automatic, the city resolved from the TV's public IP. Data refreshes periodically and the widget shows the last known values when offline.

Settings for city choice and hiding the widget land in 07; this ticket reads those preferences with automatic city and widget visible as defaults.

Visual reference: `docs/design/weather-detail.png` and the Status Bar in `docs/design/home-app-tray.png`.

**Blocked by:** 01 (Minimal Home with App Tray)

**Status:** ready-for-agent

- [ ] The compact widget shows icon, temperature and today's high/low
- [ ] The detail screen shows humidity, chance of rain, 12 hours and 7 days
- [ ] Location uses the chosen city, falling back to IP-based location when set to automatic
- [ ] When the network is unavailable, last known values are shown and no error banner appears on the Home
- [ ] A Hidden Widget performs no network requests (verifiable through the fake port)
- [ ] Tests cover city vs automatic location, offline behaviour and the hidden-means-no-fetch rule
