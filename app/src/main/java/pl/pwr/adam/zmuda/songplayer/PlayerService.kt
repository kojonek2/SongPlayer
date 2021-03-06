package pl.pwr.adam.zmuda.songplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver


class PlayerService : MediaBrowserServiceCompat() {

    companion object {
        const val LOG_TAG = "PlayerService_MediaSession"
        const val MEDIA_ROOT_ID = "root"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaPlayer: MediaPlayer

    private lateinit var songs : MutableList<MediaMetadataCompat>
    private var currentSongIndex = 0

    override fun onCreate() {
        super.onCreate()

        mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )

        songs = loadSongs()

        mediaSession = MediaSessionCompat(baseContext, LOG_TAG)

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
            .build()

        mediaSession.setPlaybackState(playbackState)

        setMedia(songs[currentSongIndex].description.mediaId!!)

        mediaSession.setCallback(MediaSessionCallbacks(this, mediaSession, mediaPlayer))
        sessionToken = mediaSession.sessionToken
    }

    fun setMedia(mediaId: String) {
        var song : MediaMetadataCompat? = null
        for (i in songs.indices) {
            if (songs[i].description.mediaId == mediaId ) {
                song = songs[i]
                currentSongIndex = i
            }
        }

        if (song == null)
            return

        val songId = mediaId.toLong()
        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

        mediaPlayer.reset()
        mediaPlayer.setDataSource(applicationContext, contentUri)
        mediaPlayer.prepare()

        mediaSession.setMetadata(song)
    }

    fun nextSong() {
        currentSongIndex = (currentSongIndex + 1) % songs.size
        setMedia(songs[currentSongIndex].description.mediaId!!)
    }

    fun prevSong() {
        currentSongIndex--
        if (currentSongIndex < 0)
            currentSongIndex = songs.size - 1

        setMedia(songs[currentSongIndex].description.mediaId!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaPlayer.release()
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        if (parentId == MEDIA_ROOT_ID) {
            for (song in songs) {
                val item = MediaBrowserCompat.MediaItem(song.description, FLAG_PLAYABLE)
                mediaItems.add(item)
            }

            return result.sendResult(mediaItems)
        }

        return result.sendResult(null)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    private fun loadSongs() : MutableList<MediaMetadataCompat> {
        val resolver: ContentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION
        )

        val result = mutableListOf<MediaMetadataCompat>()
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        val sortOrder = MediaStore.MediaColumns.DURATION+" DESC" //TODO remove
        val cursor: Cursor? = resolver.query(uri, projection, selection, null, sortOrder)
        when {
            cursor == null -> {}
            cursor.moveToFirst() -> {
                do {
                    val art = getArtForSongId(cursor.getLong(0))

                    val metadata = MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getLong(0).toString())
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(1))
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(2))
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, cursor.getString(3))
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, cursor.getLong(4))

                    if (art != null)
                        metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)


                    result.add(metadata.build())

                } while (cursor.moveToNext())
            }
        }

        cursor?.close()
        return result
    }

    private fun getArtForSongId(id: Long): Bitmap? {
        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val mmr = MediaMetadataRetriever()
        val rawArt: ByteArray?

        mmr.setDataSource(applicationContext, songUri)
        rawArt = mmr.embeddedPicture

        if (null != rawArt)
            return BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, BitmapFactory.Options())

        return null
    }
}