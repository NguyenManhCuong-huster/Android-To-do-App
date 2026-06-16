package com.project.hustassistant.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.project.hustassistant.ui.common.applyWindowInsets
import androidx.lifecycle.lifecycleScope
import com.project.hustassistant.TodoApplication
import com.project.hustassistant.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, binding.toolbar)

        val config =(application as TodoApplication).container.serverConfig

        binding.etServerHost.setText(config.getHost())
        binding.etServerPort.setText(config.getPort().toString())
        binding.tvCurrentUrl.text = config.getBaseUrl()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val host    = binding.etServerHost.text.toString().trim()
            val portStr = binding.etServerPort.text.toString().trim()

            binding.tilServerHost.error = null
            binding.tilServerPort.error = null

            if (host.isBlank()) {
                binding.tilServerHost.error = "Vui lòng nhập host"
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull()
            if (port == null || port !in 1..65535) {
                binding.tilServerPort.error = "Port hợp lệ: 1–65535"
                return@setOnClickListener
            }

            config.setFromHostPort(host, port)
            binding.tvCurrentUrl.text = config.getBaseUrl()
            Toast.makeText(this, "Đã lưu — ${config.getBaseUrl()}", Toast.LENGTH_SHORT).show()
        }

        binding.btnTest.setOnClickListener {
            val host    = binding.etServerHost.text.toString().trim()
            val portStr = binding.etServerPort.text.toString().trim()
            val port    = portStr.toIntOrNull() ?: 3000

            binding.btnTest.isEnabled = false
            binding.tvTestResult.text = "Đang kiểm tra kết nối…"
            binding.tvTestResult.setTextColor(getColor(android.R.color.darker_gray))

            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { testConnection(host, port) }
                binding.btnTest.isEnabled = true
                if (ok) {
                    binding.tvTestResult.text = "✅ Server đang hoạt động tại $host:$port"
                    binding.tvTestResult.setTextColor(getColor(android.R.color.holo_green_dark))
                } else {
                    binding.tvTestResult.text = "❌ Không kết nối được $host:$port\n" +
                            "• Kiểm tra cùng WiFi\n• Firewall cho phép port $port\n• Server đang chạy"
                    binding.tvTestResult.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }

        binding.btnReset.setOnClickListener {
            config.reset()
            binding.etServerHost.setText(config.getHost())
            binding.etServerPort.setText(config.getPort().toString())
            binding.tvCurrentUrl.text = config.getBaseUrl()
            Toast.makeText(this, "Đã đặt lại mặc định", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection(host: String, port: Int): Boolean {
        return try {
            val conn = URL("http://$host:$port/api/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout    = 3000
            conn.requestMethod  = "GET"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) { false }
    }
}
