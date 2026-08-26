package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InactiveSubscriptionDialog(onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.subscription_inactive_title),
        subtitle = stringResource(R.string.subscription_inactive_message),
        titleTextAlign = TextAlign.Center
    ) {
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        ) {
            androidx.tv.material3.Text(stringResource(R.string.action_ok))
        }
    }
}
