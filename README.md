# Kavach - Ad & Tracker Blocker for Android

Kavach is a **local, whole-phone ad and tracker blocker**. It runs a DNS-only VPN
(using Android's `VpnService`) that filters ad, tracker, analytics, and malware
domains for **every app** on the device, not just one browser. Nothing is sent to
a remote server - the tunnel is entirely on your phone.

## How it works

```
any app -> asks for ads.example.com
        -> Kavach (local DNS resolver) checks the blocklist
        -> listed?  answer 0.0.0.0  -> ad never loads
        -> not listed? forward to your DNS and relay the answer back
```

The same idea as Pi-hole, running on the phone. Because it filters at the DNS
layer, it works across all apps at once.

## Features

- System-wide blocking via a local DNS filter (no root, no remote VPN)
- Blocklists: StevenBlack, OISD, AdAway, Peter Lowe, 1Hosts (enable/disable each)
- Custom **block** and **allow** rules (allow always wins)
- Per-app control (choose apps that should bypass filtering)
- Upstream resolver of your choice (blank = your network's DNS)
- Blocked log, session counter, start-on-boot, IPv4 + IPv6

## What it can and cannot do - please read

Kavach blocks ads that are served from **separate ad/tracker domains**. That
covers the large majority of ads and trackers in apps and browsers.

It **cannot** remove ads that a service serves from its **own** domain - most
importantly **YouTube in-app ads** (served from the same Google domains as the
video, so DNS blocking cannot separate them). This is a real limit of every
DNS-based blocker, not a bug. For YouTube you would need an in-browser blocker
(watching on the web) or a subscription. Kavach will not break YouTube by
blocking its domains.

It also cannot filter apps that use their **own hard-coded DNS** (rare), and it
is not an antivirus or an anonymity tool.

## Build

Requirements: Android Studio (Koala+) or a JDK 17 + Android SDK, and internet for
the first Gradle sync.

```bash
# Android Studio: File > Open > this folder, then Run.
# Or from the command line:
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

If `gradlew` is not executable on your machine: `chmod +x gradlew`.

### Automatic builds with GitHub Actions

`.github/workflows/android.yml` builds a debug APK on every push and uploads it
as an artifact. Push this project to GitHub and download the APK from the
Actions tab.

### Release signing (never commit keys)

The release build reads signing values from the environment:

```bash
export KAVACH_KEYSTORE=/path/to/kavach.jks
export KAVACH_KEYSTORE_PASSWORD=...
export KAVACH_KEY_ALIAS=kavach
export KAVACH_KEY_PASSWORD=...
./gradlew assembleRelease
```

Create the keystore once with:

```bash
keytool -genkeypair -v -keystore kavach.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias kavach
```

For CI, base64-encode the keystore and store it as a repository secret. No key is
ever written into the source.

## Install

1. Build the APK (above) or download the CI artifact.
2. Install it on your phone (enable "install unknown apps" for your file manager).
3. Open Kavach, flip the switch, and approve the VPN prompt.
4. Tap **Update blocklists** once (needs internet). After that it works offline.

## Project layout

```
app/src/main/java/com/kavach/
  KavachApp.kt              application wiring
  MainActivity.kt           Compose entry + bottom navigation
  dns/IpPacket.kt           IPv4/IPv6 + UDP parse/build
  dns/DnsMessage.kt         DNS query parse + sinkhole response
  service/KavachVpnService.kt   the VpnService
  service/DnsProxy.kt       the DNS read/block/forward loop
  service/BootReceiver.kt   start on boot
  service/VpnController.kt  prepare/start/stop helpers
  data/                     Room DB, settings, blocklist repository
  ui/                       Home, Apps, Rules, Settings screens
```

## Limitations & honest notes

- Compiled with AGP 8.5 / Kotlin 2.0 / Compose; `minSdk 24`, `targetSdk 34`.
- `QUERY_ALL_PACKAGES` is used only to list apps for per-app control; on Google
  Play this needs a policy justification (fine for sideloaded builds).
- The DNS-only design means a very small number of apps that bypass the system
  resolver are not filtered.

## License

Add a license of your choice before publishing.
