package org.arcmediaserver.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.arcmediaserver.android.ui.ArcNavHost
import org.arcmediaserver.android.ui.theme.ArcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArcTheme { ArcNavHost() }
        }
    }
}
