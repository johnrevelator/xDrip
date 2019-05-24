package com.eveningoutpost.dexdrip.insulin.watlaa

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context

import java.util.Calendar
import java.util.UUID

import android.content.Context.BLUETOOTH_SERVICE


object CurrentTimeService {
    private val SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    private val CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")
    private val LOCAL_TIME_INFO_CHARACTERISTIC_UUID = UUID.fromString("00002A0F-0000-1000-8000-00805f9b34fb")

    private var sGattServer: BluetoothGattServer? = null

    private val GATT_SERVICE = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    init {
        GATT_SERVICE.addCharacteristic(
                BluetoothGattCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ)
        )
        GATT_SERVICE.addCharacteristic(
                BluetoothGattCharacteristic(LOCAL_TIME_INFO_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ)
        )
    }

    private class CurrentTimeCallback : BluetoothGattServerCallback() {

        private var mGattServer: BluetoothGattServer? = null

        internal fun setGattServer(gattServer: BluetoothGattServer) {
            mGattServer = gattServer
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            when {
                CURRENT_TIME_CHARACTERISTIC_UUID == characteristic.uuid -> mGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, TimeUtil.exactTime(Calendar.getInstance()))
                LOCAL_TIME_INFO_CHARACTERISTIC_UUID == characteristic.uuid -> mGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, TimeUtil.timezoneWithDstOffset(Calendar.getInstance()))
                else -> mGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }


    fun startServer(context: Context): Boolean {
        if (sGattServer == null) {
            val manager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val callback = CurrentTimeCallback()
            sGattServer = manager.openGattServer(context, callback)
            if (sGattServer == null) {
                return false
            }
            sGattServer!!.addService(GATT_SERVICE)
            callback.setGattServer(sGattServer!!)
        }
        return true
    }


    fun stopServer() {
        sGattServer
        if (sGattServer != null) {
            sGattServer!!.close()
            sGattServer = null
        }
    }

}