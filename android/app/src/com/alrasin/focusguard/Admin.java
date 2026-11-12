package com.alrasin.focusguard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device admin with no policies: only there so the app can't be uninstalled in one tap. */
public class Admin extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context c, Intent i) {
        return "Focus Guard will stop protecting you. Strict mode exists for moments like this one.";
    }
}
