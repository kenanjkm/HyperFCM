# Changelog

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
