package com.test.audiorecorder

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.test.audiorecorder.service.MediaCaptureService
import com.test.audiorecorder.service.MediaCaptureService.Companion.ACTION_START
import com.test.audiorecorder.service.MediaCaptureService.Companion.ACTION_STOP
import com.test.audiorecorder.ui.theme.AudioRecorderTheme

class MainActivity : ComponentActivity() {


    lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var mediaProjectionManager: MediaProjectionManager

    companion object {
        private const val MEDIA_PROJECTION_REQUEST_CODE = 13
        private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Handle the result here
            if (result.resultCode == Activity.RESULT_OK) {
                // Process the result data
                val data: Intent? = result.data
                Intent(applicationContext, MediaCaptureService::class.java).apply {
                    this.action = ACTION_START
                    this.putExtra(MediaCaptureService.EXTRA_RESULT_DATA , data)

                    startService(this)
                }
            }
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ),
            0
        )




        setContent {
            AudioRecorderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            Button(onClick = {
//                                Intent(applicationContext, MediaCaptureService::class.java).apply {
//                                    this.action = ACTION_START
//
//                                    startService(this)
//                                }

                                mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

                                launcher.launch(mediaProjectionManager.createScreenCaptureIntent(),)


                            }) {
                                Text(text = "start", style = MaterialTheme.typography.bodyMedium)
                            }

                            Button(onClick = {
                                Intent(applicationContext, MediaCaptureService::class.java).apply {
                                    this.action = ACTION_STOP

                                    stopService(this)
                                }
                            }) {
                                Text(text = "stop", style = MaterialTheme.typography.bodyMedium)

                            }

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AudioRecorderTheme {
        Greeting("Android")
    }
}