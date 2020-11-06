package com.hw.bluetoothdemo

import android.app.Activity
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ExpandableListView
import android.widget.ExpandableListView.OnChildClickListener
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView
import com.hw.bluetoothlib.BluetoothLeService
import com.hw.bluetoothlib.SampleGattAttributes
import java.util.*


/**
 * @author hewei(David)
 * @date 2020/11/5  10:29 AM
 * @Copyright ©  Shanghai Xinke Digital Technology Co., Ltd.
 * @description
 */

class DeviceRangingActivity : Activity(){
    companion object{
        private val TAG = DeviceRangingActivity::class.java.simpleName
        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
    }

    private var mConnectionState: TextView? = null
    private var mDataField: TextView? = null
    private var mRssiField: TextView? = null
    private var mDistanceField: TextView? = null
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mGattServicesList: ExpandableListView? = null
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mGattCharacteristics =
        ArrayList<ArrayList<BluetoothGattCharacteristic>>()
    private var mConnected = false
    private var mNotifyCharacteristic: BluetoothGattCharacteristic? = null

    private val LIST_NAME = "NAME"
    private val LIST_UUID = "UUID"


    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService =
                (service as BluetoothLeService.LocalBinder).service
            if (mBluetoothLeService?.initialize() != true) {
                Log.e(DeviceRangingActivity.TAG, "Unable to initialize Bluetooth")
                finish()
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService?.connect(mDeviceAddress)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBluetoothLeService = null
        }
    }

    // Handles various events fired by the Service.
// ACTION_GATT_CONNECTED: connected to a GATT server.
// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
//                        or notification operations.
    private val mGattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothLeService.ACTION_GATT_CONNECTED == action) {
                mConnected = true
                updateConnectionState(R.string.connected)
                invalidateOptionsMenu()
                startReadRssi()
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED == action) {
                mConnected = false
                updateConnectionState(R.string.disconnected)
                invalidateOptionsMenu()
                clearUI()
                stopReadRssi()
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED == action) { // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService!!.getSupportedGattServices())
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE == action) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA))
            } else if (BluetoothLeService.ACTION_READ_RSSI == action) {
                displayRssi(intent.getStringExtra(BluetoothLeService.EXTRA_DATA))
            }
        }
    }

    // If a given GATT characteristic is selected, check for supported features.  This sample
// demonstrates 'Read' and 'Notify' features.  See
// http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
// list of supported characteristic features.
    private val servicesListClickListner =
        OnChildClickListener { parent, v, groupPosition, childPosition, id ->
            if (mGattCharacteristics != null) {
                val characteristic =
                    mGattCharacteristics[groupPosition][childPosition]
                val charaProp = characteristic.properties
                if (charaProp or BluetoothGattCharacteristic.PROPERTY_READ > 0) { // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    mNotifyCharacteristic?.let {
                        mBluetoothLeService?.setCharacteristicNotification(it, false)
                        mNotifyCharacteristic = null
                    }
                    mBluetoothLeService?.readCharacteristic(characteristic)
                }
                if (charaProp or BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                    mNotifyCharacteristic = characteristic
                    mBluetoothLeService?.setCharacteristicNotification(
                        characteristic, true
                    )
                }
                return@OnChildClickListener true
            }
            false
        }

    private fun clearUI() {
        mGattServicesList!!.setAdapter(null as SimpleExpandableListAdapter?)
        mDataField!!.setText(R.string.no_data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gatt_services_characteristics)
        val intent = intent
        mDeviceName = intent.getStringExtra(DeviceRangingActivity.EXTRAS_DEVICE_NAME)
        mDeviceAddress = intent.getStringExtra(DeviceRangingActivity.EXTRAS_DEVICE_ADDRESS)
        // Sets up UI references.
        (findViewById<View>(R.id.device_address) as TextView).text = mDeviceAddress
        mGattServicesList =
            findViewById<View>(R.id.gatt_services_list) as ExpandableListView
        mGattServicesList!!.setOnChildClickListener(servicesListClickListner)
        mConnectionState = findViewById<View>(R.id.connection_state) as TextView
        mDataField = findViewById<View>(R.id.data_value) as TextView
        mRssiField = findViewById<View>(R.id.data_rssi) as TextView
        mDistanceField = findViewById<View>(R.id.data_distance) as TextView
        actionBar!!.title = mDeviceName
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        val gattServiceIntent = Intent(
            this,
            BluetoothLeService::class.java
        )
        bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter())
        if (mBluetoothLeService != null) {
            val result = mBluetoothLeService?.connect(mDeviceAddress)
            Log.d(DeviceRangingActivity.TAG, "Connect request result=$result")
        }
    }

    private var isReadRssi = true
    private var readRSSI: Thread? = null
    /**
     * 读取蓝牙RSSi线程
     */
    private fun startReadRssi() {
        isReadRssi = true
        val Rssi = 0
        readRSSI = object : Thread() {
            override fun run() { //                super.run();
                while (isReadRssi) {
                    try {
                        sleep(200)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    //如果读取蓝牙RSSi回调成功
                    mBluetoothLeService?.getRssiVal()
//                    if (mBluetoothLeService != null && mBluetoothLeService?.getRssiVal() == true) { //获取已经读到的RSSI值
//                        Rssi   = mBluetoothLeService?.getRssiVal();
//                    mHandler.obtainMessage(READRSSI, Rssi).sendToTarget();
//                    }
                }
            }
        }
        readRSSI?.start()
    }

    private fun stopReadRssi() {
        if (readRSSI != null) {
            isReadRssi = false
            readRSSI = null
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mGattUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnection)
        mBluetoothLeService = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gatt_services, menu)
        if (mConnected) {
            menu.findItem(R.id.menu_connect).isVisible = false
            menu.findItem(R.id.menu_disconnect).isVisible = true
        } else {
            menu.findItem(R.id.menu_connect).isVisible = true
            menu.findItem(R.id.menu_disconnect).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_connect -> {
                mBluetoothLeService!!.connect(mDeviceAddress)
                return true
            }
            R.id.menu_disconnect -> {
                mBluetoothLeService!!.disconnect()
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread { mConnectionState!!.setText(resourceId) }
    }

    private fun displayData(data: String?) {
        if (data != null) {
            mDataField!!.text = data
        }
    }

    private fun displayRssi(rssi: String?) {
        if (rssi != null) {
            mRssiField!!.text = rssi
            mDistanceField!!.text = calDistance(java.lang.Double.valueOf(rssi))
        }
    }

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
    private fun calDistance(rssi: Double): String? {
        if(rssi >= 0) return "0.0米"
        val Rssi = Math.abs(rssi)
        val power = (Rssi - 59) / (10.0 * 3.0)
        //80=10米    55=1米
        val location = Math.pow(10.0, power).toString()
        return if (location.length >= 6) location.substring(0, 6) + "米" else location + "米"
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
// In this sample, we populate the data structure that is bound to the ExpandableListView
// on the UI.
    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null) return
        var uuid: String? = null
        val unknownServiceString = resources.getString(R.string.unknown_service)
        val unknownCharaString =
            resources.getString(R.string.unknown_characteristic)
        val gattServiceData =
            ArrayList<HashMap<String, String?>>()
        val gattCharacteristicData =
            ArrayList<ArrayList<HashMap<String, String?>>>()
        mGattCharacteristics =
            ArrayList()
        // Loops through available GATT Services.
        for (gattService in gattServices) {
            val currentServiceData =
                HashMap<String, String?>()
            uuid = gattService.uuid.toString()
            currentServiceData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownServiceString)
            currentServiceData[LIST_UUID] = uuid
            gattServiceData.add(currentServiceData)
            val gattCharacteristicGroupData =
                ArrayList<HashMap<String, String?>>()
            val gattCharacteristics =
                gattService.characteristics
            val charas =
                ArrayList<BluetoothGattCharacteristic>()
            // Loops through available Characteristics.
            for (gattCharacteristic in gattCharacteristics) {
                charas.add(gattCharacteristic)
                val currentCharaData =
                    HashMap<String, String?>()
                uuid = gattCharacteristic.uuid.toString()
                currentCharaData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownCharaString)
                currentCharaData[LIST_UUID] = uuid
                gattCharacteristicGroupData.add(currentCharaData)
            }
            mGattCharacteristics.add(charas)
            gattCharacteristicData.add(gattCharacteristicGroupData)
        }
        val gattServiceAdapter = SimpleExpandableListAdapter(
            this,
            gattServiceData,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf(LIST_NAME, LIST_UUID),
            intArrayOf(android.R.id.text1, android.R.id.text2),
            gattCharacteristicData,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf(LIST_NAME, LIST_UUID),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        mGattServicesList!!.setAdapter(gattServiceAdapter)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        intentFilter.addAction(BluetoothLeService.ACTION_READ_RSSI)
        return intentFilter
    }
}