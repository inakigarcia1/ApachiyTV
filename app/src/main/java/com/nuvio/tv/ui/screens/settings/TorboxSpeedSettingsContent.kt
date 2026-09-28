package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.network.PlaybackActiveGuard
import com.nuvio.tv.core.network.TorboxSpeedTestCoordinator
import java.text.DateFormat
import java.util.Date

@Composable
fun TorboxSpeedSettingsRow(modifier: Modifier = Modifier) {
    var refreshTick by remember { mutableStateOf(0) }
    val sample = remember(refreshTick) { TorboxSpeedTestCoordinator.lastSample() }
    val subtitle = when {
        sample == null -> stringResource(R.string.settings_playback_torbox_speed_none)
        else -> {
            val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(sample.measuredAtEpochMs))
            stringResource(
                R.string.settings_playback_torbox_speed_last,
                sample.speedMbps,
                whenText,
            )
        }
    }
    SettingsActionRow(
        modifier = modifier,
        title = stringResource(R.string.settings_playback_torbox_speed_title),
        subtitle = subtitle,
        value = stringResource(R.string.settings_playback_torbox_measure_now),
        onClick = {
            if (PlaybackActiveGuard.isPlaybackActive) return@SettingsActionRow
            TorboxSpeedTestCoordinator.runManualMeasure {
                refreshTick++
            }
        },
    )
}
