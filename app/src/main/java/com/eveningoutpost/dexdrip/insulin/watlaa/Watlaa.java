package com.eveningoutpost.dexdrip.insulin.watlaa;

import com.eveningoutpost.dexdrip.UtilityModels.Pref;

/**
 * jamorham
 */

public class Watlaa {

    private static final String PREF_WATLAA_MAC = "watlaa_mac";

    static final String STORE_WATLAA_ADVERT = "watlaa-advert-";
    static final String STORE_WATLAA_INFOS = "watlaa-infos-";
    static final String STORE_WATLAA_BATTERY = "watlaa-battery-";

    static final float DEFAULT_BOND_UNITS = 2.0f;

    static String getMac() {
        return Pref.getString(PREF_WATLAA_MAC, null);
    }

    static void setMac(final String mac) {
        Pref.setString(PREF_WATLAA_MAC, mac);
    }

    static boolean soundsEnabled() {
        return Pref.getBooleanDefaultFalse("watlaa_sounds");
    }

}
