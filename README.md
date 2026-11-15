# JKT48 Bonus Monitor

An Android application that monitors the JKT48 exclusive bonus quota API and sends notifications when **Astrella Virgiananda**'s available quota falls below 5.

---

## Features

- Periodic background monitoring every 15 minutes via WorkManager
- Push notification when `available_quota < 5`
- Smart notification deduplication — no spam when quota stays the same
- Local monitoring history (JSON file, no database)
- Clean Jetpack Compose UI
- Buildable entirely via GitHub Actions — no Android Studio required

---

## Editing Without Android Studio

This project is a standard Gradle project. You can edit all Kotlin source files using:

- **Visual Studio Code** with the [Kotlin extension](https://marketplace.visualstudio.com/items?itemName=fwcd.kotlin)
- Any plain text editor (Notepad++, Sublime Text, etc.)

**Source files are located at:**
```
app/src/main/kotlin/com/example/jkt48bonusmonitor/
```

**To build locally** you need:
1. Java 17+ (`java -version`)
2. Run: `./gradlew assembleDebug` (Linux/Mac) or `gradlew.bat assembleDebug` (Windows)

The compiled APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Configuring the Exclusive ID

The **Exclusive ID** (e.g. `EX79CB`) identifies which JKT48 exclusive event to monitor.

**How to change it:**
1. Launch the app.
2. Edit the **Exclusive ID** input field at the top of the screen.
3. Tap **START MONITORING** or **CHECK NOW** — the new ID is saved automatically.

The ID is persisted in DataStore and survives app restarts. The background worker always reads the latest saved ID.

---

## Building the APK via GitHub Actions

### Debug APK (no signing required)

1. Push your code to the `main` branch.
2. Go to **Actions** → **Build APK** → latest run.
3. Download the `JKT48-Bonus-Monitor` artifact (contains `app-debug.apk`).

### Signed Release APK (optional)

Configure the following GitHub repository secrets:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded keystore file: `base64 keystore.jks` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Then set the repository variable:

| Variable | Value |
|---|---|
| `HAS_SIGNING_KEYSTORE` | `true` |

The release job runs automatically on `main` push when the variable is set.

> ⚠️ **Never commit** keystore files or passwords to the repository.

---

## Installing the APK

1. Download the APK from GitHub Actions artifacts.
2. Transfer it to your Android device.
3. On your device: **Settings → Security → Install unknown apps** — enable for your file manager or browser.
4. Open the APK file and install.

Minimum Android version: **Android 8.0 (API 26)**

---

## How Background Monitoring Works

The app uses **AndroidX WorkManager** for background monitoring.

```
App UI  ──startMonitoring()──▶  WorkManager.enqueueUniquePeriodic()
                                        │
                                        ▼ (every ~15 min, requires network)
                              BonusMonitorWorker.doWork()
                                        │
                                        ├──▶ Read Exclusive ID from DataStore
                                        ├──▶ GET jkt48.com/api/v1/exclusives/{id}/bonus
                                        ├──▶ Parse session_members for "Astrella Virgiananda"
                                        ├──▶ Compare quota with lastNotifiedQuota
                                        ├──▶ Send notification if quota < 5 AND changed
                                        ├──▶ Update DataStore (timestamp, quota)
                                        └──▶ Append to monitoring_history.json
```

### Notification Deduplication

| Scenario | Action |
|---|---|
| Quota drops 6 → 4 | ✅ Notify |
| Quota stays 4 | ❌ No duplicate |
| Quota drops 4 → 3 | ✅ Notify (different value) |
| Quota recovers 3 → 5 | ❌ Not below threshold |
| Quota drops 5 → 4 | ✅ Notify |

The last-notified quota is saved in DataStore and survives reboots.

---

## Where Local History Is Stored

History is stored as a JSON file inside the app's private internal storage:

```
/data/data/com.example.jkt48bonusmonitor/files/monitoring_history.json
```

You can view it on rooted devices or via ADB:
```bash
adb shell run-as com.example.jkt48bonusmonitor cat files/monitoring_history.json
```

The file holds up to **200 entries** (oldest are trimmed automatically). Consecutive identical quota+status entries are deduplicated.

---

## Known Background Execution Limitations

Android intentionally restricts background work to preserve battery life.

| Limitation | Details |
|---|---|
| **Doze mode** | WorkManager is Doze-aware. During deep sleep, work is deferred to the next maintenance window. |
| **15-minute minimum** | WorkManager enforces a minimum 15-minute repeat interval. The OS may delay beyond this. |
| **Battery optimization** | Manufacturer overlays (MIUI, One UI, ColorOS) may kill background work aggressively. Add the app to your battery optimization whitelist. |
| **No exact timing guarantee** | WorkManager does not provide exact scheduling. ±15 minutes variance is normal. |
| **Network constraint** | The worker only runs when connected. Checks are skipped while offline. |

**To improve reliability on stock Android:**
- Settings → Battery → Battery optimization → Find JKT48 Bonus Monitor → Set to "Don't optimize"

---

## API Notes

- **Endpoint:** `GET https://jkt48.com/api/v1/exclusives/{EXCLUSIVE_ID}/bonus?lang=id`
- **Response structure:** `data[].session_members[].{ member_name, available_quota }`
- **Cloudflare protection:** The API returns 403 to non-browser user-agents. The Android app sends a mobile browser User-Agent header to bypass this.
- **Multiple sessions:** The app searches all sessions in the response and returns the first match for `Astrella Virgiananda`.

---

## Project Structure

```
app/src/main/kotlin/com/example/jkt48bonusmonitor/
├── data/
│   ├── api/
│   │   ├── BonusApiClient.kt       # OkHttp client + JSON parser
│   │   └── BonusApiDto.kt          # Kotlinx Serialization DTOs
│   ├── datastore/
│   │   └── AppPreferences.kt       # DataStore Preferences wrapper
│   ├── history/
│   │   ├── HistoryEntry.kt         # History entry data class
│   │   └── HistoryRepository.kt    # JSON file read/write (atomic)
│   └── repository/
│       └── BonusMonitorRepository.kt # Orchestrates API + notif + history
├── domain/
│   └── model/
│       └── Models.kt               # Domain models (MemberQuota, ApiResult, etc.)
├── notification/
│   └── NotificationHelper.kt       # Channel setup, permission check, send
├── worker/
│   └── BonusMonitorWorker.kt       # WorkManager CoroutineWorker
├── ui/
│   ├── home/
│   │   ├── HomeScreen.kt           # Jetpack Compose main screen
│   │   └── HomeViewModel.kt        # AndroidViewModel
│   └── theme/
│       ├── Color.kt
│       └── Theme.kt
├── JKT48Application.kt             # Application class
└── MainActivity.kt                 # Entry point
app/src/test/
├── kotlin/.../BonusMonitorTest.kt  # 13 unit test cases
└── resources/fixtures/             # Actual API response fixtures
.github/workflows/build.yml         # GitHub Actions CI/CD
```

---

## License

For personal use only. JKT48 is a trademark of PT. PIA Entertainment.
