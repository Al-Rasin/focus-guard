package com.alrasin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.Calendar;

/**
 * Wall time that can't be moved by hand. With automatic time on, the system clock is trusted
 * (network time) and becomes the anchor. With it off, time = anchor + time since the anchor
 * (elapsedRealtime), so setting the clock forward/back gives no new day, opens or unlock time.
 * A reboot re-anchors to the system clock (known limit).
 */
final class Clock {
    private static final String PREFS = "clock";

    private Clock() {
    }

    static long now(Context ctx) {
        long wall = System.currentTimeMillis(), rt = SystemClock.elapsedRealtime();
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int boot = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        boolean auto = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
        long aWall = p.getLong("wall", 0), aRt = p.getLong("rt", -1);
        int aBoot = p.getInt("boot", -2);
        if (auto || aBoot != boot || aRt < 0 || rt < aRt) {
            if (auto) {
                // Re-anchor at most once a minute to keep writes rare.
                if (aBoot != boot || Math.abs((aWall + rt - aRt) - wall) > 5_000 || rt - aRt > 60_000) {
                    p.edit().putLong("wall", wall).putLong("rt", rt).putInt("boot", boot).apply();
                }
                return wall;
            }
            if (aBoot != boot || aRt < 0 || rt < aRt) {
                p.edit().putLong("wall", wall).putLong("rt", rt).putInt("boot", boot).apply();
                return wall;
            }
        }
        return aWall + (rt - aRt);
    }

    static Calendar calendar(Context ctx) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now(ctx));
        return c;
    }
}
