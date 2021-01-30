package pl.pwr.adam.zmuda.songplayer

import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.*
import pl.pwr.adam.zmuda.songplayer.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService


class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var binding: ActivityMainBinding

    private val executorService : ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dividerItemDecoration = DividerItemDecoration(binding.musicList.getContext(), DividerItemDecoration.VERTICAL)
        binding.musicList.addItemDecoration(dividerItemDecoration)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, PlayerService::class.java),
            connectionCallbacks,
            null
        )

        binding.seekBar.setOnSeekBarChangeListener(this)
    }

    var updateSeekBarJob : Job? = null

    private fun startUpdatingSeekBar() {
        stopUpdatingSeekBar()
        updateSeekBarJob = GlobalScope.launch {
            while(true) {
                delay(1000)
                binding.seekBar.progress = binding.seekBar.progress + 1
            }
        }
    }

    private fun stopUpdatingSeekBar() {
        if (updateSeekBarJob != null && !updateSeekBarJob!!.isCancelled) {
            updateSeekBarJob!!.cancel()
        }
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {

            val mediaController = MediaControllerCompat(
                this@MainActivity,
                mediaBrowser!!.sessionToken
            )

            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

            binding.seekBar.max = (mediaController.metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000).toInt()
            binding.musicTitleTV.text = mediaController.metadata.description.title

            setupTransportControls()

            mediaBrowser.subscribe(mediaBrowser.root, subscriptionCallback)
        }


        override fun onConnectionSuspended() {}

        override fun onConnectionFailed() {}
    }

    private fun setupTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this)

        binding.playPause.setOnClickListener {
            if (mediaController.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                mediaController.transportControls.pause()
            else
                mediaController.transportControls.play()
        }

        binding.stop.setOnClickListener {
            mediaController.transportControls.stop()
        }

        binding.next.setOnClickListener {
            mediaController.transportControls.skipToNext()
        }

        binding.prev.setOnClickListener {
            mediaController.transportControls.skipToPrevious()
        }

        binding.reverse10.setOnClickListener {
            mediaController.transportControls.seekTo((binding.seekBar.progress - 10).toLong() * 1000)
            mediaController.transportControls.seekTo((binding.seekBar.progress - 10).toLong() * 1000)
        }

        binding.forward10.setOnClickListener {
            mediaController.transportControls.seekTo((binding.seekBar.progress + 10).toLong() * 1000)
            mediaController.transportControls.seekTo((binding.seekBar.progress + 10).toLong() * 1000)
        }

        mediaController.registerCallback(controllerCallback)
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            binding.seekBar.max = (metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000).toInt()
            binding.musicTitleTV.text = metadata.description.title
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            binding.seekBar.progress = (state.position / 1000).toInt()

            if (state.playbackSpeed > 0)
                startUpdatingSeekBar()
            else
                stopUpdatingSeekBar()
        }
    }

    private var subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {

        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {

            binding.musicList.adapter = MusicListAdapter(children) { item -> musicItemClicked(item)}
        }
    }

    private fun musicItemClicked(item: MediaBrowserCompat.MediaItem) {
        val mediaController = MediaControllerCompat.getMediaController(this)

        mediaController.transportControls.playFromMediaId(item.mediaId, null)
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

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            mediaController.transportControls.seekTo(progress.toLong() * 1000)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}