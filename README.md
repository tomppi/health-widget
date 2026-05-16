# Health Connect Widget

An Android home screen widget that reads **Heart Rate**, **Blood Oxygen Saturation (SpO2)**, and **Body Temperature** live from [Google Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect).

---

## Screenshots

> Add yours here after building.

---

## Features

- 🏠 **Home screen widget** — glanceable metrics without opening the app
- ❤️ Heart rate (bpm)
- 🫁 Blood oxygen saturation (SpO₂ %)
- 🌡️ Body temperature (°C)
- 🔄 Auto-refreshes every 15 minutes via WorkManager
- 🔒 Permission flow handled in-app with Health Connect's native contract

---

## Requirements

| Requirement | Version |
|---|---|
| Android Studio | Hedgehog or newer |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Kotlin | 1.9+ |
| Health Connect app | Installed on device |

> **Note:** Health Connect is pre-installed on Pixel devices running Android 14+. On older devices users need to install it from the Play Store.

---

## Setup

### 1. Clone

```bash
git clone https://github.com/YOUR_USERNAME/HealthConnectWidget.git
cd HealthConnectWidget
```

### 2. Open in Android Studio

File → Open → select the cloned folder.

### 3. Sync Gradle

Android Studio will prompt you — click **Sync Now**.

### 4. Run on a real device

Health Connect **does not work on the emulator** for live biometric data. Connect a physical Android device and click Run.

### 5. Grant permissions

On first launch the app triggers the Health Connect permission dialog for:
- `READ_HEART_RATE`
- `READ_OXYGEN_SATURATION`
- `READ_BODY_TEMPERATURE`

### 6. Add the widget

Long-press your home screen → **Widgets** → find **Health Connect Widget** → drag it onto your screen.

---

## Project Structure

```
app/src/main/
├── java/com/healthwidget/
│   ├── MainActivity.kt              # Permission flow + in-app data display
│   ├── HealthConnectManager.kt      # All Health Connect read logic
│   └── widget/
│       ├── HealthAppWidget.kt       # AppWidgetProvider
│       └── HealthWidgetUpdateWorker.kt  # WorkManager periodic sync
└── res/
    ├── layout/
    │   ├── activity_main.xml        # Main screen layout
    │   └── widget_layout.xml        # Home screen widget layout
    ├── xml/
    │   ├── health_widget_info.xml   # Widget metadata
    │   └── health_permissions.xml   # Declares HC permissions to the system
    ├── drawable/
    │   └── card_background.xml      # Rounded card background
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── themes.xml
```

---

## How It Works

```
MainActivity
  └─ checks permissions via HealthConnectManager
       └─ if granted → reads latest records (last 24h) from Health Connect
            └─ HeartRateRecord, OxygenSaturationRecord, BodyTemperatureRecord

HealthAppWidget (AppWidgetProvider)
  └─ onUpdate → HealthAppWidget.updateWidget()
       └─ coroutine on IO dispatcher → HealthConnectManager.getLatestHealthData()
            └─ pushes RemoteViews update to AppWidgetManager

HealthWidgetUpdateWorker (CoroutineWorker)
  └─ scheduled as PeriodicWork every 15 min
       └─ calls updateWidget() for all active widget instances
```

---

## Refresh Behaviour

The widget refreshes:
- Immediately when first added to the home screen
- Every **15 minutes** via WorkManager (Android's minimum for periodic work)
- Every time the main app is opened

To trigger an immediate refresh, open the app and tap **Refresh**.

---

## Data Sources

Health Connect reads from whatever sources are writing to it on the device:
- Wear OS watches (Galaxy Watch, Pixel Watch, etc.)
- Fitbit (via Health Connect sync)
- Samsung Health
- Any app that writes to Health Connect

Body temperature data is less commonly synced automatically — you may need an app that supports it (e.g., some smart thermometers or Withings devices).

---

## Permissions

Declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />
<uses-permission android:name="android.permission.health.READ_BODY_TEMPERATURE" />
```

Also declared in `res/xml/health_permissions.xml` as required by the Health Connect SDK.

---

## License

MIT — see [LICENSE](LICENSE).
