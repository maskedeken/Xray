package io.github.saeeddev94.xray.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import io.github.saeeddev94.xray.BuildConfig
import io.github.saeeddev94.xray.R

class VpnTileService : TileService(), ServiceConnection, TProxyService.StateCallback {

    private var binder: IBinder? = null
    private var service: TProxyService? = null

    override fun onStartListening() {
        super.onStartListening()
        Intent(this, TProxyService::class.java).also {
            bindService(it, this, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStopListening() {
        unbindService(this)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (qsTile?.state) {
            Tile.STATE_INACTIVE -> {
                val isPrepare = VpnService.prepare(applicationContext) == null
                if (!isPrepare) {
                    Log.e("VpnTileService", "Can't start: VpnService#prepare: needs user permission")
                    return
                }
                Intent(applicationContext, TProxyService::class.java).also {
                    startForegroundService(it)
                }
            }
            Tile.STATE_ACTIVE -> {
                Intent(TProxyService.STOP_VPN_SERVICE_ACTION_NAME).also {
                    it.`package` = BuildConfig.APPLICATION_ID
                    sendBroadcast(it)
                }
            }
        }
    }

    private fun updateTile(newState: Int, newLabel: String) {
        qsTile?.apply {
            state = newState
            label = newLabel
            icon = Icon.createWithResource(applicationContext, R.drawable.vpn_key)
            updateTile()
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        this.binder = binder
        service = (binder as? TProxyService.ServiceBinder)?.getService()
        onStateChanged()
        service?.setStateCallback(this)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service?.setStateCallback(null)
        binder = null
        service = null
    }

    override fun onStateChanged() {
        if (service?.getIsRunning() == true) {
            val label = service?.getProfile()?.name ?: getString(R.string.appName)
            updateTile(Tile.STATE_ACTIVE, label)
        } else {
            updateTile(Tile.STATE_INACTIVE, getString(R.string.appName))
        }
    }
}
