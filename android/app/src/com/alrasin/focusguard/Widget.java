package com.alrasin.focusguard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;

/**
 * Home-screen widget: protection status, opens left per target and minutes today.
 * Redrawn after each open, when the service starts/stops, by the keep-alive job and every 30 min.
 */
public class Widget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids);
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
        if (ids != null && ids.length > 0) draw(c, m, ids);
    }

    private static void draw(Context c, AppWidgetManager m, int[] ids) {
        boolean on = Health.active(c);
        Limits limits = new Limits(c);
        SharedPreferences p = c.getSharedPreferences("limits", Context.MODE_PRIVATE);
        Stats.Day today = Stats.day(p, limits.today());
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.w_status, L.t(c, on ? "Protected" : "Stopped – tap to fix"));
        v.setTextColor(R.id.w_status, Color.parseColor(on ? Ui.GREEN : Ui.RED));
        String s = limits.summary();
        v.setTextViewText(R.id.w_left, L.t(c, s));
        v.setTextViewText(R.id.w_today, L.t(c, "Today: %s min · went back %s times"
                .replaceFirst("%s", String.valueOf((today.seconds + 59) / 60))
                .replaceFirst("%s", String.valueOf(today.skips))));
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, v);
    }
}
