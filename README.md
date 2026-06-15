# GoDark

One button. Three postures.

Open-source privacy app for Android — no root, everything local.

## Modes

| Mode | Ring | What happens |
|---|---|---|
| **EXPOSED** | red | Nothing. You're a normal phone. |
| **GUARD** | green, breathing | Phone fully usable. Every DNS lookup is filtered against HaGeZi Multi (trackers/ads/malware die with NXDOMAIN) and clean queries are forwarded over encrypted DNS (DoH) to the resolver you chose (Quad9 / AdGuard / Cloudflare). Every decision logged locally. |
| **DARK** | white | Total network blackout. Every packet on the phone is dropped. |

Tap the big button to cycle modes. Quick Settings tile = instant DARK.

## Sentinel (independent toggle)

Watches camera + microphone system callbacks. Any activation -> notification.
Activation **while the screen is off** -> high-priority alarm. Timestamped log in-app.

## Privacy stance

Everything stays local, and we say so openly:
- No analytics, no accounts, no servers of ours
- Logs never leave the device
- The app's only network activity: fetching the public blocklist once, and
  forwarding your DNS queries (encrypted) to the resolver you selected

## Build & install

1. Android Studio (Koala+), File -> Open -> this folder, let Gradle sync.
2. Phone: Developer options -> Wireless debugging -> pair with Mac.
3. Run. First GUARD/DARK press shows Android's VPN consent dialog — accept once.
4. First GUARD activation downloads the blocklist (~10 MB, cached after that).

## Roadmap

**Phase 3 — Shizuku**
- [ ] WiFi / Bluetooth / Location / NFC toggles folded into DARK
- [ ] Exact app attribution for Sentinel events (AppOps)
- [ ] Auto-kill apps caught using mic/cam in background

**Phase 4**
- [ ] Per-app whitelist for DARK mode
- [ ] Persist logs (Room) + daily privacy report
- [ ] Blocklist picker (HaGeZi Pro / StevenBlack / custom URL)
- [ ] F-Droid release, reproducible builds, GitHub Sponsors

## License

GPL-3.0 (add LICENSE file before publishing).
