package com.onetap.alwayson

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AlwaysOnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val running = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(FloatBallView.KEY_SERVICE_RUNNING, false)
        if (running) {
            startService(
                Intent(this, FloatBallService::class.java)
                    .setAction(FloatBallService.ACTION_HIDE)
            )
        } else {
            val duration = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
                .getInt(FloatBallView.KEY_DURATION_MINUTES, FloatBallService.DEFAULT_DURATION_MINUTES)
            startService(
                Intent(this, FloatBallService::class.java)
                    .setAction(FloatBallService.ACTION_SHOW)
                    .putExtra(FloatBallService.EXTRA_DURATION_MINUTES, duration)
            )
        }
        updateTile()
    }

    private fun updateTile() {
        val running = getSharedPreferences(FloatBallView.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(FloatBallView.KEY_SERVICE_RUNNING, false)
        qsTile?.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}