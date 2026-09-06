package com.karaza.squish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.media3.common.util.UnstableApi
import com.karaza.squish.ui.SquishApp
import com.karaza.squish.ui.theme.SquishTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: CompressionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            viewModel.handleIntent(intent)
        }

        setContent {
            SquishTheme {
                SquishApp(viewModel)
            }
        }
    }
}
