package com.airpods.signal

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Build
import android.util.Log

class BleBroadcaster(private val bluetoothAdapter: BluetoothAdapter) {

    private var advertiser: BluetoothLeAdvertiser? = null
    var isAdvertising = false
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

    fun start(): String {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: return "❌ 设备不支持 BLE 广播"

        val originalName = bluetoothAdapter.name

        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}

        // ★ 核心：AirPods Pro 配对广播包
        // FF 4C 00 07 19 01 5C 58 4C
        //                    ^^^ 苹果公司 ID 0x004C
        //                       ^^ 设置类型 0x07 (触发弹窗)
        //                           ^^ 设备能力(ANC)
        //                              ^^ 电量和填充
        val manufacturerData = byteArrayOf(
            0x07,             // Setup Type — 配对弹窗触发器
            0x19, 0x01,       // 设备能力 (0x0119 = 支持ANC/自适应模式)
            0x5C, 0x58, 0x4C  // 电池: 左耳92% 右耳88% 充电盒76%
        )

        // 广播数据包 — 不包含设备名
        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, manufacturerData)
            .setIncludeDeviceName(false)  // ★ 重要：设备名只在 SCAN_RSP
            .build()

        // 扫描回应包 — 包含设备名和额外信息
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        // ★ 不可连接、高频率广播
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  // ~100ms间隔
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)     // +4dBm
            .setConnectable(false)     // ★ 不可连接广播，跟 AirPods 一致
            .build()

        advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)

        return "📡 信号已发射 (不可连接广播)"
    }

    fun stop(): String {
        try {
            advertiser?.stopAdvertising(callback)
        } catch (_: Exception) {}
        isAdvertising = false
        try {
            bluetoothAdapter.name = "AirPods Pro"
        } catch (_: Exception) {}
        return "⏹ 已停止"
    }
}
