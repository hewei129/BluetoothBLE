package com.hw.bluetoothlib

import android.bluetooth.BluetoothDevice


/**
 * @author hewei(David)
 * @date 2020/11/5  10:17 AM
 * @Copyright ©  Shanghai Xinke Digital Technology Co., Ltd.
 * @description
 */

class MyBluetoothDevice(bluetoothDevice: BluetoothDevice, rssi: Int) {
    private val bluetoothDevice: BluetoothDevice = bluetoothDevice
    private var Rssi: Int
    fun getBluetoothDevice(): BluetoothDevice {
        return bluetoothDevice
    }

    fun getRssi(): Int {
        return Rssi
    }

    fun setRssi(rssi: Int) {
        Rssi = rssi
    }

    init {
        Rssi = rssi
    }
}
