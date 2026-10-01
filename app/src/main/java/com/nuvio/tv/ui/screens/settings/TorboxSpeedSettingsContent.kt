package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.core.network.PlaybackActiveGuard
import com.nuvio.tv.core.network.TorboxSpeedTestCoordinator
import com.nuvio.tv.core.network.TorboxSpeedTestHarness
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun TorboxSpeedSettingsRow(modifier: Modifier = Modifier) {
    var refreshTick by remember { mutableStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    val sample = remember(refreshTick) { TorboxSpeedTestCoordinator.lastSample() }
    val pinnedMbps = remember(refreshTick) { TorboxSpeedTestHarness.readPinnedMbps() }
    val subtitle = when {
        pinnedMbps != null -> stringResource(R.string.settings_playback_torbox_speed_pinned, pinnedMbps)
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
    val measureClick = {
        if (!PlaybackActiveGuard.isPlaybackActive) {
            TorboxSpeedTestCoordinator.runManualMeasure {
                refreshTick++
            }
        }
    }
    if (BuildConfig.DEBUG) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsActionRow(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.settings_playback_torbox_speed_title),
                subtitle = subtitle,
                value = stringResource(R.string.settings_playback_torbox_measure_now),
                onClick = measureClick,
            )
            SettingsActionRow(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.settings_playback_torbox_pin_title),
                subtitle = stringResource(R.string.settings_playback_torbox_pin_subtitle),
                value = stringResource(R.string.settings_playback_torbox_pin_action),
                onClick = { showPinDialog = true },
            )
        }
    } else {
        SettingsActionRow(
            modifier = modifier,
            title = stringResource(R.string.settings_playback_torbox_speed_title),
            subtitle = subtitle,
            value = stringResource(R.string.settings_playback_torbox_measure_now),
            onClick = measureClick,
        )
    }
    if (showPinDialog) {
        PinMbpsDialog(
            initial = pinnedMbps?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
            onDismiss = { showPinDialog = false },
            onSaved = {
                showPinDialog = false
                refreshTick++
            },
        )
    }
}

@Composable
private fun PinMbpsDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_playback_torbox_pin_dialog_title),
        subtitle = stringResource(R.string.settings_playback_torbox_pin_dialog_subtitle),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocus)
                    .padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = NuvioTheme.colors.TextPrimary),
                singleLine = true,
            )
            if (invalid) {
                Text(
                    text = stringResource(R.string.settings_playback_torbox_pin_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                )
            }
            SettingsActionRow(
                title = stringResource(R.string.settings_playback_torbox_pin_save),
                subtitle = null,
                onClick = {
                    val mbps = text.trim().replace(',', '.').toDoubleOrNull()
                    if (mbps == null || mbps <= 0.0) {
                        invalid = true
                        return@SettingsActionRow
                    }
                    TorboxSpeedTestHarness.pinMbps(mbps)
                    onSaved()
                },
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_playback_torbox_pin_clear),
                subtitle = null,
                onClick = {
                    TorboxSpeedTestHarness.pinMbps(null)
                    onSaved()
                },
            )
        }
    }
}
