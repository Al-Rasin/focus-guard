package com.alrasin.focusguard;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile: lit while protection runs, dim with "Stopped" when it doesn't. Tapping it
 * opens the app (the fix-it checklist when stopped). It can't switch anything off.
 */
public class Tile extends TileService {
    @Override
    public void onStartListening() {
        android.service.quicksettings.Tile t = getQsTile();
        if (t == null) return;
        boolean on = Health.active(this);
        t.setState(on ? android.service.quicksettings.Tile.STATE_ACTIVE
                : android.service.quicksettings.Tile.STATE_INACTIVE);
        t.setLabel("Focus Guard");
        String sub = L.t(this, on ? "Protected" : "Stopped – tap to fix");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(sub);
        t.setContentDescription("Focus Guard: " + sub);
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
