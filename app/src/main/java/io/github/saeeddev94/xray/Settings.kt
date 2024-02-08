package io.github.saeeddev94.xray

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object Settings {
    var primaryDns: String = "1.1.1.1"
    var secondaryDns: String = "1.0.0.1"
    var socksAddress: String = "127.0.0.1"
    var socksPort: String = "10808"
    var socksUsername: String = ""
    var socksPassword: String = ""
    var excludedApps: String = ""
    var bypassLan: Boolean = true
    var socksUdp: Boolean = true
    var enableIPv6: Boolean = true
    var tunName: String = "tun0"
    var tunMtu: Int = 1500
    var ipv4Address: String = "26.26.26.1"
    var ipv4Prefix: Int = 30
    var ipv6Address: String = "da26:2626::1"
    var ipv6Prefix: Int = 126
    var selectedProfile: Long = 0L
    var pingTimeout: Int = 5
    var pingAddress: String = "https://developers.google.com"

    fun testConfig(context: Context): File = File(context.filesDir, "test.json")
    fun xrayConfig(context: Context): File = File(context.filesDir, "config.json")
    fun tun2socksConfig(context: Context): File = File(context.filesDir, "tun2socks.yml")
    fun sharedPref(context: Context): SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
