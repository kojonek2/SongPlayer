package pl.pwr.adam.zmuda.songplayer

import android.content.ComponentName
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import pl.pwr.adam.zmuda.songplayer.databinding.ActivityMainBinding
import java.security.Permission


class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, PlayerService::class.java),
            connectionCallbacks,
            null
        )
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {

            val mediaController = MediaControllerCompat(
                this@MainActivity,
                mediaBrowser!!.sessionToken
            )

            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

            setupTransportControls()
        }

        override fun onConnectionSuspended() {
            Log.d("test", "a")
        }

        override fun onConnectionFailed() {
            Log.d("test", "a")
        }
    }

    private fun setupTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this)

        binding.play.setOnClickListener {
            mediaController.transportControls.play()
        }

        binding.pause.setOnClickListener {
            mediaController.transportControls.pause()
        }

        mediaController.registerCallback(controllerCallback)
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {}

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {}
    }

    public override fun onStart() {
        super.onStart()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE);
        }
        else {
            afterGainingPermissions()
        }
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    public override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)

        if (mediaBrowser.isConnected)
            mediaBrowser.disconnect()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    afterGainingPermissions()
                } else {
                    Toast.makeText(this, getString(R.string.read_permision_denied), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun afterGainingPermissions() {
        mediaBrowser.connect()
    }
}