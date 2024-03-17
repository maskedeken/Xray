package io.github.saeeddev94.xray.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.saeeddev94.xray.BuildConfig
import io.github.saeeddev94.xray.R
import io.github.saeeddev94.xray.databinding.ActivityLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.logs)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logs, menu)
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refreshLogs -> {
                logcat()
            }
            R.id.deleteLogs -> {
                flush()
            }
            R.id.copyLogs -> copyToClipboard(binding.logsTextView.text.toString())
            else -> finish()
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        logcat()
    }

    private fun logcat() {
        binding.pbWaiting.visibility = View.VISIBLE
        val refreshMenu = menu?.findItem(R.id.refreshLogs)?.setVisible(false)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val loggingProcess = ProcessBuilder("logcat", "-d", "-v", "time", "-s", "GoLog,${BuildConfig.APPLICATION_ID}").start()
                val allText = loggingProcess.inputStream.bufferedReader().use { it.readText() }
                withContext(Dispatchers.Main) {
                    binding.logsTextView.text = allText
                    binding.pbWaiting.visibility = View.GONE
                    refreshMenu?.setVisible(true)
                    binding.logsScrollView.post {
                        binding.logsScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }

    private fun flush() {
        lifecycleScope.launch(Dispatchers.IO) {
            val command = listOf("logcat", "-c")
            val process = ProcessBuilder(command).start()
            process.waitFor()
            withContext(Dispatchers.Main) {
                binding.logsTextView.text = ""
            }
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        try {
            val clipData = ClipData.newPlainText(null, text)
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(applicationContext, "Logs copied", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

}
