package com.mkdziner.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets the launcher read which app is playing music (MediaSessionManager needs an
 * enabled notification listener). It reads no notifications. Enable it once over ADB:
 * cmd notification allow_listener com.mkdziner.tvhome/com.mkdziner.tvhome.MediaListener
 */
public class MediaListener extends NotificationListenerService {
}
