# OEM Battery Optimization Exclusion Guide

AION requires a persistent foreground service to run autonomously. Many Android OEMs
aggressively kill background services even when they have a valid foreground notification.
This guide explains how to exclude AION from battery optimization on each major OEM.

## Why This Matters

Without exclusion:
- AION's foreground service will be killed after 10–30 minutes in the background
- Model downloads will be interrupted
- Autonomous triggers (notification-based, time-based) will not fire
- The agent will appear to "stop working" until you reopen the app

---

## Samsung One UI

1. Open **Settings** → **Battery and device care** → **Battery**
2. Tap **Background usage limits**
3. Tap **Never sleeping apps** → **Add apps**
4. Select **AION**
5. Also: **Settings** → **Apps** → **AION** → **Battery** → Set to **Unrestricted**

## Xiaomi MIUI / HyperOS

1. Open **Settings** → **Apps** → **Manage apps** → **AION**
2. Tap **Battery saver** → Select **No restrictions**
3. Open **Settings** → **Apps** → **Autostart**
4. Enable **AION** (critical — MIUI kills NotificationListenerService without autostart)
5. Open **Recents** → Long-press AION card → Tap the lock icon

## Oppo / OnePlus ColorOS

1. Open **Settings** → **Battery** → **Battery optimization**
2. Tap **Apps** → **AION** → Select **Don't optimize**
3. Open **Settings** → **Apps** → **App management** → **AION**
4. Enable **Allow background activity** and **Allow autostart**
5. Recents: Lock AION by tapping the menu dots on the recents card

## Huawei EMUI

1. Open **Phone Manager** → **Battery** → **App launch**
2. Find **AION** → Set to **Manage manually**
3. Enable all three: **Auto-launch**, **Secondary launch**, **Run in background**
4. Open **Settings** → **Battery** → **More battery settings**
5. Disable **Power-intensive apps** optimization
6. Recents: Lock AION card

## Google Pixel (Stock Android)

1. Open **Settings** → **Apps** → **AION** → **Battery**
2. Select **Unrestricted**
3. This is usually sufficient — Pixel does not kill foreground services aggressively.

## Generic (Other OEMs)

1. **Settings** → **Apps** → **AION** → **Battery** → **Unrestricted**
2. **Settings** → **Battery** → **Battery optimization** → **AION** → **Don't optimize**
3. Lock AION in the recent apps screen (varies by OEM)
4. Check **Settings** → **Apps** → **Special access** → **Notification access** after every restart
   (OEMs may silently revoke NotificationListenerService access on reboot)

## Verifying It Works

After applying the above:
1. Close the AION app (swipe away from recents)
2. Wait 15 minutes
3. Send yourself a text message
4. Verify AION's notification is still visible and the agent responds

If the notification disappears within 30 minutes of closing the app, the OEM is still
killing the service. Check for additional OEM-specific battery management settings.
