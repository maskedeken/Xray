package io.github.saeeddev94.xray

import android.app.Application
import android.content.Context


class Xray: Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        application = this
    }

    companion object {
        lateinit var application: Xray
    }
}