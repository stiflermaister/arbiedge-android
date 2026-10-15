# Validation

Checked on 2026-10-15 before publication:

- The local sample API returned JSON for opportunities, history, picks, notifications and status endpoints.
- The synthetic opportunity's decimal odds satisfy the application's arbitrage condition.
- Python and JSON source validation passed.
- Public source files were scanned for known credential values, common token formats and private runtime files.

An Android APK build was not run during this portfolio preparation because JDK was unavailable in the environment. Use Android Studio with JDK 17 and SDK 35 to build and run the app. Emulator UI testing and live backend integration remain separate checks.
