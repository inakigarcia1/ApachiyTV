package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncPreferences
import com.nuvio.tv.ui.screens.player.autosync.isChosenSubtitleLanguage

internal fun LazyListScope.autoSyncSettingsItems(
    preferredLanguage: String?,
    onRequestLanguage: () -> Unit,
    enabled: Boolean,
    onItemFocused: () -> Unit = {},
) {
    if (!BuildConfig.IS_DEBUG_BUILD) return
    item(key = "subtitle_auto_sync") {
        AutoSyncToggle(
            preferredLanguage = preferredLanguage,
            onRequestLanguage = onRequestLanguage,
            enabled = enabled,
            row = { title, subtitle, checked, onCheckedChange, rowEnabled ->
                ToggleSettingsItem(
                    icon = Icons.Default.Sync,
                    title = title,
                    subtitle = subtitle,
                    isChecked = checked,
                    onCheckedChange = onCheckedChange,
                    onFocused = onItemFocused,
                    enabled = rowEnabled,
                )
            },
        )
    }
    item(key = "subtitle_auto_sync_tolerance") {
        AutoSyncToleranceSlider(
            preferredLanguage = preferredLanguage,
            enabled = enabled,
            onFocused = onItemFocused,
        )
    }
    item(key = "subtitle_auto_sync_aggressive") {
        AutoSyncAggressiveToggle(
            preferredLanguage = preferredLanguage,
            enabled = enabled,
            row = { title, subtitle, checked, onCheckedChange, rowEnabled ->
                ToggleSettingsItem(
                    icon = Icons.Default.Sync,
                    title = title,
                    subtitle = subtitle,
                    isChecked = checked,
                    onCheckedChange = onCheckedChange,
                    onFocused = onItemFocused,
                    enabled = rowEnabled,
                )
            },
        )
    }
}

@Composable
internal fun AutoSyncDeveloperToggles(
    preferredLanguage: String?,
    onRequestLanguage: () -> Unit,
    enabled: Boolean,
) {
    if (!BuildConfig.IS_DEBUG_BUILD) return
    AutoSyncToggle(
        preferredLanguage = preferredLanguage,
        onRequestLanguage = onRequestLanguage,
        enabled = enabled,
        row = { title, subtitle, checked, onCheckedChange, rowEnabled ->
            SettingsToggleRow(
                title = title,
                subtitle = subtitle,
                checked = checked,
                onToggle = { onCheckedChange(!checked) },
                enabled = rowEnabled,
            )
        },
    )
    AutoSyncToleranceSlider(
        preferredLanguage = preferredLanguage,
        enabled = enabled,
    )
    AutoSyncAggressiveToggle(
        preferredLanguage = preferredLanguage,
        enabled = enabled,
        row = { title, subtitle, checked, onCheckedChange, rowEnabled ->
            SettingsToggleRow(
                title = title,
                subtitle = subtitle,
                checked = checked,
                onToggle = { onCheckedChange(!checked) },
                enabled = rowEnabled,
            )
        },
    )
}

@Composable
private fun AutoSyncToggle(
    preferredLanguage: String?,
    onRequestLanguage: () -> Unit,
    enabled: Boolean,
    row: @Composable (
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    AutoSyncPreferences.ensureLoaded(context)
    val stored by AutoSyncPreferences.enabled.collectAsStateWithLifecycle()
    val languageChosen = isChosenSubtitleLanguage(preferredLanguage)
    row(
        stringResource(R.string.playback_autosync),
        if (languageChosen) {
            stringResource(R.string.playback_autosync_sub)
        } else {
            stringResource(R.string.playback_autosync_needs_language)
        },
        stored && languageChosen,
        { turnOn ->
            AutoSyncPreferences.setEnabled(context, turnOn)
            if (turnOn && !languageChosen) onRequestLanguage()
        },
        enabled,
    )
}

@Composable
private fun AutoSyncToleranceSlider(
    preferredLanguage: String?,
    enabled: Boolean,
    onFocused: () -> Unit = {},
) {
    val context = LocalContext.current
    AutoSyncPreferences.ensureLoaded(context)
    val toleranceMs by AutoSyncPreferences.syncToleranceMs.collectAsStateWithLifecycle()
    val storedEnabled by AutoSyncPreferences.enabled.collectAsStateWithLifecycle()
    val shownOn = storedEnabled && isChosenSubtitleLanguage(preferredLanguage)
    SliderSettingsItem(
        icon = Icons.Default.Timer,
        title = stringResource(R.string.playback_autosync_tolerance),
        subtitle = stringResource(R.string.playback_autosync_tolerance_sub),
        values = AutoSyncPreferences.syncToleranceOptionsMs,
        selected = toleranceMs,
        valueText = if (toleranceMs > 0) {
            stringResource(R.string.playback_autosync_tolerance_value, toleranceMs)
        } else {
            stringResource(R.string.playback_autosync_tolerance_off)
        },
        onValueChange = { AutoSyncPreferences.setSyncToleranceMs(context, it) },
        onFocused = onFocused,
        enabled = enabled && shownOn,
    )
}

@Composable
private fun AutoSyncAggressiveToggle(
    preferredLanguage: String?,
    enabled: Boolean,
    row: @Composable (
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    AutoSyncPreferences.ensureLoaded(context)
    val storedEnabled by AutoSyncPreferences.enabled.collectAsStateWithLifecycle()
    val aggressive by AutoSyncPreferences.aggressiveMode.collectAsStateWithLifecycle()
    val shownOn = storedEnabled && isChosenSubtitleLanguage(preferredLanguage)
    row(
        stringResource(R.string.playback_autosync_aggressive),
        stringResource(R.string.playback_autosync_aggressive_sub),
        aggressive,
        { AutoSyncPreferences.setAggressiveMode(context, it) },
        enabled && shownOn,
    )
}
