package com.hw.bluetoothdemo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import java.util.*
import kotlin.math.pow


/**
 * @author hewei(David)
 * @date 2020/10/29  5:01 PM
 * @Copyright ©  Shanghai Xinke Digital Technology Co., Ltd.
 * @description
 */

class BluetoothBroadcastReceiver(val tvDevices: TextView) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {

        // 收到的广播类型
        val action = intent!!.action
        // 发现设备的广播
        val device: BluetoothDevice = intent
            .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        if (BluetoothDevice.ACTION_FOUND == action) { // 从intent中获取设备

            val aa: String = tvDevices.text.toString()
            if (aa.contains(device.address)) {
                return
            } else { // 判断是否配对过
                if (device.bondState != BluetoothDevice.BOND_BONDED) { // 添加到列表
                    val rssi = intent.extras!!.getShort(
                        BluetoothDevice.EXTRA_RSSI
                    )
                    val iRssi: Int = Math.abs(rssi.toInt())
                    // 将蓝牙信号强度换算为距离
                    val power = (iRssi - 59) / 25.0
                    val mm: String = Formatter().format("%.2f", 10.0.pow(power)).toString()
                    tvDevices.append(
                        device.name + ":"
                                + device.address + " ：" + mm + "m" + "\n"
                    )
                } else {

                }
            }
            // 搜索完成
        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
            == action
        ) { // 关闭进度条
//            setProgressBarIndeterminateVisibility(true)
//            setTitle("搜索完成！")
            Toast.makeText(context, "搜索完成！", Toast.LENGTH_SHORT).show()
            val rssi = intent.extras!!.getShort(
                BluetoothDevice.EXTRA_RSSI
            )
            val iRssi: Int = Math.abs(rssi.toInt())
            // 将蓝牙信号强度换算为距离
            val power = (iRssi - 59) / 25.0
            val mm: String = Formatter().format("%.2f", 10.0.pow(power)).toString()
            tvDevices.append(
                device.name + ":"
                        + device.address + " ：" + mm + "m" + "\n"
            )
        } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED
            == action
        ) {
            val rssi = intent.extras!!.getShort(
                BluetoothDevice.EXTRA_RSSI
            )
            val iRssi: Int = Math.abs(rssi.toInt())
            // 将蓝牙信号强度换算为距离
            val power = (iRssi - 59) / 25.0
            val mm: String = Formatter().format("%.2f", 10.0.pow(power)).toString()
            tvDevices.append(
                device.name + ":"
                        + device.address + " ：" + mm + "m" + "\n"
            )
        }
    }
}