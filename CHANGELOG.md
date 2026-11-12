# Changelog

## 1.0.1

- Critical app protection lists every app again: HyperFCM now asks for the HyperOS "read installed apps" permission, reloads the list each time the dialog opens and shows how to grant the permission if HyperOS still hides apps.

## 1.0.0

First release.

- New interface built with [Miuix](https://github.com/compose-miuix-ui/miuix), new icon, English / Turkish / Chinese.
- Boot guard: after three boots in a row that do not finish, the module stops installing its hooks until you turn them back on in the app.
- Hook status shows, per hook group, how often the system has called it since boot, and warns when a group on an always-busy path is never called.
- PowerKeeper hooks (sleep-mode mobile data, GMS DNS block) report their status too.
- Two layers against the Greeze GMS limit (`triggerQuickFreezeAction`, `triggerGMSLimitAction`).
