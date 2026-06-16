package com.airpods.signal

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class BleBroadcaster(private val bluetoothAdapter: BluetoothAdapter) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    var isAdvertising = false
        private set

    var lastResult = ""
        private set

    // 实际检测到的广播帧数据
    var detectedPacket: String = ""
        private set
    var detectedName: String = ""
        private set
    var detectedRssi: Int = 0
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

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
        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            results.add("❌ 不支持 BLE 扫描（不影响广播）")
        } else {
            results.add("✅ 支持 BLE 自检扫描")
        }
        results.add("\n📡 广播格式:")
        results.add("   Apple 0x004C + Setup 0x07")
        results.add("   名称: AirPods Pro | 可连接")
        return results
    }

    fun start(): String {
        lastResult = ""
        detectedPacket = ""
        detectedName = ""
        detectedRssi = 0

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            ?: run {
                lastResult = "❌ 设备不支持 BLE 广播"
                return lastResult
            }

        // 改名
        try {
            bluetoothAdapter.name = "AirPods Pro"
            Log.d("BleBroadcaster", "蓝牙名称已改为 AirPods Pro")
        } catch (_: Exception) {}

        try { Thread.sleep(200) } catch (_: Exception) {}

        // ★ 启动扫描（检测自己发出的广播）
        startSelfScan()

        // ★ 广播数据
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
            .setIncludeTxPowerLevel(true)
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

    /**
     * 启动 BLE 扫描来检测自己的广播
     * 不保证一定能扫到，但如果扫到就能验证帧内容
     */
    private fun startSelfScan() {
        try {
            scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) return

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result ?: return
                    val device = result.device ?: return
                    val name = device.name ?: ""
                    val rssi = result.rssi
                    val scanRecord = result.scanRecord

                    // 过滤：只关注同名设备
                    if (name.contains("AirPods", ignoreCase = true) ||
                        device.address.contains("4C", ignoreCase = true)) {

                        detectedName = name
                        detectedRssi = rssi

                        // 解析广播帧
                        val sb = StringBuilder()
                        scanRecord?.manufacturerSpecificData?.forEach { (companyId, data) ->
                            if (companyId == 0x004C) {
                                sb.append("厂商数据(0x004C): ")
                                sb.append(bytesToHex(data))
                                sb.append("\n")
                            }
                        }

                        // 服务 UUID
                        scanRecord?.serviceUuids?.forEach { uuid ->
                            sb.append("Service UUID: ${uuid.uuid}\n")
                        }

                        // 设备名
                        if (name.isNotEmpty()) {
                            sb.append("设备名: $name\n")
                        }

                        // Tx Power
                        if (scanRecord?.txPowerLevel != Int.MIN_VALUE) {
                            sb.append("TxPower: ${scanRecord?.txPowerLevel} dBm\n")
                        }

                        // 原始广播帧
                        scanRecord?.bytes?.let { bytes ->
                            sb.append("\n完整广播帧 (${bytes.size}字节):\n")
                            sb.append(bytesToHex(bytes))

                            // 解析 AD Structure
                            sb.append("\n\nAD Structure 解析:\n")
                            parseAdStructure(bytes).forEach { line ->
                                sb.append("  $line\n")
                            }
                        }

                        detectedPacket = sb.toString()
                        Log.d("BleBroadcaster", "检测到广播: $name RSSI=$rssi")
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w("BleBroadcaster", "自检扫描失败: $errorCode")
                }
            }

            // 只扫描不设过滤，启动扫描
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(null, scanSettings, scanCallback!!)
            Log.d("BleBroadcaster", "自检扫描已启动")

        } catch (e: Exception) {
            Log.w("BleBroadcaster", "自检扫描不可用: ${e.localizedMessage}")
        }
    }

    fun stop(): String {
        // 停止自检扫描
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Exception) {}
        scanCallback = null
        scanner = null

        // 停止广播
        try {
            advertiser?.stopAdvertising(callback)
        } catch (_: Exception) {}
        advertiser = null
        isAdvertising = false

        lastResult = "⏹ 已停止"
        return lastResult
    }

    fun getDetectedInfo(): String {
        if (detectedPacket.isEmpty()) {
            return "尚未检测到广播包\n\n提示：\n- 检查蓝牙是否开启\n- 检查广播权限是否授予\n- 部分手机无法自检（需另一台设备扫描）"
        }
        return "📡 检测到广播 (RSSI: ${detectedRssi}dBm)\n设备名: $detectedName\n\n$detectedPacket"
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }

    /**
     * 解析 AD Structure 并显示
     */
    private fun parseAdStructure(data: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < data.size) {
            val len = data[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > data.size) break

            val type = data[i + 1].toInt() and 0xFF
            val value = data.copyOfRange(i + 2, i + 1 + len)

            val typeName = when (type) {
                0x01 -> "Flags"
                0x02 -> "Incomplete Service UUIDs (16-bit)"
                0x03 -> "Complete Service UUIDs (16-bit)"
                0x06 -> "Incomplete Service UUIDs (128-bit)"
                0x07 -> "Complete Service UUIDs (128-bit)"
                0x08 -> "Shortened Local Name"
                0x09 -> "Complete Local Name"
                0x0A -> "Tx Power Level"
                0x16 -> "Service Data"
                0xFF -> "Manufacturer Specific Data"
                else -> "Unknown (0x${type.toString(16)})"
            }

            result.add("[$len] 0x${type.toString(16)} $typeName: ${bytesToHex(value)}")
            i += 1 + len
        }
        return result
    }
}
