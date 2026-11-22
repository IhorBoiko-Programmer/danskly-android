package app.danskly

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.danskly.ui.DansklyApp

class DansklyApplication : Application() {
    /** Created on first use, so tests can replace it before the activity starts. */
    var graph: AppGraph? = null

    fun graph(): AppGraph = graph ?: AppGraph.create(this).also { graph = it }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as DansklyApplication).graph()
        setContent { DansklyApp(graph) }
    }
}
