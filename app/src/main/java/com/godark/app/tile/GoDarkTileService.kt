package com.godark.app.tile

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.godark.app.MainActivity
import com.godark.app.state.GoDarkState
import com.godark.app.state.Mode
import com.godark.app.vpn.GoDarkVpnService

/**
 * Quick Settings tile: instant DARK (full blackout) from the shade.
 * The tile is the panic button; mode nuance lives in the app.
 */
class GoDarkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (GoDarkState.mode.value == Mode.DARK) {
            GoDarkVpnService.stop(this)
        } else {
            val consent = VpnService.prepare(this)
            if (consent == null) {
                GoDarkVpnService.start(this, Mode.DARK)
            } else {
                val i = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("request_vpn", true)
                startActivityAndCollapse(i)
            }
        }
        refresh()
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (GoDarkState.mode.value == Mode.DARK) Tile.STATE_ACTIVE
                    else Tile.STATE_INACTIVE
            label = "GoDark"
            updateTile()
        }
    }
}
