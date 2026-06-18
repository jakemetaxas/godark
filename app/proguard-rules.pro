# ---- GoDark ProGuard / R8 rules ----

# Keep components referenced from the manifest by name.
-keep class com.godark.app.MainActivity { *; }
-keep class com.godark.app.vpn.GoDarkVpnService { *; }
-keep class com.godark.app.sentinel.SentinelService { *; }
-keep class com.godark.app.tile.GoDarkTileService { *; }

# Keep the DNS engine + packet classes intact. They do low-level byte work and
# are only ever called internally, but we keep them whole to avoid any R8
# surprises with method inlining during packet parsing.
-keep class com.godark.app.dns.** { *; }

# Compose: the Compose compiler + R8 defaults handle this well, but keep these
# to silence warnings and avoid stripping runtime-referenced members.
-dontwarn androidx.compose.**

# Android's built-in JSON (used by Stats) needs no special rules, but be explicit.
-keep class org.json.** { *; }