package com.mustafa.sinavtakvim.shared.utils

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

object CalendarContext {
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context? = appContext
}

actual fun addToCalendar(title: String, description: String, startTime: Long) {
    val context = CalendarContext.context() ?: return
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTime + 90 * 60 * 1000L)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
