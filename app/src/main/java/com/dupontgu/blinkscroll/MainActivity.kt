package com.dupontgu.blinkscroll

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat

private const val REQUEST_CODE_PERMISSIONS = 10

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen(this::navigateToAccessibilitySettings) }
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun navigateToAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}

@Preview
@Composable
fun MainScreen(
    navToAccessibilitySettings: () -> Unit = {}
) {
    Column(
        Modifier
            .background(Color.White)
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text("Instructions", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("1. Allow Camera permissions for this app. Restart the app if you accidentally click past it.")
        Text("2. Go to 'Accessibility' settings, find the switch for 'BlinkScroll'")
        Text("3. Turn 'BlinkScroll' on, accept screen control permissions.")
        Text("4. Blink to scroll!! (Warning: this will work everywhere, until you turn 'BlinkScroll' off!)")
        Spacer(modifier = Modifier.height(12.dp))
        Button(navToAccessibilitySettings) {
            Text("Open A11Y Settings")
        }
    }
}