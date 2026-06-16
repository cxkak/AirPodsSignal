package com.airpods.signal

import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {

    private lateinit var broadcaster: BleBroadcaster
    private lateinit var btnToggle: MaterialButton
    private lateinit var txtStatus: MaterialTextView
    private lateinit var txtPacket: MaterialTextView

    // Android 12+ 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            btnToggle.isEnabled = true
        } else {
            Toast.makeText(this, "需要蓝牙权限才能广播", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btn_toggle)
        txtStatus = findViewById(R.id.txt_status)
        txtPacket = findViewById(R.id.txt_packet)

        // 初始化蓝牙
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            txtStatus.text = "❌ 设备不支持蓝牙"
            btnToggle.isEnabled = false
            return
        }
        broadcaster = BleBroadcaster(adapter)

        // 显示广播包结构
        showPacketFormat()

        // 按钮点击
        btnToggle.setOnClickListener {
            if (!adapter.isEnabled) {
                Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleBroadcast()
        }

        // 请求权限
        requestPermissions()
    }

    private fun toggleBroadcast() {
        if (broadcaster.isAdvertising) {
            val msg = broadcaster.stop()
            txtStatus.text = msg
            btnToggle.text = "开始广播"
            btnToggle.icon = ContextCompat.getDrawable(this, R.drawable.ic_start)
        } else {
            val msg = broadcaster.start()
            txtStatus.text = msg
            if (broadcaster.isAdvertising) {
                btnToggle.text = "停止广播"
                btnToggle.icon = ContextCompat.getDrawable(this, R.drawable.ic_stop)
            }
        }
    }

    private fun showPacketFormat() {
        txtPacket.text = """
            BLE 广播包:
            02 01 06           ← Flags
            FF 4C 00           ← Apple Inc. (0x004C)
               07              ← ★ Setup Type (弹窗关键)
               19 01           ← Device Capabilities
               5C 58 4C        ← Battery L/R/Case
            0B 09              ← "AirPods Pro"
        """.trimIndent()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: 需要 BLUETOOTH_ADVERTISE
            val permissions = arrayOf(
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
            val needsRequest = permissions.any {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (needsRequest) {
                permissionLauncher.launch(permissions)
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-11: 需要位置权限
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
                )
                return
            }
        }
        btnToggle.isEnabled = true
    }
}
