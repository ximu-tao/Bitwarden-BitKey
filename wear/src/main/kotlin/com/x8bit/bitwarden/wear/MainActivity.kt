package com.x8bit.bitwarden.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Text
import com.x8bit.bitwarden.wear.ui.SdkStatusViewModel
import com.x8bit.bitwarden.wear.ui.theme.BitwardenWearTheme

/**
 * Entry point for the Wear OS app.
 */
class MainActivity : ComponentActivity() {

    private val sdkStatusViewModel: SdkStatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitwardenWearTheme {
                WearAppRoot(sdkStatusViewModel = sdkStatusViewModel)
            }
        }
    }
}

@Composable
private fun WearAppRoot(sdkStatusViewModel: SdkStatusViewModel) {
    val sdkStatus by sdkStatusViewModel.sdkStatusFlow.collectAsStateWithLifecycle()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "Bitwarden Wear",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                text = "SDK: $sdkStatus",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}