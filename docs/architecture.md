# NSFW Shield — Architecture Documentation

## System Overview

NSFW Shield is a privacy-first, AI-powered NSFW content filtering system for Android. It combines on-device machine learning, network-level filtering, and screen monitoring to detect and block explicit content across browsers, apps, images, video, and audio in real time.

**Core Principle: All processing happens entirely on-device. No images, video frames, or message content is ever sent to external servers.**

## Architecture Layers

```
┌─────────────────────────────────────────┐
│              UI Layer                    │
│  Jetpack Compose + Navigation            │
│  Onboarding ← Play Store Required        │
│  Dashboard | Profiles | Reports          │
│  Settings | Subscription | PIN Lock      │
├─────────────────────────────────────────┤
│           Detection Engine               │
│  ImageClassifier (TFLite)                │
│  TextClassifier (Keyword + NLP)          │
│  VideoHandler (Frame extraction)         │
│  AudioHandler (Whisper → Text)           │
│  ContextRescorer (False positive reducer)│
│  DecisionEngine (Score combiner)         │
├─────────────────────────────────────────┤
│          Network Control                 │
│  LocalVpnService (TUN interface)         │
│  DnsFilter (Packet parser)               │
│  DomainBlocklist (Curated + custom)      │
│  SafeSearchEnforcer (Google/YT/Bing)     │
├─────────────────────────────────────────┤
│       Screen & App Monitoring            │
│  NSFWAccessibilityService                │
│  BlurOverlayManager                      │
│  AppActivityTracker                      │
│  MediaScanner (WorkManager)              │
├─────────────────────────────────────────┤
│         Premium Features                 │
│  SubscriptionGate (Play Billing)         │
│  AccountabilityReportService             │
│  DelayToDisableManager (24hr timer)      │
├─────────────────────────────────────────┤
│          Security Layer                  │
│  AntiBypassModule                        │
│  AdminLockManager (PIN + crypto)         │
│  UninstallProtection (Device Admin)      │
│  EncryptedLogStore (AES-256)             │
├─────────────────────────────────────────┤
│            Data Layer                    │
│  Room Database (Encrypted)               │
│  FilterProfile | ActivityLog             │
│  BlockedDomain | SubscriptionState       │
└─────────────────────────────────────────┘
```

## Data Flow

### Content Detection Pipeline
1. Content arrives via Accessibility event, DNS query, or media scan
2. Content is routed to appropriate classifier (image/text/video/audio)
3. Raw classifier score (0.0–1.0) is generated
4. ContextRescorer adjusts score based on surrounding context
5. DecisionEngine applies profile thresholds and category toggles
6. Action is taken: ALLOW, BLUR, REPLACE, or BLOCK
7. Event is logged via ActivityLogger → Room DB

### DNS Filtering Pipeline
1. App creates local VPN via TUN interface
2. All DNS packets are intercepted
3. Domain is extracted from DNS query
4. Domain checked against in-memory blocklist + Room DB
5. Blocked domains receive NXDOMAIN response
6. Non-blocked queries are forwarded normally

## Dependency Injection
- **Hilt** provides all dependencies via `@Singleton` scoped classes
- `DatabaseModule` provides Room DB + DAOs
- All detection engine components are `@Inject` constructable

## Technology Stack
| Component | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material3 |
| Database | Room (SQLite) |
| DI | Hilt (Dagger) |
| ML Inference | TensorFlow Lite |
| Network | Android VpnService |
| Monitoring | AccessibilityService |
| Encryption | AndroidX security-crypto (AES-256) |
| Billing | Google Play Billing Library |
| Background | WorkManager |
