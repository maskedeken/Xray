package io.github.saeeddev94.xray.activity

import XrayCore.XrayCore
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import io.github.saeeddev94.xray.BuildConfig
import io.github.saeeddev94.xray.R
import io.github.saeeddev94.xray.Settings
import io.github.saeeddev94.xray.adapter.ProfileAdapter
import io.github.saeeddev94.xray.database.XrayDatabase
import io.github.saeeddev94.xray.databinding.ActivityMainBinding
import io.github.saeeddev94.xray.dto.ProfileList
import io.github.saeeddev94.xray.helper.HttpHelper
import io.github.saeeddev94.xray.helper.ProfileTouchHelper
import io.github.saeeddev94.xray.service.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vpnService: TProxyService
    private var vpnLauncher = registerForActivityResult(StartActivityForResult()) {
        if (it.resultCode != RESULT_OK) return@registerForActivityResult
        toggleVpnService()
    }

    private lateinit var profilesList: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var profiles: ArrayList<ProfileList>
    private var profileLauncher = registerForActivityResult(StartActivityForResult()) {
        if (it.resultCode != RESULT_OK || it.data == null) return@registerForActivityResult
        val id = it.data!!.getLongExtra("id", 0L)
        val index = it.data!!.getIntExtra("index", -1)
        onProfileActivityResult(id, index)
    }

    private var vpnServiceBound: Boolean = false
    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TProxyService.ServiceBinder
            vpnService = binder.getService()
            vpnServiceBound = true
            setVpnServiceStatus()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            vpnServiceBound = false
        }
    }

    private val toggleVpnAction: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TProxyService.START_VPN_SERVICE_ACTION_NAME -> vpnStartStatus()
                TProxyService.STOP_VPN_SERVICE_ACTION_NAME -> vpnStopStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        Settings.sync(applicationContext)
        binding.toggleButton.setOnClickListener { onToggleButtonClick() }
        binding.pingBox.setOnClickListener { ping() }
        binding.navView.menu.findItem(R.id.appVersion).title = BuildConfig.VERSION_NAME
        binding.navView.menu.findItem(R.id.xrayVersion).title = XrayCore.version()
        binding.navView.setNavigationItemSelectedListener(this)
        ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.drawerOpen, R.string.drawerClose).also {
            binding.drawerLayout.addDrawerListener(it)
            it.syncState()
        }
        getProfiles()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        Intent(this, TProxyService::class.java).also {
            bindService(it, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        IntentFilter().also {
            it.addAction(TProxyService.START_VPN_SERVICE_ACTION_NAME)
            it.addAction(TProxyService.STOP_VPN_SERVICE_ACTION_NAME)
            registerReceiver(toggleVpnAction, it, RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
        unregisterReceiver(toggleVpnAction)
        vpnServiceBound = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1) onToggleButtonClick()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.newProfile -> {
                Intent(applicationContext, ProfileActivity::class.java).also {
                    it.putExtra("id", 0L)
                    it.putExtra("index", -1)
                    profileLauncher.launch(it)
                }
            }
        }
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.assets -> Intent(applicationContext, AssetsActivity::class.java)
            R.id.logs -> Intent(applicationContext, LogsActivity::class.java)
            R.id.excludedApps -> Intent(applicationContext, ExcludeActivity::class.java)
            R.id.settings -> Intent(applicationContext, SettingsActivity::class.java)
            else -> null
        }.also {
            if (it != null) startActivity(it)
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun setVpnServiceStatus() {
        if (!vpnServiceBound) return
        if (vpnService.getIsRunning()) {
            vpnStartStatus()
        } else {
            vpnStopStatus()
        }
    }

    private fun vpnStartStatus() {
        binding.toggleButton.visibility = View.VISIBLE
        binding.toggleButton.setImageResource(R.drawable.ic_stop)
        binding.pingResult.text = getString(R.string.pingConnected)
    }

    private fun vpnStopStatus() {
        binding.toggleButton.visibility = View.VISIBLE
        binding.toggleButton.setImageResource(R.drawable.ic_play_arrow)
        binding.pingResult.text = getString(R.string.pingNotConnected)
    }

    private fun onToggleButtonClick() {
        if (!hasPostNotification()) return
        VpnService.prepare(this).also {
            if (it == null) {
                toggleVpnService()
                return
            }
            vpnLauncher.launch(it)
        }
    }

    private fun toggleVpnService() {
        binding.toggleButton.visibility = View.GONE
        if (vpnService.getIsRunning()) {
            Intent(TProxyService.STOP_VPN_SERVICE_ACTION_NAME).also {
                it.`package` = BuildConfig.APPLICATION_ID
                sendBroadcast(it)
            }
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val intent = Intent(applicationContext, TProxyService::class.java).apply {
                if (Settings.selectedProfile != 0L) {
                    XrayDatabase.profileDao.find(Settings.selectedProfile).also {
                        putExtra("name", it.name)
                        putExtra("config", it.config)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                startForegroundService(intent)
            }
        }
    }

    private fun profileSelect(index: Int, profile: ProfileList) {
        if (vpnService.getIsRunning()) return
        val selectedProfile = Settings.selectedProfile
        lifecycleScope.launch(Dispatchers.IO) {
            val ref = if (selectedProfile > 0L) XrayDatabase.profileDao.find(selectedProfile) else null
            withContext(Dispatchers.Main) {
                Settings.selectedProfile = if (selectedProfile == profile.id) 0L else profile.id
                Settings.save(applicationContext)
                profileAdapter.notifyItemChanged(index)
                if (ref != null && ref.index != index) profileAdapter.notifyItemChanged(ref.index)
            }
        }
    }

    private fun profileEdit(index: Int, profile: ProfileList) {
        if (vpnService.getIsRunning() && Settings.selectedProfile == profile.id) return
        Intent(applicationContext, ProfileActivity::class.java).also {
            it.putExtra("id", profile.id)
            it.putExtra("index", index)
            profileLauncher.launch(it)
        }
    }

    private fun profileDelete(index: Int, profile: ProfileList) {
        if (vpnService.getIsRunning() && Settings.selectedProfile == profile.id) return
        val selectedProfile = Settings.selectedProfile
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Profile#${profile.index + 1} ?")
            .setMessage("\"${profile.name}\" will delete forever !!")
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { dialog, _ ->
                dialog?.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ref = XrayDatabase.profileDao.find(profile.id)
                    val id = ref.id
                    XrayDatabase.profileDao.delete(ref)
                    XrayDatabase.profileDao.fixDeleteIndex(index)
                    withContext(Dispatchers.Main) {
                        if (selectedProfile == id) {
                            Settings.selectedProfile = 0L
                            Settings.save(applicationContext)
                        }
                        profiles.removeAt(index)
                        profileAdapter.notifyItemRemoved(index)
                        profileAdapter.notifyItemRangeChanged(index, profiles.size - index)
                    }
                }
            }.show()
    }

    private fun onProfileActivityResult(id: Long, index: Int) {
        if (index == -1) {
            lifecycleScope.launch(Dispatchers.IO) {
                val newProfile = XrayDatabase.profileDao.find(id)
                withContext(Dispatchers.Main) {
                    profiles.add(0, ProfileList.fromProfile(newProfile))
                    profileAdapter.notifyItemRangeChanged(0, profiles.size)
                }
            }
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = XrayDatabase.profileDao.find(id)
            withContext(Dispatchers.Main) {
                profiles[index] = ProfileList.fromProfile(profile)
                profileAdapter.notifyItemChanged(index)
            }
        }
    }

    private fun getProfiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = XrayDatabase.profileDao.all()
            withContext(Dispatchers.Main) {
                profiles = ArrayList(list)
                profilesList = binding.profilesList
                profileAdapter = ProfileAdapter(applicationContext, lifecycleScope, profiles, object : ProfileAdapter.ProfileClickListener {
                    override fun profileSelect(index: Int, profile: ProfileList) = this@MainActivity.profileSelect(index, profile)
                    override fun profileEdit(index: Int, profile: ProfileList) = this@MainActivity.profileEdit(index, profile)
                    override fun profileDelete(index: Int, profile: ProfileList) = this@MainActivity.profileDelete(index, profile)
                })
                profilesList.adapter = profileAdapter
                profilesList.layoutManager = LinearLayoutManager(applicationContext)
                profilesList.addItemDecoration(object: RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
                        super.getItemOffsets(outRect, view, parent, state)
                        if (parent.getChildAdapterPosition(view) == (parent.adapter?.itemCount ?: 0) - 1 ) {
                            outRect.bottom = resources.getDimensionPixelOffset(R.dimen.bottom_spacing)
                        }
                    }
                })
                ItemTouchHelper(ProfileTouchHelper(profileAdapter)).also { it.attachToRecyclerView(profilesList) }
            }
        }
    }

    private fun ping() {
        if (!vpnService.getIsRunning()) return
        binding.pingResult.text = getString(R.string.pingTesting)
        lifecycleScope.launch(Dispatchers.IO) {
            val delay = HttpHelper().measureDelay()
            withContext(Dispatchers.Main) {
                binding.pingResult.text = delay
            }
        }
    }

    private fun hasPostNotification(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            return false
        }
        return true
    }

}
