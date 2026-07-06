# GoDark Privacy Policy

Last updated: 6 July 2026

GoDark is a privacy app for Android developed by Jake Metaxas. This policy explains, plainly, what GoDark does and does not do with your information. The short version: everything happens on your device, and no personal data is ever collected, stored on any server, or shared with anyone.

## No data collection

GoDark does not collect, transmit, or sell any personal data. There are no user accounts, no analytics, no advertising, and no tracking of any kind. The developer has no servers that receive your data, because no data is ever sent.

## What stays on your device

All of GoDark's activity happens locally on your phone:

- Blocked and allowed DNS lookups shown in the live log
- Statistics in the privacy dashboard (totals and top blocked domains)
- Camera and microphone activity alerts from the Sentinel feature

This information is stored only on your device and never leaves it. You can clear it at any time from within the app. Uninstalling GoDark removes it entirely.

## VPN permission

GoDark uses Android's VpnService permission to filter DNS and, when you choose, to block network traffic. This is a local mechanism. GoDark is not a VPN in the sense of routing your traffic through a remote server. No browsing traffic, DNS queries, or any other data is logged, stored, or sent to the developer or any third party. The VPN permission is used solely to inspect DNS requests on your device and block known tracker and ad domains locally.

## Encrypted DNS

When filtering is active, DNS queries that are not blocked are forwarded to a public encrypted DNS resolver that you select in the app (for example Quad9, AdGuard, or Cloudflare). These queries go directly from your device to your chosen resolver over an encrypted connection. GoDark does not see, log, or store them. The privacy practices of the resolver you choose are governed by that provider.

## Camera and microphone access

GoDark's Sentinel feature monitors when other apps use your device's camera or microphone, so it can alert you. GoDark itself does not access, record, capture, or transmit any camera or microphone content. It only detects that another app has activated the hardware, and shows you a local alert.

## Blocklist downloads

To keep its tracker and ad blocklist current, GoDark downloads a public blocklist file from its source. This is a normal file download and does not send any personal information about you.

## Children's privacy

GoDark does not knowingly collect any information from anyone, including children. Because it collects no personal data at all, it poses no data-collection risk to users of any age.

## Changes to this policy

If this policy changes, the updated version will be posted at this address with a revised date.

## Contact

For any questions about this policy or about GoDark, contact:

Jake Metaxas
jakemetaxas@proton.me

Source code: https://github.com/jakemetaxas/godark