package com.airpods.signal

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Build
import android.util.Log

class BleBroadcaster(private val bluetoothAdapter: BluetoothAdapter) {

    private var advertiser: BluetoothLeAdvertiser? = null
    var isAdvertising = false
        private set

    var lastResult = ""
        private set

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.d("BleBroadcaster", "广播启动成功")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "已经在广播"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "广播数据过大"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持此功能"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播者过多"
                else -> "未知错误($errorCode)"
            }
            Log.e("BleBroadcaster", "广播启动失败: $errorMsg")
        }
    }

    fun selfCheck(): List<String> {
        val results = mutableListOf<String>()
        if (!bluetoothAdapter.isEnabled) {
            results.add("❌ 蓝牙未开启")
        } else {
            results.add("✅ 蓝牙已开启")
        }
        val leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            results.add("❌ 不支持 BLE 外设广播模式")
        } else {
            results.add("✅ 支持 BLE 外设广播模式")
        }
        results.add("\n📡 广播帧 (ADV_IND):")
        results.add("   Flags: LE Limited Discoverable")
        results.add("   Apple: 4C 00 07 19 01 5C 58 4C")
        results.add("   设备名: AirPods Pro (广播+扫描回应)")
        results.add("   模式: 可连接 + 高功率")
        return results
    }

    fun start(): String {
        lastResult = ""
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: run {
                lastResult = "❌ 设备不支持 BLE 广播"
                return lastResult
            }

        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}

        // ★ AirPods Pro 完整配对帧
        // iOS 需要看到：
        // 1. Apple 公司 ID 0x004C + Setup Type 0x07 ✅
        // 2. 设备名 "AirPods Pro" 在 ADV_IND 中 ← 之前漏了
        // 3. 可连接广播 (ADV_IND) ← 之前是 NONCONN
        val manufacturerData = byteArrayOf(
            0x07,             // Setup Type — 弹窗触发器
            0x19, 0x01,       // 设备能力 (ANC + 自适应)
            0x5C, 0x58, 0x4C  // 电量: 左92% 右88% 盒76%
        )

        // ★ 广播数据：包含设备名！
        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, manufacturerData)
            .setIncludeDeviceName(true)      // ★ 关键：设备名在广播包中
            .build()

        // ★ 扫描回应包也包含设备名
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        // ★ 可连接广播 (ADV_IND) — 和真实 AirPods 一致
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)             // ★ 关键：可连接！
            .build()

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)
            isAdvertising = true
            lastResult = "📡 信号已发射 (ADV_IND+名称)"
            return lastResult
        } catch (e: SecurityException) {
            lastResult = "❌ 缺少蓝牙广播权限"
        } catch (e: Exception) {
            lastResult = "❌ 广播失败: ${e.localizedMessage}"
        }

        return lastResult
    }

    fun stop(): String {
        try {
            advertiser?.stopAdvertising(callback)
        } catch (_: Exception) {}
        advertiser = null
        isAdvertising = false
        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}
        lastResult = "⏹ 已停止"
        return lastResult
    }
}
