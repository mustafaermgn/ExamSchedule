@file:JvmName("MainKt")
package com.mustafa.sinavtakvim

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mustafa.sinavtakvim.App
import com.mustafa.sinavtakvim.shared.di.initKoin

fun main() {
    try {
        initKoin()
        
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "SinavTakvimSistemi",
            ) {
                App()
            }
        }
    } catch (t: Throwable) {
        System.err.println("FATAL ERROR DURING STARTUP:")
        t.printStackTrace()
        System.exit(1)
    }
}
