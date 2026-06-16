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
    private lateinit var btnCheck: MaterialButton
    private lateinit var txtStatus: MaterialTextView
    private lateinit var txtPacket: MaterialTextView
    private lateinit var txtCheck: MaterialTextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            btnToggle.isEnabled = true
            btnCheck.isEnabled = true
        } else {
            Toast.makeText(this, "需要蓝牙权限才能广播", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btn_toggle)
        btnCheck = findViewById(R.id.btn_check)
        txtStatus = findViewById(R.id.txt_status)
        txtPacket = findViewById(R.id.txt_packet)
        txtCheck = findViewById(R.id.txt_check)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            txtStatus.text = "❌ 设备不支持蓝牙"
            btnToggle.isEnabled = false
            btnCheck.isEnabled = false
            return
        }
        broadcaster = BleBroadcaster(adapter)

        showPacketFormat()

        btnToggle.setOnClickListener {
            if (!adapter.isEnabled) {
                Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleBroadcast()
        }

        btnCheck.setOnClickListener {
            val results = broadcaster.selfCheck()
            txtCheck.text = results.joinToString("\n")
            Toast.makeText(this, "自检完成", Toast.LENGTH_SHORT).show()
        }

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
            } else {
                // 启动失败，显示详细错误
                txtStatus.text = broadcaster.lastResult
            }
        }
    }

    private fun showPacketFormat() {
        txtPacket.text = """
            BLE 广播帧 (双重广播):
            FF 4C 00 07 19 01 5C 58 4C
            └── 苹果公司ID 0x004C
                └── Setup Type 0x07 (弹窗触发)
                    └── 设备能力 0x0119
                        └── 电量 L=92% R=88% C=76%
            频率: ~100ms | 功率: 最大
        """.trimIndent()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
                )
                return
            }
        }
        btnToggle.isEnabled = true
        btnCheck.isEnabled = true
    }
}
