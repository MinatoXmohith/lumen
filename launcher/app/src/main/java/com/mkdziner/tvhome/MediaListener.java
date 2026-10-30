package com.mkdziner.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Lets the launcher see which app is playing (MediaSessionManager needs an enabled notification listener). It reads
 * no notifications. Android keeps it bound while other apps are in front, so it also hosts {@link WatchTracker},
 * which fills the Continue watching row. Enable it once over ADB:
 * cmd notification allow_listener com.mkdziner.tvhome/com.mkdziner.tvhome.MediaListener
 */
public class MediaListener extends NotificationListenerService {

    private WatchTracker tracker;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (tracker == null) tracker = new WatchTracker(this);
        tracker.start();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (tracker != null) tracker.stop();
    }
}
