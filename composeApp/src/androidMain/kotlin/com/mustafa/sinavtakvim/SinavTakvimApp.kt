package com.mustafa.sinavtakvim

import android.app.Application
import com.mustafa.sinavtakvim.shared.di.initKoin
import com.mustafa.sinavtakvim.shared.utils.CalendarContext
import org.koin.android.ext.koin.androidContext

class SinavTakvimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CalendarContext.attach(this)
        initKoin {
            androidContext(this@SinavTakvimApp)
        }
    }
}
