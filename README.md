# Ali Noori Share

Offline file sharing between two Android phones (Kotlin + Jetpack Compose).
Package `com.alinoori.share` · minSdk 24 (Android 7.0) · target/compile SDK 34 · UI: فارسی (RTL) + English · light/dark.

> **Status:** the source is complete, but it has **not been compiled or run yet** – the environment
> that produced it had no Android SDK and no access to Google/Maven repositories. No APK exists.
> Open it in Android Studio, let Gradle sync, and fix any compile error that shows up (please report it).

## How the two phones talk (no internet)
| Method | How |
|---|---|
| Wi-Fi Direct (primary) | `WifiP2pManager` discovers peers and forms a group. The group owner listens on TCP port 8988, the other phone connects to it. Who becomes owner is decided by Android; the code handles both cases. |
| Hotspot / same Wi-Fi (fallback) | Receiver listens on `:8988` and shows `ip:8988` as text + QR. Sender types the code. Used when Wi-Fi Direct is unsupported or fails. |

Transfer protocol (`Protocol.kt`): hello + file list → receiver shows names/sizes and accepts or rejects →
for each file raw bytes + SHA-256 → receiver replies OK/corrupt. Incomplete files are written to `.part` and
deleted on any failure. INTERNET permission is declared only because Android requires it for any socket;
the app never contacts a server.

## Structure
```
app/src/main/java/com/alinoori/share/
  MainActivity.kt            navigation, bottom bar, runtime permissions
  ShareApp.kt                application singletons
  ShareViewModel.kt          selection + send/receive sessions
  DeviceDiscoveryManager.kt  Wi-Fi Direct peer discovery
  ConnectionManager.kt       Wi-Fi Direct connect, sockets, local IP
  FileTransferManager.kt     send/receive, progress, speed, ETA, SHA-256
  FileScanner.kt             MediaStore / apps / SAF picker lists
  TransferHistoryManager.kt  local JSON history
  SettingsManager.kt         language, theme, folder, toggles
  TransferService.kt, Notifier.kt, Protocol.kt, Models.kt, Qr.kt
  ui/                        Theme, Home, Category, Connect (Send/Receive), Transfers, Settings, About, Help
```

## Build the APK
Requirements: Android Studio Koala or newer (JDK 17, Android SDK 34).
```
# Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s)
# or from a terminal in the project folder (needs ANDROID_HOME and internet for the first sync):
./gradlew assembleDebug
# result: app/build/outputs/apk/debug/app-debug.apk
```

## Test a real transfer (two phones)
1. Install the APK on both phones. On both: Wi-Fi **on** (no need to join a network); on Android ≤ 12 also turn **Location** on.
2. Phone B: *Receive*. Phone A: pick files (Photos, Music, Files…) then *Send*.
3. Phone A: tap Phone B in "Nearby devices". Accept the Wi-Fi Direct invitation on Phone B.
4. Phone B: *Accept* the file offer. Watch progress, MB/s and ETA; the result appears on both phones and in History.
5. Fallback: join both phones to one hotspot, type the receiver's `ip:8988` code on the sender.
6. Test a file > 1 GB, cancel mid-transfer (no leftover file), and two files with the same name (saved as `name (1).ext`).

## Known limits (honest list)
- Pause/resume is **not** implemented; an interrupted transfer restarts and never leaves a broken file.
- Folder transfers send all files inside but the receiver flattens the path (`folder_sub_file.ext`) for safety.
- Apps are shared as the base APK only (split APKs are not bundled).
- Received files go to the app folder (`Android/data/com.alinoori.share/files/Received`) unless a save folder is chosen in Settings.
- Wi-Fi Direct behaviour varies by vendor; the hotspot method is the reliable fallback.
