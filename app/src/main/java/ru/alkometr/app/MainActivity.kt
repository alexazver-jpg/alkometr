package ru.alkometr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.alkometr.app.ui.AppRoot
import ru.alkometr.app.ui.theme.AlkometrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AlkometrTheme {
                AppRoot()
            }
        }
    }
}
