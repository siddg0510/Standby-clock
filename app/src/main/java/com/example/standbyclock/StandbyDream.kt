package com.example.standbyclock

import android.service.dreams.DreamService

/** Screensaver entry point. Samsung's "Screen saver" setting can run it while the phone charges. */
class StandbyDream : DreamService() {

    private var clock: ClockUi? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setInteractive(false)
        setFullscreen(true)
        val ui = ClockUi(this, Settings(this).screensaverFace)
        ui.onMinute = { night -> setScreenBright(!night) }
        clock = ui
        setContentView(ui.root)
        ui.start()
    }

    override fun onDetachedFromWindow() {
        clock?.stop()
        clock = null
        super.onDetachedFromWindow()
    }
}
