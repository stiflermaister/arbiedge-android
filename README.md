# ArbiEdge — Android

A native **Java Android application** for reviewing football arbitrage opportunities, inspecting odds, tracking tickets and viewing pick analytics.

The project demonstrates Android UI development, HTTP/JSON integration, local persistence and client-side probability calculations.

## Features

- Arbitrage opportunity cards and outcome details.
- Local calculation of implied probabilities and arbitrage percentages.
- API clients for opportunities, history, notifications and picks.
- Pull-to-refresh and foreground refresh scheduling.
- Local ticket history and capital planning.
- Android notification channels and runtime notification permissions.

## Tech stack

Java · Android SDK · Gradle · AndroidX SwipeRefreshLayout · HTTP/JSON · SharedPreferences

Minimum Android version: **6.0 / API 23**. Compile and target SDK: **35**.

## Architecture

```text
ArbOnlyActivity
    -> API clients and refresh schedulers
    -> Opportunity / outcome / pick models
    -> Local calculations, ticket storage and notifications
```

`ArbOnlyActivity` is the launcher. The source also includes the earlier `MainActivity` prototype. API clients and calculation models live in `app/src/main/java/com/valuebetfinder/`.

## Build and run

1. Install Android Studio with **JDK 17** and **Android SDK 35**.
2. Open this repository and let Gradle sync.
3. Run the `app` configuration on an emulator or Android device.

Command-line build:

```bash
./gradlew assembleDebug
```

On Windows use `.\gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Try the local sample API

The production scanner and its data are separate from this Android source repository. A small, dependency-free Python server is included for inspecting the arbitrage UI with **synthetic data**:

```bash
python demo/server.py
```

The Android emulator connects to `http://10.0.2.2:8000/arbs`. For a USB-connected physical device, run:

```bash
adb reverse tcp:8000 tcp:8000
```

The client also tries `127.0.0.1:8000`. The sample server provides opportunities and history; other supported read endpoints return empty lists or status objects. It does not scan bookmakers, generate AI picks or place bets.

## Arbitrage calculation

For decimal odds across all mutually exclusive outcomes:

```text
book_percent = sum(1 / odds)
arbitrage exists when book_percent < 1
```

The application calculates this from the returned outcome data. Sample odds are fictional and only demonstrate the UI and calculation flow.

## API example

`GET /arbs` returns a JSON array:

```json
[
  {
    "country": "Demo",
    "league": "Demo League",
    "match": "Demo United - Sample City",
    "market": "1X2",
    "outcomes": [
      {"selection": "Home", "odds": 2.5, "bookmaker": "Demo A"},
      {"selection": "Draw", "odds": 3.6, "bookmaker": "Demo B"},
      {"selection": "Away", "odds": 3.4, "bookmaker": "Demo C"}
    ]
  }
]
```

## Current scope

This is an application prototype with a Serbian-language UI. Live results require a separate backend. Refresh scheduling runs while the app is active; it is not a guaranteed background service. Cleartext HTTP is enabled for local development; a public deployment should use a configured HTTPS backend.

This public source snapshot excludes the private scanner, provider configuration, local data, signing keys and machine-specific settings.

**CV description:** Built a native Java Android application with HTTP/JSON integration, arbitrage calculations, local ticket tracking and Android notifications.
