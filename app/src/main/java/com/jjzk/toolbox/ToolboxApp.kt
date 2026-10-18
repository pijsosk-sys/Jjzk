package com.jjzk.toolbox

import android.app.Application

class ToolboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    companion object {
        lateinit var instance: ToolboxApp
            private set
    }
}
