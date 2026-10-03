# F TikTok Manager

Multi-account TikTok web manager (isolated profiles) with VCAM image feeding, IP checker, saved links and backup/restore.

Build: push to `main` -> GitHub Actions -> download `F-TikTokManager-APK` artifact.
Local: `gradle assembleDebug` (Gradle 8.5, JDK 17).

Notes
- Full session isolation needs Android System WebView 128+ (multi-profile). On older WebView only cookies are separated.
- Backup files contain login cookies - keep them private.
