package com.hw.bluetoothdemo

import android.bluetooth.BluetoothAdapter


/**
 * @author hewei(David)
 * @date 2020/10/29  4:55 PM
 * @Copyright ©  Shanghai Xinke Digital Technology Co., Ltd.
 * @description
 */

class BluetoothUtil {
    companion object{
        // 获取本地蓝牙适配器
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        fun scanBluth() { // 设置进度条
//            setProgressBarIndeterminateVisibility(true)
//            setTitle("正在搜索...")
            // 判断是否在搜索,如果在搜索，就取消搜索
            if (mBluetoothAdapter.isDiscovering) {
                mBluetoothAdapter.cancelDiscovery()
            }
            // 开始搜索
            mBluetoothAdapter.startDiscovery()
        }
    }

}