package com.project3.todoapp.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val config = (application as TodoApplication).container.serverConfig

        // Populate fields from current config
        binding.etServerHost.setText(config.getHost())
        binding.etServerPort.setText(config.getPort().toString())
        binding.tvCurrentUrl.text = "Current: ${config.getBaseUrl()}"

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val host = binding.etServerHost.text.toString().trim()
            val portStr = binding.etServerPort.text.toString().trim()

            if (host.isBlank()) {
                binding.etServerHost.error = "Host is required"
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull()
            if (port == null || port !in 1..65535) {
                binding.etServerPort.error = "Valid port (1–65535)"
                return@setOnClickListener
            }

            config.setFromHostPort(host, port)
            binding.tvCurrentUrl.text = "Current: ${config.getBaseUrl()}"
            Toast.makeText(this, "Saved — ${config.getBaseUrl()}", Toast.LENGTH_SHORT).show()
        }

        binding.btnTest.setOnClickListener {
            val host = binding.etServerHost.text.toString().trim()
            val portStr = binding.etServerPort.text.toString().trim()
            val port = portStr.toIntOrNull() ?: 3000

            binding.btnTest.isEnabled = false
            binding.tvTestResult.text = "Testing…"

            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { testConnection(host, port) }
                binding.btnTest.isEnabled = true
                if (ok) {
                    binding.tvTestResult.text = "✅ Connected to $host:$port"
                    binding.tvTestResult.setTextColor(getColor(android.R.color.holo_green_dark))
                } else {
                    binding.tvTestResult.text = "❌ Cannot reach $host:$port\n" +
                            "• Check same WiFi\n• Firewall allows port $port\n• Server is running"
                    binding.tvTestResult.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }

        binding.btnReset.setOnClickListener {
            (application as TodoApplication).container.serverConfig.reset()
            val config2 = (application as TodoApplication).container.serverConfig
            binding.etServerHost.setText(config2.getHost())
            binding.etServerPort.setText(config2.getPort().toString())
            binding.tvCurrentUrl.text = "Current: ${config2.getBaseUrl()}"
            Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                true
            }
        } catch (_: Exception) { false }
    }
}
