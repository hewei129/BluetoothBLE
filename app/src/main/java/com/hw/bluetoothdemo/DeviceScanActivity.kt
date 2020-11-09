package com.hw.bluetoothdemo

import android.Manifest
import android.app.Activity
import android.app.ListActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hw.bluetoothlib.MyBluetoothDevice
import com.hw.bluetoothlib.calDistance
import java.util.*
import kotlin.collections.HashMap


/**
 * @author hewei(David)
 * @date 2020/11/5  10:23 AM
 * @Copyright ©  Shanghai Xinke Digital Technology Co., Ltd.
 * @description
 */

class DeviceScanActivity : ListActivity() {
    private var mLeDeviceListAdapter: LeDeviceListAdapter? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var myBluetoothDeviceMap: MutableMap<String, MyBluetoothDevice> ?= null
    private var mBleScanner //BLE扫描器
            : BluetoothLeScanner? = null
    private var mScanning = false
    private var mHandler: Handler? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar!!.setTitle(R.string.title_devices)
        bluetoothPermissions()
        mHandler = Handler()
        myBluetoothDeviceMap = HashMap<String, MyBluetoothDevice>()
        // Use this check to determine whether BLE is supported on the device.  Then you can
// selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
// BluetoothAdapter through BluetoothManager.
        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleScanner = mBluetoothAdapter!!.bluetoothLeScanner
        }
    }


    // 定义获取基于地理位置的动态权限
    private fun bluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1
            )
        }
    }

    /**
     * 重写onRequestPermissionsResult方法
     * 获取动态权限请求的结果,再开启蓝牙
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mBluetoothAdapter == null) {
                Toast.makeText(this, "本机未找到蓝牙功能", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "用户拒绝了权限", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).isVisible = false
            menu.findItem(R.id.menu_scan).isVisible = true
            menu.findItem(R.id.menu_refresh).actionView = null
        } else {
            menu.findItem(R.id.menu_stop).isVisible = true
            menu.findItem(R.id.menu_scan).isVisible = false
            menu.findItem(R.id.menu_refresh).setActionView(
                R.layout.actionbar_indeterminate_progress
            )
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                mLeDeviceListAdapter!!.clear()
                scanLeDevice(true)
            }
            R.id.menu_stop -> scanLeDevice(false)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
// fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter!!.isEnabled) {
            if (!mBluetoothAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, DeviceScanActivity.REQUEST_ENABLE_BT)
            }
        }
        // Initializes list view adapter.
        mLeDeviceListAdapter = LeDeviceListAdapter()
        listAdapter = mLeDeviceListAdapter
        scanLeDevice(true)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) { // User chose not to enable Bluetooth.
        if (requestCode == DeviceScanActivity.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        mLeDeviceListAdapter!!.clear()
    }

    override fun onListItemClick(
        l: ListView?,
        v: View?,
        position: Int,
        id: Long
    ) {
        val device = mLeDeviceListAdapter!!.getDevice(position) ?: return
        val intent = Intent(this, DeviceRangingActivity::class.java)
        intent.putExtra(
            DeviceRangingActivity.EXTRAS_DEVICE_NAME,
            device.getBluetoothDevice().name
        )
        intent.putExtra(
            DeviceRangingActivity.EXTRAS_DEVICE_ADDRESS,
            device.getBluetoothDevice().address
        )
        if (mScanning) { //            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mBleScanner!!.startScan(mLeScanCallback2)
            mScanning = false
        }
        startActivity(intent)
    }

    /**
     *
     * @param enable
     */
    private fun scanLeDevice(enable: Boolean) {
        if (enable) { // Stops scanning after a pre-defined scan period.
            mHandler!!.postDelayed({
                mScanning = false
                //                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mBleScanner!!.stopScan(mLeScanCallback2)
                invalidateOptionsMenu()
            }, DeviceScanActivity.SCAN_PERIOD)
            mScanning = true
            //            mBluetoothAdapter.startLeScan(mLeScanCallback);
            mBleScanner!!.startScan(mLeScanCallback2)
        } else {
            mScanning = false
            //            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mBleScanner!!.stopScan(mLeScanCallback2)
        }
        invalidateOptionsMenu()
    }



    // Adapter for holding devices found through scanning.
    private inner class LeDeviceListAdapter : BaseAdapter() {
        private val mLeDevices: ArrayList<MyBluetoothDevice> = ArrayList()
        private val mInflator: LayoutInflater
        fun addDevice(device: MyBluetoothDevice?) {
            if (!mLeDevices.contains(device) //                    && device.getBluetoothDevice().getBondState() == BluetoothDevice.BOND_BONDED
            ) {
                mLeDevices.add(device!!)
            }
        }

        fun getDevice(position: Int): MyBluetoothDevice {
            return mLeDevices[position]
        }

        fun clear() {
            mLeDevices.clear()
        }

        override fun getCount(): Int {
            return mLeDevices.size
        }

        override fun getItem(i: Int): Any {
            return mLeDevices[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(
            i: Int,
            view: View?,
            viewGroup: ViewGroup
        ): View {
            var view = view
            val viewHolder: ViewHolder
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null)
                viewHolder = ViewHolder()
                viewHolder.deviceAddress = view.findViewById<View>(R.id.device_address) as TextView
                viewHolder.deviceName = view.findViewById<View>(R.id.device_name) as TextView
                viewHolder.deviceRssi = view.findViewById<View>(R.id.device_rssi) as TextView
                viewHolder.deviceDis = view.findViewById<View>(R.id.device_distance) as TextView
                view.tag = viewHolder
            } else {
                viewHolder = view.tag as ViewHolder
            }
            val device = mLeDevices[i]
            val deviceName = device.getBluetoothDevice().name
            if (deviceName != null && deviceName.isNotEmpty())
                viewHolder.deviceName?.text = deviceName else viewHolder.deviceName?.setText(R.string.unknown_device)
            viewHolder.deviceAddress?.text = device.getBluetoothDevice().address
            viewHolder.deviceRssi?.text = "Rssi: " + device.getRssi()
            viewHolder.deviceDis?.text = "Distance: "+ calDistance(device.getRssi().toDouble())
            return view!!
        }

        init {
            mBluetoothAdapter?.let{
                val pairedDevices: Set<BluetoothDevice> = it.bondedDevices
                // 判断是否有配对过的设备
                if (pairedDevices.isNotEmpty()) {
                    for (device in pairedDevices) { // 遍历
                        var myBluetoothDevice: MyBluetoothDevice
                        if (myBluetoothDeviceMap == null) myBluetoothDeviceMap = HashMap()
                        if (myBluetoothDeviceMap!!.containsKey(device.address)) {
                            myBluetoothDevice = myBluetoothDeviceMap!![device.address]!!
                            myBluetoothDevice.setRssi(-1)
                        } else {
                            myBluetoothDevice = MyBluetoothDevice(device, -1)
                            myBluetoothDeviceMap!![device.address] = myBluetoothDevice
                        }
                        addDevice(myBluetoothDevice)
                        //                    addDevice(device);
                    }
                }
            }

            mInflator = this@DeviceScanActivity.layoutInflater
        }
        internal inner class ViewHolder {
            var deviceName: TextView? = null
            var deviceAddress: TextView? = null
            var deviceRssi: TextView? = null
            var deviceDis: TextView? = null
        }

    }

    // Device scan callback.
    private val mLeScanCallback =
        LeScanCallback { device, rssi, scanRecord ->
            Log.i(DeviceScanActivity.TAG, "rssi: $rssi")
            Log.i(DeviceScanActivity.TAG, "device: " + device.name)
            runOnUiThread {
                val myBluetoothDevice: MyBluetoothDevice?
                if (myBluetoothDeviceMap == null) myBluetoothDeviceMap =
                    HashMap<String, MyBluetoothDevice>()
                if (myBluetoothDeviceMap!!.containsKey(device.address)) {
                    myBluetoothDevice = myBluetoothDeviceMap!![device.address]
                    myBluetoothDevice!!.setRssi(rssi)
                } else {
                    myBluetoothDevice = MyBluetoothDevice(device, rssi)
                    myBluetoothDeviceMap!![device.address] = myBluetoothDevice
                }
                mLeDeviceListAdapter!!.addDevice(myBluetoothDevice)
                mLeDeviceListAdapter!!.notifyDataSetChanged()
            }
        }
    private val mLeScanCallback2: ScanCallback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            super.onScanResult(callbackType, result)
            val device = result.device
            Log.i(DeviceScanActivity.TAG, "callbackType: $callbackType")
            Log.i(DeviceScanActivity.TAG, "rssi: " + result.rssi)
            Log.i(DeviceScanActivity.TAG, "device: " + device.name)
            runOnUiThread {
                val myBluetoothDevice: MyBluetoothDevice?
                if (myBluetoothDeviceMap == null) myBluetoothDeviceMap =
                    HashMap<String, MyBluetoothDevice>()
                if (myBluetoothDeviceMap!!.containsKey(device.address)) {
                    myBluetoothDevice = myBluetoothDeviceMap!![device.address]
                    myBluetoothDevice!!.setRssi(result.rssi)
                } else {
                    myBluetoothDevice = MyBluetoothDevice(device, result.rssi)
                    myBluetoothDeviceMap!![device.address] = myBluetoothDevice
                }
                mLeDeviceListAdapter!!.addDevice(myBluetoothDevice)
                mLeDeviceListAdapter!!.notifyDataSetChanged()
            }
        }
    }

    companion object {
        private val TAG = DeviceScanActivity::class.java.simpleName
        private const val REQUEST_ENABLE_BT = 1
        // Stops scanning after 10 seconds.
        private const val SCAN_PERIOD: Long = 10000


    }

}