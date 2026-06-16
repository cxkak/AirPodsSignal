package com.airpods.signal

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

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
            results.add("❌ 蓝牙未开启 → 请先打开蓝牙")
        } else {
            results.add("✅ 蓝牙已开启")
        }

        val leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            results.add("❌ 不支持 BLE 外设广播模式")
        } else {
            results.add("✅ 支持 BLE 外设广播模式")
        }

        // MIUI 特殊提示
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("xiaomi")) {
            results.add("\n⚠️ 检测到小米手机，请注意：")
            results.add("   1. 设置 → 应用 → 模拟信号 → 允许蓝牙广播 (务必开启)")
            results.add("   2. 设置 → 应用 → 模拟信号 → 后台弹出界面 (开启)")
            results.add("   3. 设置 → 应用 → 模拟信号 → 其他权限 → 全部允许")
            results.add("   4. 设置 → 通知和控制中心 → 状态栏 → 蓝牙图标保持开启")
            results.add("   5. 开发者选项 → 蓝牙调试日志 (开启)")
        }

        results.add("\n📡 广播参数:")
        results.add("   Apple 0x004C + 0x07 + AirPods Pro")
        results.add("   模式: ADV_IND 可连接广播")
        results.add("   频率: 最低延迟 | 功率: 最大")
        return results
    }

    fun start(): String {
        lastResult = ""
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: run {
                lastResult = "❌ 设备不支持 BLE 广播"
                return lastResult
            }

        // ★ MIUI 兼容：先用反射方式尝试开启广播模式
        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}

        // 尝试通过反射开启 LE Advertising（MIUI 可能需要）
        try {
            val method: Method = bluetoothAdapter.javaClass.getMethod("setLeAdvertising", Boolean::class.java)
            method.invoke(bluetoothAdapter, true)
            Log.d("BleBroadcaster", "通过反射启用 LE Advertising")
        } catch (_: Exception) {
            // 非 MIUI 或没有此方法，忽略
        }

        // ★ 完整 AirPods Pro 广播数据
        val manufacturerData = byteArrayOf(
            0x07,             // Setup Type
            0x19, 0x01,       // 设备能力 (ANC)
            0x5C, 0x58, 0x4C, // 电量
            0x00, 0x00,
            0x0A, 0x05,
            0x01, 0x02, 0x03, 0x04
        )

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, manufacturerData)
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

        // ★ MIUI 兼容：尝试 startAdvertising 并立即重试一次
        var retry = 0
        while (retry < 2) {
            try {
                advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)
                isAdvertising = true
                lastResult = "📡 信号已发射"
                return lastResult
            } catch (e: SecurityException) {
                lastResult = "❌ 缺少蓝牙广播权限"
                return lastResult
            } catch (e: IllegalStateException) {
                // MIUI 有时第一次会失败，重试一次
                Log.w("BleBroadcaster", "MIUI 重试广播: $retry")
                try { Thread.sleep(200) } catch (_: Exception) {}
                retry++
            } catch (e: Exception) {
                if (retry == 0) {
                    Log.w("BleBroadcaster", "首次失败，重试: ${e.localizedMessage}")
                    try { Thread.sleep(200) } catch (_: Exception) {}
                    retry++
                } else {
                    lastResult = "❌ 广播失败: ${e.localizedMessage}"
                    return lastResult
                }
            }
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
