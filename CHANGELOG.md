# Changelog

## 1.0.5

- "Always alert for allowed apps" now also covers HyperOS's default sound rule: on the China ROM, System UI gives no sound or vibration to any channel you never set, unless the app is on Xiaomi's whitelist (this is why apps like Duolingo, Gmail or Claude stayed silent). For allowed apps an unset switch now counts as on; a sound or vibration switch you turned off in the app's notification settings still wins.

## 1.0.4

- New option "Let notification sounds finish": when an allowed app updates a notification right after it alerted (for example WhatsApp adding its summary), the sound and vibration play to the end instead of being cut off. Ringing calls and insistent alarms still stop at once.
- New option "Always alert for allowed apps": HyperOS System UI no longer silences an allowed app because it is hidden on the lock screen or folded as unimportant. Do Not Disturb, silent and vibrate mode, silent channels and your per-app sound settings still apply. Add System UI (com.android.systemui) to HyperFCM's LSPosed scope and reboot.
- Both options are off by default. Neither adds wakelocks, alarms or periodic work.

## 1.0.3

- Frozen push apps (Telegram and similar) get their messages on time on the CN ROM. With the "global freezer policy" option on, HyperFCM also turns on the ROM's global network control for frozen apps (`config_greezer_global_net_enable`), which the CN ROM ships off. The ROM then closes a frozen app's sockets, so its server falls back to FCM instead of holding messages until the app is opened.

## 1.0.2

- Hooks no longer risk a deadlock with system_server locks during the first config load.
- Without a config, hooks retry with a growing delay (1 min to 1 h) instead of asking the app on every call.
- Boot setup waits for the user to unlock instead of checking every 5 seconds.
- Boot guard: a boot counts as finished 2 minutes after boot completes; a clean shutdown also resets the count.
- Permission dialogs open one after another (app list, notifications, Icebox), so the notification request is no longer lost.
- The hook alert only shows again when a different set of hooks fails.
- A warning appears once if a settings save reached only one of its two stores.
- No wakelocks, alarms or periodic work were added.

## 1.0.1

- Critical app protection lists every app again: HyperFCM now asks for the HyperOS "read installed apps" permission, reloads the list each time the dialog opens and shows how to grant the permission if HyperOS still hides apps.

## 1.0.0

First release.

- New interface built with [Miuix](https://github.com/compose-miuix-ui/miuix), new icon, English / Turkish / Chinese.
- Boot guard: after three boots in a row that do not finish, the module stops installing its hooks until you turn them back on in the app.
- Hook status shows, per hook group, how often the system has called it since boot, and warns when a group on an always-busy path is never called.
- PowerKeeper hooks (sleep-mode mobile data, GMS DNS block) report their status too.
- Two layers against the Greeze GMS limit (`triggerQuickFreezeAction`, `triggerGMSLimitAction`).
