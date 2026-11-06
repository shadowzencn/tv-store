package com.shadowzen.tvstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shadowzen.tvstore.core.designsystem.TvStoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvStoreTheme {
                TvStoreApp()
            }
        }
    }
}
