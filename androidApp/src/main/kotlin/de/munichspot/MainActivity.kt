package de.munichspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import de.munichspot.data.initDataCache

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDataCache(this)
        enableEdgeToEdge()
        setContent { App() }
    }
}
