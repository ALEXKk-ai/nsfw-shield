# NSFW Shield — Google Play Store Compliance Guide

## Critical Requirements

### 1. Accessibility Service Disclosure (MANDATORY)
- **Full-screen, standalone UI** must appear BEFORE Accessibility is requested
- **NOT** a dialog, tooltip, or in-app banner
- Must explain: what data is accessed, how it's used, what is NOT done
- Implementation: `AccessibilityDisclosureScreen.kt`

> **Play Store Policy**: Apps requesting Accessibility must include a full-screen
> in-app disclosure that explains why the service is needed and how user data is
> handled. The disclosure must appear before the permission is requested.

### 2. VPN Service Disclosure (MANDATORY)
- Same full-screen requirement as Accessibility
- Must clarify: VPN is local-only, no external routing, no IP masking
- Implementation: `VpnDisclosureScreen.kt`

### 3. Declared Permissions
All permissions in `AndroidManifest.xml`:
- `INTERNET` — DNS query forwarding
- `BIND_VPN_SERVICE` — Local DNS filtering
- `BIND_ACCESSIBILITY_SERVICE` — Screen content monitoring
- `BIND_DEVICE_ADMIN` — Uninstall protection
- `FOREGROUND_SERVICE` — VPN + monitoring
- `QUERY_ALL_PACKAGES` — App monitoring
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` — Media scanning

### 4. Data Safety Form
| Question | Answer |
|---|---|
| Does your app collect or share user data? | No data is shared. Local logs only. |
| Is data encrypted? | Yes, AES-256 via AndroidX security-crypto |
| Can users request data deletion? | Yes, from Settings |
| Is data transferred to third parties? | Never |
| Analytics collected? | None |

### 5. Content Rating
- **IARC**: Appropriate content rating required (the app filters NSFW content but does not display it)
- **Target Audience**: 13+ (parental control category)

## Pre-Submission Checklist
- [ ] Full-screen Accessibility disclosure appears before permission request
- [ ] Full-screen VPN disclosure appears before VPN activation
- [ ] Privacy policy URL set in Play Console
- [ ] Data Safety form completed
- [ ] IARC content rating questionnaire completed
- [ ] App tested on API 28+ devices
- [ ] ProGuard rules tested with release build
- [ ] All model files included in assets (or fallback mode works)
