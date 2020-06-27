package ai.kun.opentracesdk_fat.alarm

import ai.kun.opentracesdk_fat.BLETrace
import ai.kun.opentracesdk_fat.dao.Device
import ai.kun.opentracesdk_fat.util.Constants
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log

object BtleScanCallback: ScanCallback() {
    private val TAG = "BtleScanCallback"
    val mScanResults = HashMap<String, Device>()
    val handler = Handler()

    override fun onScanResult(
        callbackType: Int,
        result: ScanResult
    ) {
        addScanResult(result)
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        for (result in results) {
            addScanResult(result)
        }
    }

    override fun onScanFailed(errorCode: Int) {
        Log.e(TAG, "BLE Scan Failed with code $errorCode")
    }

    private fun addScanResult(result: ScanResult) {
        synchronized(this) {
            val deviceAddress = result.device.address
            var uuid: ParcelUuid? = result.scanRecord?.serviceUuids?.get(0)
            var isAndroid = (uuid.toString().startsWith(Constants.ANDROID_PREFIX))

            // Only record devices where the UUID is one from our app...
            if (isAndroid || uuid.toString().startsWith(Constants.IOS_PREFIX)) {
                var rssi: Int = result.rssi
                var txPower: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result.txPower
                } else {
                    -1
                }

                var timeStampNanos: Long = result.timestampNanos
                val timeStamp: Long = System.currentTimeMillis()
                var sessionId = deviceAddress

                var newDevice = Device(
                    uuid.toString(),
                    rssi,
                    txPower,
                    timeStampNanos,
                    timeStamp,
                    sessionId,
                    BLETrace.isTeamMember(uuid.toString()),
                    isAndroid
                )

                // Use a rolling average...
                if (mScanResults.containsKey(deviceAddress)) {
                    newDevice.rssi = (newDevice.rssi + mScanResults[deviceAddress]!!.rssi).div(2)
                    newDevice.txPower =
                        (newDevice.txPower + mScanResults[deviceAddress]!!.txPower).div(2)
                }
                mScanResults[deviceAddress] = newDevice
            }
        }
    }
}