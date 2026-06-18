# GoDark

A free, open-source privacy app for Android, for people who care about their
privacy but aren't security engineers.

GoDark blocks ad and tracker domains before they load, encrypts your DNS so your
provider can't see what you look up, shows you in real time what's being blocked,
and warns you whenever an app uses your camera or microphone. Everything runs on
your device. No accounts, no analytics, no servers collecting your data.

No root required.

---

## What it does

**Guard** is your everyday protection. Leave it on. Every DNS lookup your phone
makes is filtered against a tracker/ad/malware blocklist (HaGeZi Multi by
default); matching domains are answered locally with NXDOMAIN, so the tracker
simply fails to load. Clean lookups are forwarded upstream over encrypted DNS
(DNS-over-HTTPS) to a resolver you choose, Quad9, AdGuard, or Cloudflare. Your
phone stays fully usable; only the tracking dies.

**Go Dark** is a panic button. One tap drops *all* network traffic on the phone
(it occupies the VPN slot and blackholes every packet). One more tap restores it.

**Sentinel** is a camera and microphone watchdog. It can't physically disable the
hardware (no app can without root), but it tells you the instant any app opens
the camera or mic. Use while the screen is off triggers a high-priority alert. That's the
genuinely suspicious case Android's little indicator dot can't help with.

**Privacy dashboard** is a local-only record of what GoDark has blocked: totals
for today, this week, and all time, plus your top blocked trackers. The live feed
shows blocks happening in real time. Clear it any time; it never leaves the phone.

---

## What it does NOT do

This matters, and most privacy apps won't tell you plainly, so here it is:

GoDark protects you from **commercial surveillance**, ad networks, trackers,
analytics SDKs, and apps phoning home. That's a real, pervasive problem and it's
what GoDark is good at.

GoDark does **not** protect you from:

- **State-level adversaries.** An app in Android's sandbox cannot defend against
  a sophisticated government attacker. If that's your threat model, you need a
  hardened OS (GrapheneOS) or different tools, not an app.
- **Malware already on your device.** GoDark filters network requests; it is not
  an antivirus and cannot remove or detect installed malware.
- **OS-level or firmware compromise.** Anything below the app layer is out of
  reach by design.

A privacy tool that promises total protection is lying. GoDark does one category
of things well and tells you where its limits are.

---

## Privacy stance

Everything stays local, and the code says so, read it and check.

- No analytics, no accounts, no servers of mine.
- Your logs and stats never leave the device.
- The app's only outbound network activity: fetching the public blocklist (once,
  then cached) and forwarding your DNS queries, encrypted, to the resolver
  *you* chose. Nothing else, nowhere else.
- Open source under GPL-3.0. Don't take my word for any of this; build it
  yourself and verify.

---

## A note on aggressive Android ROMs (Vivo, Xiaomi, etc.)

Some manufacturers (Vivo/OriginOS and Xiaomi/MIUI are the worst offenders) kill
background apps aggressively to save battery, including foreground VPN services
like GoDark's Guard. On these phones, **swiping GoDark away from recents can stop
protection.** This affects every app of this kind, not just GoDark.

To keep Guard running on these devices:
- Allow autostart for GoDark (Vivo: iManager -> Autostart)
- Allow high background power consumption
- Lock GoDark in the recents screen so a swipe doesn't clear it
- Don't swipe it away from recents

GoDark detects when it's been killed and restores protection automatically the
next time you open it, and it never shows "protected" when it isn't. On stock
Android (Pixel, etc.) none of this applies.

---

## Build it yourself

1. Install Android Studio.
2. Clone this repo and open the folder in Android Studio. Let Gradle sync.
3. Enable Developer Options + USB debugging on your phone, plug it in.
4. Press Run.

On first Guard or Go Dark activation, Android shows a one-time VPN consent
dialog, then accept it. First Guard activation downloads the blocklist (once, then
cached).

---

## Roadmap

- Blocklist picker (Normal / Pro / custom)
- Per-app Guard bypass (for apps that break under filtering)
- F-Droid release with reproducible builds

---

## License

GPL-3.0. If you fork or build on GoDark, your version stays open source too.

## Support

GoDark is free and always will be. If it earns a place on your phone, a small
donation keeps it maintained and independent:
**https://liberapay.com/jakemetaxas**

Telling a friend helps just as much.

## Contact

jakemetaxas@proton.me

Jake Metaxas
