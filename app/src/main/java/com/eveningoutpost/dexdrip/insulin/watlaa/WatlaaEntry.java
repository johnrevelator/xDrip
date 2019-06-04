package com.eveningoutpost.dexdrip.insulin.watlaa;


import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.UtilityModels.Inevitable;
import com.eveningoutpost.dexdrip.UtilityModels.Pref;
import com.eveningoutpost.dexdrip.insulin.inpen.InPenService;

/**
 * jamorham
 *
 * Watlaa lightweight entry class
 */

public class WatlaaEntry {

    public static final String ID_WATLAA = "Watlaa";
    public static long started_at = -1;

    public static boolean isEnabled() {
        return Pref.getBoolean("watlaa_enabled", true);
    }

    private static void startWith(final String function) {
        started_at = 1;
        Inevitable.task("watlaa-changed-" + function, 1000, () -> JoH.startService(WatlaaService.class, "function", function));
    }

    public static void startWithRefresh() {
        startWith("refresh");
    }

    public static void startWithReset() {
        startWith("reset");
    }

    public static void startIfEnabled() {
        if (isEnabled()) {
            if (JoH.ratelimit("watlaa-start", 40)) {
                startWithRefresh();
            }
        }
    }

    public static boolean isStarted() {
        return started_at != -1;
    }

    public static void immortality() {
        if (!isStarted()) {
            startIfEnabled();
        }
    }

}
