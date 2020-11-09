package com.hw.bluetoothlib


/**
 * @author hewei(David)
 * @date 2020/11/6  6:14 PM
 * @Copyright ©  Shanghai Xinke Digital Technology Co., Ltd.
 * @description
 */

 /*
        d = 10^((abs(RSSI) - A) / (10 * n))
         其中：
        d - 计算所得距离
        RSSI - 接收信号强度（负值）
        A - 发射端和接收端相隔1米时的信号强度
        n - 环境衰减因子

        由于所处环境不同，每台发射源（蓝牙设备）对应参数值都不一样。按道理，公式里的每项参数都应该做实验（校准）获得。
        当你不知道周围蓝牙设备准确位置时，只能给A和n赋经验值（如本例）。
     */
fun calDistance(rssi: Double): String? {
    if(rssi >= 0) return "0.0米"
    val Rssi = Math.abs(rssi)
    val power = (Rssi - 70) / (10.0 * 2.0)
    //80=10米    55=1米
    val location = Math.pow(10.0, power).toString()
    return if (location.length >= 6) location.substring(0, 6) + "米" else location + "米"
}