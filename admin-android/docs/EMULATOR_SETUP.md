# Emulator Setup — Vedant Admin Android

**Status:** NOT EXECUTED. Everything below was written to be run by hand on a faster
connection. No emulator has been booted and no instrumented test has ever run on this
project (blocker **R2**).

**Why this doc exists:** the machine is fully emulator-capable, but two Android SDK
packages are missing and the download was stopped because the connection throughput was
~60 KB/s against a combined ~1.9 GB payload.

---

## 1. Current machine state (verified 2026-10-12)

| Component | State | Evidence |
|---|---|---|
| `emulator.exe` | Installed, v37.1.11.0 | `$SDK/emulator/emulator.exe` |
| Hardware acceleration | **Working** | `emulator-check.exe accel` → `0`, `WHPX(10.0.22000) is installed and usable` |
| `adb.exe` | Installed | `$SDK/platform-tools/adb.exe` |
| Platforms | android-34, android-35, android-37.0 | `$SDK/platforms/` |
| Build-tools | 34.0.0, 35.0.0, 36.0.0 | `$SDK/build-tools/` |
| **cmdline-tools** | **MISSING** (dir exists but empty — no `sdkmanager`/`avdmanager`) | `$SDK/cmdline-tools/` |
| **system-images** | **MISSING** (no such directory) | `$SDK/system-images/` |
| **AVDs** | None | `~/.android/avd/` is empty |

`$SDK` = `C:\Users\sarfaraz\AppData\Local\Android\Sdk`

**Hardware:** 32 GB RAM, 8 logical cores, hardware virtualisation confirmed present
(`systeminfo` → *"A hypervisor has been detected"*).

---

## 2. Download the two packages

Sizes are the server's own `Content-Length` values, not estimates.

| # | Package | Size | URL |
|---|---|---|---|
| 1 | Command-Line Tools (Windows) | ~150 MB | `https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip` |
| 2 | System image, Android 15 / API 35 / x86_64 / **Google APIs** | **1.74 GB** (1,738,815,903 B) | `https://dl.google.com/android/repository/sys-img/google_apis/x86_64-35_r09.zip` |

### Choosing an image

- Use **Google APIs**, *not* **Google Play**. The Play image is larger, is tied to Play
  Store signing, and this app requires no Play Services.
- If a URL 404s, the always-current index is
  `https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-1.xml` —
  pick the `x86_64` row for the API level you want.
- A smaller alternative: API 26 x86_64 (`minSdk` = 26) is roughly a third of the data.
  It proves the app runs on the oldest Android version it claims to support, but tests
  against a much older UI layer.
- Beware `arm64-v8a` images — they will run painfully slowly or not at all under WHPX on
  an x86_64 host. Match the architecture to the host.

---

## 3. Extract — mind the directory names

The folder names are load-bearing. `sdkmanager` is *not* discovered if they are wrong.

### Package 1

Extract, then **rename the inner folder to `latest`**:

```
$SDK\cmdline-tools\
└── latest\
    ├── bin\
    │   ├── sdkmanager.bat
    │   └── avdmanager.bat
    ├── lib\
    └── source.properties
```

> The zip extracts as `cmdline-tools\cmdline-tools\...`. If you leave it that way,
> nothing will find the tools. The folder must be named `latest`.

### Package 2

The inner folder in the zip is already named `x86_64`, so extract directly to:

```
$SDK\system-images\android-35\google_apis\x86_64\
    ├── system.img
    ├── ramdisk.img
    ├── userdata.img
    └── ...
```

---

## 4. Accept licences and create the AVD

Run from Git Bash. `sdkmanager` is a `.bat`, so call it by full path.

```bash
SDK="/c/Users/sarfaraz/AppData/Local/Android/Sdk"

# 4a. Accept SDK licences (interactive: type "y" and Enter at each prompt)
"$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --licenses

# 4b. Confirm the system image is visible to the SDK
"$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --list_installed

# 4c. Create the AVD
echo "no" | "$SDK/cmdline-tools/latest/bin/avdmanager.bat" create avd \
  -n vedant_api35 \
  -k "system-images;android-35;google_apis;x86_64" \
  -d pixel_6

# 4d. Confirm it exists
"$SDK/cmdline-tools/latest/bin/avdmanager.bat" list avd
```

Piping `echo "no"` answers avdmanager's *"Do you wish to create a custom hardware
profile?"* prompt with no, so it uses the built-in `pixel_6` profile.

---

## 5. Boot the emulator

```bash
SDK="/c/Users/sarfaraz/AppData/Local/Android/Sdk"

# Boot in the background. -no-snapshot-load forces a clean cold boot, which is what
# you want for a first run and for reproducible test results.
"$SDK/emulator/emulator.exe" -avd vedant_api35 \
  -no-snapshot-load -no-boot-anim &

# Wait for the device to appear, then wait for it to finish booting
"$SDK/platform-tools/adb.exe" wait-for-device
"$SDK/platform-tools/adb.exe" shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
echo "BOOT COMPLETE"
"$SDK/platform-tools/adb.exe" devices
```

Add `-no-window` for a headless run. Drop it if you want to actually watch the UI —
useful for the first time, since part of the goal is verifying the interface.

---

## 6. Build and install the APK

```bash
cd "/c/Users/sarfaraz/Desktop/vedant fruits and vegitables/admin-android"

./gradlew assembleDebug
"$SDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected APK size at the last build: **23,831,166 bytes**.

---

## 7. Run the instrumented tests

This is the payoff — **42 `@Test` methods across 9 files** that have compiled green but
have never once executed:

```bash
./gradlew connectedDebugAndroidTest
```

Test sources (`app/src/androidTest/`):

| File | Notes |
|---|---|
| `CatalogueHubScreenTest.kt` | |
| `ComplaintsListScreenTest.kt` | |
| `DashboardScreenTest.kt` | |
| `OfflineGatingScreenTest.kt` | covers the T26 DEFECT-1 fix: offline → disabled + hint, online → enabled |
| `OrderDetailScreenTest.kt` | |
| `OrdersListScreenTest.kt` | |
| `ProductEditorScreenTest.kt` | |
| `SignInScreenTest.kt` | |
| `Fakes.kt` | shared test doubles, not a test class |

Results land in `app/build/reports/androidTests/connected/` (open `index.html`).

---

## 8. What this will and will NOT prove

### It WILL clear

- **R2 (no device)** — instrumented tests actually execute instead of merely compiling.
- Real Compose rendering, navigation, and dark-mode behaviour on a real Android runtime.
- The offline-gating fix verified for real rather than only against fakes.
- Touch-target and layout behaviour at real device density.

### It will NOT clear

- **R1 (no admin credentials)** — this is the important one. The app will boot to the
  **sign-in screen and stop there**, because there is no account to sign in with.
  Everything behind authentication stays unverified:
  - the server-side `private.is_admin()` authorisation gate,
  - every admin RPC (`admin_update_order_status`, `admin_reject_order_item`,
    `admin_restore_order_item`, `admin_slot_load`),
  - all catalogue writes and storage uploads,
  - live reads of orders, complaints, and slots.

To clear R1 as well, supply a **test admin account (email + password)** and a
read-only `GET` check against `private.is_admin()` becomes possible.

Never describe this run as "the app works end to end". It proves the UI layer renders
and the instrumented suite executes — nothing about the live server.

---

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `sdkmanager: command not found` | Inner folder is not named `latest`. See §3. |
| `avdmanager` reports package path not found | System image extracted to the wrong depth, or `--list_installed` does not show it. Check §3's exact path. |
| Emulator starts then dies instantly | Usually an architecture mismatch (arm64 image on x86_64 host) or a stale lock. Check `$SDK/emulator/emulator.exe -avd vedant_api35 -verbose`. |
| `adb devices` shows `offline` | Still booting. Re-run the `boot_completed` wait loop in §5. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A previous install with a different signature. `adb uninstall com.vedant.admin` then install again. |
| Emulator is very slow | Confirm WHPX is active: `emulator-check.exe accel` should print `0`. If it does not, hardware acceleration is off. |
| `connectedDebugAndroidTest` finds no devices | `adb devices` must list the emulator before you invoke Gradle. |

---

## 10. Cleanup

```bash
# Remove the AVD when finished
"$SDK/cmdline-tools/latest/bin/avdmanager.bat" delete avd -n vedant_api35

# Kill a running emulator
"$SDK/platform-tools/adb.exe" -s emulator-5554 emu kill
```
