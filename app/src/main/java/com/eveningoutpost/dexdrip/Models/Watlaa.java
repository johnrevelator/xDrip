package com.eveningoutpost.dexdrip.Models;

/**
 * Created by Alexander Klimov on 02.05.2019.
 */

public class Watlaa {



    private static final String TAG = "Watlaa";



    public static boolean isWatlaa() {
        final ActiveBluetoothDevice activeBluetoothDevice = ActiveBluetoothDevice.first();
        if (activeBluetoothDevice == null || activeBluetoothDevice.name == null) {
            return false;
        }

        return activeBluetoothDevice.name.contentEquals("Watlaa A0");
    }

    public static String getWatlaaAdress(){
        final ActiveBluetoothDevice activeBluetoothDevice = ActiveBluetoothDevice.first();
        if (activeBluetoothDevice == null || activeBluetoothDevice.name == null) {
            return null;
        }

        return activeBluetoothDevice.address;
    }

}
