package com.facecam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.navigation.FaceCamNav
import com.facecam.app.ui.theme.FaceCamTheme

/**
 * Single-activity host. All screens are Compose destinations hosted by
 * [FaceCamNav]; shared state lives in [FaceCamViewModel].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: FaceCamViewModel = viewModel()
                    FaceCamNav(viewModel = viewModel)
                }
            }
        }
    }
}
