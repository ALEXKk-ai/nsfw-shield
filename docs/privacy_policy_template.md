# Privacy Policy — NSFW Shield

**Last Updated: March 2026**

## Overview
NSFW Shield ("we", "our", "the app") is a content filtering application designed to detect and block explicit content on Android devices. This privacy policy explains how we handle your data.

## Data Collection & Processing

### What We Process
- **Screen content**: Analyzed in real-time to detect explicit material
- **DNS queries**: Inspected to block known explicit domains
- **Media files**: Scanned locally for explicit images (when enabled)

### How We Process It
**All processing happens entirely on your device.** We use on-device machine learning models to analyze content locally. No images, screenshots, browsing data, message content, or personal information is ever transmitted to any external server.

### What We Store Locally
- **Activity logs**: Aggregated detection events (category, timestamp, action taken) stored in an encrypted local database
- **Filter profiles**: User-configured sensitivity settings
- **Blocked domain list**: Curated and custom domain entries
- **Admin PIN**: Hashed and encrypted locally

### What We NEVER Do
- Upload, transmit, or store screenshots or screen content externally
- Read your messages, passwords, or personal data
- Share any data with third parties
- Collect analytics, telemetry, or usage statistics
- Track your location or browsing history

## Accountability Reports (Premium)
If enabled, accountability reports contain **aggregated metadata only**:
- Number of blocked attempts per category
- Number of override events
- No URLs, images, messages, or specific content is included

Reports are sent via server-side email dispatch (SendGrid). The email contains only the metadata counts listed above.

## Data Retention
- **Free tier**: Activity logs retained for 7 days
- **Premium tier**: Activity logs retained for 90 days
- Users can delete all logs at any time from Settings

## Permissions Used
| Permission | Purpose |
|---|---|
| Accessibility Service | Real-time screen content detection |
| VPN Service | Local DNS filtering (no external routing) |
| Device Admin | Uninstall protection |
| Storage | Media scanning (optional) |
| Internet | DNS query forwarding only |

## Children's Privacy
NSFW Shield is designed as a parental control tool. We do not knowingly collect personal information from children under 13.

## Changes to This Policy
We may update this privacy policy. Changes will be posted in the app and on our website.

## Contact
For privacy inquiries, contact: privacy@nsfwshield.app
