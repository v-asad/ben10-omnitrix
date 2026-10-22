package dev.omnitrix.app

import android.app.Application

/** Loads the roster once per process, before any activity, service or receiver runs. */
class OmnitrixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Aliens.load(this)
    }
}
