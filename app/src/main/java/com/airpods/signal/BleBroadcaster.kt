package com.airpods.signal

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.ParcelUuid
import java.util.*

class BleBroadcaster(private val bluetoothAdapter: BluetoothAdapter) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var originalName: String? = null
    var isAdvertising = false
        private set

    // AirPods 广播回调
    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
        }
        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
        }
    }

    fun start(): String {
        // 1. 检查 BLE 外设模式支持
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: return "❌ 设备不支持 BLE 广播"

        // 2. 保存原名称，设为 AirPods Pro
        originalName = bluetoothAdapter.name
        bluetoothAdapter.name = "AirPods Pro"

        // 3. 厂商数据 —— 这是弹窗关键！
        //    系统会自动加上 AD Type 0xFF 和 Apple Company ID 0x004C
        val manufacturerData = byteArrayOf(
            0x07,             // ★ Setup Type — 弹窗关键
            0x19, 0x01,       // Device Capabilities
            0x5C, 0x58, 0x4C  // Battery: L=92% R=88% Case=76%
        )

        // 4. 广播数据包
        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, manufacturerData)  // Apple Company ID
            .setIncludeDeviceName(true)                     // "AirPods Pro"
            .build()

        // 5. 扫描回应包（额外数据）
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        // 6. 广播参数
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  // 高频广播
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)     // 最大功率
            .setConnectable(true)                                            // 可连接
            .build()

        // 7. 开始广播
        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)
            return "📡 信号已发射"
        } catch (e: Exception) {
            bluetoothAdapter.name = originalName
            return "❌ 广播失败: ${e.localizedMessage}"
        }
    }

    fun stop(): String {
        // 停止广播
        advertiser?.stopAdvertising(callback)
        isAdvertising = false
        // 恢复原设备名
        originalName?.let { bluetoothAdapter.name = it }
        return "⏹ 已停止"
    }
}
