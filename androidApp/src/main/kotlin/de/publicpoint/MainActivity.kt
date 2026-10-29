package de.publicpoint

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import de.publicpoint.data.initDataCache

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        }
        initDataCache(this)
        enableEdgeToEdge()
        setContent { App() }
    }
}
