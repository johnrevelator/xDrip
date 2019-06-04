package com.eveningoutpost.dexdrip.insulin.watlaa;

import com.eveningoutpost.dexdrip.Services.WatlaaCommandService;

import java.util.UUID;

import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_FIRMWARE_VERSION;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_HARDWARE_VERSION;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_MANUFACTURER;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_MODEL_NUMBER;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_PNP_ID;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_REGULATORY_ID;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_SERIAL_NUMBER;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_SOFTWARE_VERSION;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.INFO_SYSTEM_ID;

// jamorham

public class Constants {

    public final static String BLUETOOTH_WATLAA_TAG = "Bluetooth WATLAA";



    public static final String TAG = WatlaaCommandService.class.getSimpleName();

    public static final UUID GLUCOSE_DATA_SERVICE = UUID.fromString("00001010-1212-EFDE-0137-875F45AC0113");
    public static final UUID CURRENT_TIME_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");

    public static final UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");
    public static final UUID TIME_CHARACTERISTIC = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
    public static final UUID UNIT_CHARACTERISTIC = UUID.fromString("00001017-1212-EFDE-0137-875F45AC0113");
    public static final UUID CALLIBRATION_SLATE_CHARACTERISTIC = UUID.fromString("00001015-1212-EFDE-0137-875F45AC0113");
    public static final UUID CALLIBRATION_INTERCEPT_CHARACTERISTIC = UUID.fromString("00001016-1212-EFDE-0137-875F45AC0113");


/*
    static final UUID[] INFO_CHARACTERISTICS = {INFO_MODEL_NUMBER,
            INFO_FIRMWARE_VERSION, INFO_HARDWARE_VERSION, INFO_SOFTWARE_VERSION, INFO_MANUFACTURER,
            INFO_REGULATORY_ID, INFO_PNP_ID, INFO_SERIAL_NUMBER, INFO_SYSTEM_ID};

    static final UUID[] PRINTABLE_INFO_CHARACTERISTICS = {INFO_MODEL_NUMBER, INFO_FIRMWARE_VERSION,
            INFO_HARDWARE_VERSION, INFO_MANUFACTURER};

    static final UUID[] HEXDUMP_INFO_CHARACTERISTICS = {INFO_MODEL_NUMBER, INFO_SERIAL_NUMBER, INFO_SYSTEM_ID,
            INFO_FIRMWARE_VERSION, INFO_HARDWARE_VERSION, INFO_MANUFACTURER};

    public static final float MULTIPLIER = 2.0f;
    public static final int COUNTER_START = 0;
    public static final int COUNTER_ID = 195850905;*/

}
