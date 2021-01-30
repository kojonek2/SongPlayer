package pl.pwr.adam.zmuda.songplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver

class MediaSessionCallbacks(private val service: PlayerService, private val mediaSession: MediaSessionCompat, private val mediaPlayer: MediaPlayer) : MediaSessionCompat.Callback(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "ForegroundService"
    }

    private var becomeNoisyRegistered: Boolean = false
    private val intentFilter = IntentFilter(ACTION_AUDIO_BECOMING_NOISY)
    private val context = service.baseContext

    private lateinit var audioFocusRequest: AudioFocusRequest

    init {
        createNotificationChannel()

        mediaPlayer.setOnCompletionListener {
            onSkipToNext()
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_AUDIO_BECOMING_NOISY) {
                onPause()
            }
        }
    }

    override fun onPlay() {
        if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) //prevent double clicks
            return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            )
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            context.startService(Intent(context, PlayerService::class.java))
            mediaSession.isActive = true

            mediaPlayer.start()


            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                    .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.currentPosition.toLong(), mediaPlayer.playbackParams.speed)
                    .build()
            )

            context.registerReceiver(becomingNoisyReceiver, intentFilter)
            becomeNoisyRegistered = true

            val notification = createNotification()
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onPause() {
        if (mediaSession.controller.playbackState.state != PlaybackStateCompat.STATE_PLAYING) //prevent double clicks
            return

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.abandonAudioFocusRequest(audioFocusRequest)

        if (becomeNoisyRegistered)
            context.unregisterReceiver(becomingNoisyReceiver)
        becomeNoisyRegistered = false
        mediaSession.isActive = false

        mediaPlayer.pause()

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                .setState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer.currentPosition.toLong(), mediaPlayer.playbackParams.speed)
                .build()
        )

        val notification = createNotification()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    override fun onStop() {
        if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_STOPPED) //prevent double clicks
            return

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaPlayer.seekTo(0)//Needed for resetting positions so it will be ok in notification which will be changed after calling start on mediaPlayer.
                                    //Without it mediaPlayer will start to play song from beginning but currentPosition will be wrong for a moment.
        mediaPlayer.stop() //prepare to play song again
        mediaPlayer.prepare()


        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, mediaPlayer.playbackParams.speed)
                .build()
        )

        if (becomeNoisyRegistered)
            context.unregisterReceiver(becomingNoisyReceiver)
        becomeNoisyRegistered = false

        val notification = createNotification()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        service.stopForeground(false)
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        service.setMedia(mediaId)
        newMediaSet()
    }

    override fun onSkipToNext() {
        service.nextSong()
        newMediaSet()
    }

    override fun onSkipToPrevious() {
        service.prevSong()
        newMediaSet()
    }

    private fun newMediaSet() {
        if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) { //if player was playing then we need only to start it again
            mediaPlayer.start()

            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, mediaPlayer.playbackParams.speed)
                    .build()
            )

            val notification = createNotification()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
        else
            onPlay()
    }

    override fun onSeekTo(pos: Long) {
        mediaPlayer.seekTo(pos.toInt())

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder(mediaSession.controller.playbackState)
                .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.currentPosition.toLong(), mediaPlayer.playbackParams.speed)
                .build()
        )
    }

    override fun onAudioFocusChange(focusChange: Int) {
        TODO("Not yet implemented")
    }

    private fun createNotification() : Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val description = mediaSession.controller.metadata.description
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(description.title)
            .setContentText(description.subtitle)
            .setSubText(description.description)
            .setLargeIcon(description.iconBitmap)

        builder.setContentIntent(pendingIntent)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_STOP
                )
            )

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_music_note)

        val state = mediaSession.controller.playbackState.state
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause,
                    "Pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PAUSE
                    )
                )
            )
        }

        if (state == PlaybackStateCompat.STATE_PAUSED || state == PlaybackStateCompat.STATE_STOPPED) {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_play,
                    "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PLAY
                    )
                )
            )
        }

        if (state == PlaybackStateCompat.STATE_PAUSED || state == PlaybackStateCompat.STATE_PLAYING) {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stop,
                    "Stop",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
            )
        }

        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(CHANNEL_ID,"Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}

