package com.wannaverse.chimesdk

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled by OS; meeting start validates permissions itself */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        appContext = applicationContext
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissions.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
        setContent {
            App()
        }
    }
}
