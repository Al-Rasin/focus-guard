package com.alrasin.focusguard;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;

import java.io.File;

/**
 * Is protection really running? Shared by the Quick Settings tile, the home-screen widget and
 * the keep-alive job, so the user can see at a glance that nothing has silently stopped.
 */
final class Health {
    private static final long FRESH_MS = 3 * 60_000L;

    private Health() {
    }

    static boolean active(Context c) {
        if (!Setup.accessibilityOn(c)) return false;
        if (GuardService.instance != null) return true;
        File beat = new File(c.getFilesDir(), "heartbeat");
        return beat.exists() && System.currentTimeMillis() - beat.lastModified() < FRESH_MS;
    }

    /** Ask the tile and the widget to redraw (state or counts changed). */
    static void changed(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, Tile.class));
        } catch (RuntimeException ignored) { // tile not added: nothing to update
        }
        Widget.refresh(c);
    }
}
