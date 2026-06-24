# AppDock

Docker-like on-demand app launcher for Android.

## How it works

1. **Vault** — Back up APKs of apps you use rarely (Instagram, Netflix, BGMI, etc.)
2. **Launch** — Tap play to install & open an app instantly from vault
3. **Auto-cleanup** — Swipe AppDock from recents → all session apps uninstall automatically
4. **Data preserved** — App data (/data/data/) is kept, only APK is removed. You stay logged in.

## Setup (one-time)

After installing AppDock, run this once via ADB to grant Device Owner (silent install/uninstall):

```bash
adb shell dpm set-device-owner com.raaghav99.appdock/.admin.AppDockAdminReceiver
```

> **Note:** Device Owner requires no other accounts on the device, or use Shizuku as alternative.

## Without Device Owner (Shizuku fallback)

If Device Owner isn't possible, AppDock falls back to standard install prompt.
Shizuku support coming in v2.

## Project structure

```
app/src/main/java/com/raaghav99/appdock/
├── admin/       AppDockAdminReceiver   — Device Owner callbacks
├── data/        Room DB, DAO           — App vault storage
├── model/       AppEntry               — App data model
├── service/     AppDockService         — Session manager + onTaskRemoved cleanup
│               ApkBackupHelper        — APK copy + icon extraction
└── ui/          MainActivity           — Compose UI
```

## Flow

```
User taps Launch
    → AppDockService installs APK (silent via DPM or prompt)
    → Records app in sessionApps set
    → Opens app

User swipes AppDock from recents
    → onTaskRemoved() fires
    → All sessionApps uninstalled silently
    → DB updated (isActive = false)
    → RAM freed
```
