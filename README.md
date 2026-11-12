<p align="center"><img src="docs/icon.png" width="96" alt="HyperFCM icon"></p>

# HyperFCM

An LSPosed module that makes Firebase Cloud Messaging (FCM) push notifications arrive reliably on
**Xiaomi HyperOS 4 China ROMs**, including for apps that the system has stopped or frozen.

**Only HyperOS 4 (Android 17) is supported.** The hooks were written against the HyperOS 4 system
code; older HyperOS versions (1, 2, 3) are not supported. Tested on a Xiaomi 17 Pro Max with the
China ROM. The app shows which hooks could be installed on your system.

## What it does

HyperOS China ROMs break FCM in two places: they freeze Google Play services, which holds the push
connection, and they refuse to deliver the push to apps that are stopped, frozen or not allowed to
auto-start. HyperFCM fixes both for the apps you choose.

- **Delivery to stopped and frozen apps.** A push that Google Play services sends to an allowed app
  wakes the app, thaws it in the HyperOS freezer (Greeze) and passes the auto-start checks.
- **Google Play services stays connected.** The freezer and its "GMS limit" no longer freeze Google
  Play services or cut its network while the screen is off.
- **Global ROM freezer policy** (optional, experimental). Runs Greeze with the international policy
  that is already inside the system instead of the China one.
- **Mobile data stays on in sleep mode** (optional). HyperOS sleep mode keeps working but no longer
  turns mobile data off at night.
- **Critical app protection** (optional, per app). Selected apps are neither frozen nor killed in
  the background. This costs battery.
- **Keep notifications** (optional). Notifications of an allowed app survive when the system stops
  the app, for example after clearing recents.
- **Hook status.** Shows every hook group, whether it installed, and how often the system has called
  it since boot. You get one notification if a hook fails after a system update.
- **Boot guard.** If the phone fails to finish booting three times in a row, the module stops
  installing hooks until you turn them back on in the app.

A push is only trusted when it really comes from Google Play services and targets an app on your
allow list. The app then gets a 20-second window to handle it.

## Battery

The module is built around one rule: **it must never keep the phone awake.** It takes no wakelocks,
sets no alarms, runs no periodic jobs and creates no network traffic of its own. It only reacts to a
push that Google Play services is already delivering.

Options that do cost battery say so in the app: critical app protection and the global freezer
policy let more apps run in the background.

## Requirements

- Xiaomi HyperOS 4 (China ROM, Android 17)
- Root with LSPosed (libxposed API 102 or newer)
- Google Play services installed and working

## Install

1. Download the APK from [Releases](../../releases) and install it.
2. In LSPosed, enable HyperFCM. Keep the recommended scope: **System Framework** and
   **Battery and performance** (`com.miui.powerkeeper`).
3. Reboot.
4. Open HyperFCM. The home page should say "Module is active" and "All hooks are working".
5. On the Apps page, allow the apps that should be woken by push.

Do not run it together with another FCM fix (fcmfix forks, HyperFCMFix, XNotifyFix and similar):
they hook the same system methods.

## Reporting a problem

Open an [issue](../../issues) with your HyperOS version, device, module version and the hook status
list. `scripts/collect-report.sh` collects the useful system state (read-only, needs root):

```sh
su -c 'sh /sdcard/Download/collect-report.sh'
```

Check the file for private information before you upload it.

## Build

```sh
./gradlew assembleRelease testDebugUnitTest
```

Needs JDK 21 and Android SDK platform 37.

## Credits

HyperFCM builds on the trusted-delivery core of [fcmfix](https://github.com/kooritea/fcmfix) by
kooritea and its community forks (see [LICENSE](LICENSE)). The HyperOS hooks, the interface and the
safety features were written for HyperFCM.

The interface uses [Miuix](https://github.com/compose-miuix-ui/miuix).

Licensed under the [MIT License](LICENSE).
