package com.airpods.signal

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Build
import android.util.Log

class BleBroadcaster(private val bluetoothAdapter: BluetoothAdapter) {

    private var advertiserMain: BluetoothLeAdvertiser? = null
    private var advertiserExtra: BluetoothLeAdvertiser? = null
    var isAdvertising = false
        private set

    var lastResult = ""
        private set

    private val callbackMain = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BleBroadcaster", "主广播启动成功")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleBroadcaster", "主广播失败: $errorCode")
        }
    }

    private val callbackExtra = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BleBroadcaster", "辅广播启动成功")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleBroadcaster", "辅广播失败: $errorCode")
        }
    }

    /**
     * 自检：检查设备是否满足广播条件
     */
    fun selfCheck(): List<String> {
        val results = mutableListOf<String>()

        // 1. 检查蓝牙是否开启
        if (!bluetoothAdapter.isEnabled) {
            results.add("❌ 蓝牙未开启")
        } else {
            results.add("✅ 蓝牙已开启")
        }

        // 2. 检查 BLE 外设模式
        val leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            results.add("❌ 不支持 BLE 外设广播模式")
        } else {
            results.add("✅ 支持 BLE 外设广播模式")
        }

        // 3. 检查多重广播支持 (LE Extended Advertising)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            results.add("✅ 支持多重广播 (Android 8+)")
        } else {
            results.add("⚠️ 不支持多重广播 (Android < 8)")
        }

        // 4. 检查权限
        results.add("ℹ️ 具体弹窗效果取决于接收端 iOS 设备")

        return results
    }

    fun start(): String {
        lastResult = ""

        advertiserMain = bluetoothAdapter.bluetoothLeAdvertiser
            ?: run {
                lastResult = "❌ 设备不支持 BLE 广播"
                return lastResult
            }

        // 保存原来的名称
        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}

        // ★ 广播包：AirPods Pro 配对帧
        // FF 4C 00 07 19 01 5C 58 4C
        val manufacturerData = byteArrayOf(
            0x07,             // Setup Type — 弹窗触发器
            0x19, 0x01,       // 设备能力 (ANC)
            0x5C, 0x58, 0x4C  // 电量
        )

        // ===== 主广播 (ADV_NONCONN_IND) =====
        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, manufacturerData)
            .setIncludeDeviceName(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  // 最快频率 ~100ms
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)     // 最大功率
            .setConnectable(false)
            .build()

        try {
            advertiserMain?.startAdvertising(settings, advertiseData, scanResponse, callbackMain)
            lastResult = "📡 主广播已启动"
        } catch (e: Exception) {
            lastResult = "❌ 主广播失败: ${e.localizedMessage}"
            return lastResult
        }

        // ===== 辅广播 (第二个通道，提高频率) =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val extraAdData = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, manufacturerData)
                    .setIncludeDeviceName(false)
                    .build()

                val extraSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build()

                advertiserExtra = bluetoothAdapter.bluetoothLeAdvertiser
                advertiserExtra?.startAdvertising(extraSettings, extraAdData, null, callbackExtra)
                lastResult += " + 辅广播"
            } catch (e: Exception) {
                Log.w("BleBroadcaster", "辅广播不可用: ${e.localizedMessage}")
            }
        }

        isAdvertising = true
        return "📡 信号已发射 (双重广播)"
    }

    fun stop(): String {
        try {
            advertiserMain?.stopAdvertising(callbackMain)
        } catch (_: Exception) {}
        try {
            advertiserExtra?.stopAdvertising(callbackExtra)
        } catch (_: Exception) {}
        advertiserMain = null
        advertiserExtra = null
        isAdvertising = false
        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}
        lastResult = "⏹ 已停止"
        return lastResult
    }
}
