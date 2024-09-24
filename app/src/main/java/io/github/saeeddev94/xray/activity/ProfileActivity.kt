package io.github.saeeddev94.xray.activity

import XrayCore.XrayCore
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blacksquircle.ui.editorkit.plugin.autoindent.autoIndentation
import com.blacksquircle.ui.editorkit.plugin.base.PluginSupplier
import com.blacksquircle.ui.editorkit.plugin.delimiters.highlightDelimiters
import com.blacksquircle.ui.editorkit.plugin.linenumbers.lineNumbers
import com.blacksquircle.ui.language.json.JsonLanguage
import io.github.saeeddev94.xray.R
import io.github.saeeddev94.xray.Settings
import io.github.saeeddev94.xray.database.Profile
import io.github.saeeddev94.xray.database.XrayDatabase
import io.github.saeeddev94.xray.databinding.ActivityProfileBinding
import io.github.saeeddev94.xray.helper.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var profile: Profile
    private var id: Long = 0L
    private var index: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getLongExtra("id", 0L)
        index = intent.getIntExtra("index", -1)
        title = if (isNew()) getString(R.string.newProfile) else getString(R.string.editProfile)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val jsonUri = intent.data
        if (Intent.ACTION_VIEW == intent.action && jsonUri != null) {
            val profile = Profile()
            profile.config = readJsonFile(jsonUri)
            resolved(profile)
        } else if (isNew()) {
            val profile = Profile()
            profile.name = intent.getStringExtra("name") ?: ""
            profile.config = intent.getStringExtra("config") ?: ""
            resolved(profile)
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val profile = XrayDatabase.profileDao.find(id)
                withContext(Dispatchers.Main) {
                    resolved(profile)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_profile, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.saveProfile -> save()
            else -> finish()
        }
        return true
    }

    private fun isNew() = id == 0L

    private fun readJsonFile(uri: Uri): String {
        val content = StringBuilder()
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).forEachLine { content.append("$it\n") }
            }
        } catch (error: Exception) {
            error.printStackTrace()
        }
        return content.toString()
    }

    private fun resolved(value: Profile) {
        profile = value
        binding.profileName.setText(profile.name)

        val editor = binding.profileConfig
        val pluginSupplier = PluginSupplier.create {
            lineNumbers {
                lineNumbers = true
                highlightCurrentLine = true
            }
            highlightDelimiters()
            autoIndentation {
                autoIndentLines = true
                autoCloseBrackets = true
                autoCloseQuotes = true
            }
        }
        editor.language = JsonLanguage()
        editor.setTextContent(profile.config)
        editor.plugins(pluginSupplier)
    }

    private fun save() {
        profile.name = binding.profileName.text.toString()
        profile.config = binding.profileConfig.text.toString()
        lifecycleScope.launch(Dispatchers.IO) {
            FileHelper().createOrUpdate(Settings.testConfig(applicationContext), profile.config)
            val error = XrayCore.test(applicationContext.filesDir.absolutePath, Settings.testConfig(applicationContext).absolutePath)
            if (error.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            if (profile.id == 0L) {
                profile.id = XrayDatabase.profileDao.insert(profile)
                XrayDatabase.profileDao.fixInsertIndex()
            } else {
                XrayDatabase.profileDao.update(profile)
            }
            withContext(Dispatchers.Main) {
                Intent().also {
                    it.putExtra("id", profile.id)
                    it.putExtra("index", index)
                    setResult(RESULT_OK, it)
                    finish()
                }
            }
        }
    }

}
