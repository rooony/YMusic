package com.example.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicplayer.LocalStateManager
import com.example.musicplayer.MainActivity
import com.example.musicplayer.PlayMode
import com.example.musicplayer.SettingsManager
import com.example.musicplayer.data.Song
import kotlin.random.Random

class MusicService : Service() {

    private val binder = LocalBinder()

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var localStateManager: LocalStateManager
    private lateinit var settingsManager: SettingsManager

    private val queue = mutableListOf<Song>()
    private var currentIndex = -1
    private var currentMode = PlayMode.LIST_LOOP

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        localStateManager = LocalStateManager(this)
        settingsManager = SettingsManager(this)
        currentMode = localStateManager.getPlayMode()

        createNotificationChannel()

        player = ExoPlayer.Builder(this).build()
        player.volume = settingsManager.getVolume()

        // MediaSessionCompat publishes metadata and playback state to the system
        // (lock screen, notification shade, Bluetooth, Android Auto, ...).
        mediaSession = MediaSessionCompat(this, "YMusic").apply {
            setSessionActivity(createSessionActivityPendingIntent())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player.play()
                    updatePlaybackState()
                }

                override fun onPause() {
                    player.pause()
                    updatePlaybackState()
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }

                override fun onStop() {
                    player.pause()
                    updatePlaybackState()
                }
            })
            isActive = true
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (settingsManager.getAutoPlay()) {
                        playNext()
                    }
                }
                updatePlaybackState()
                updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                updateNotification()
            }
        })

        startForeground(1, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PREVIOUS" -> playPrevious()
            "ACTION_PLAY_PAUSE" -> togglePlayPause()
            "ACTION_NEXT" -> playNext()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val song = getCurrentSong()
        if (song != null) {
            localStateManager.saveLastSongId(song.id)
            localStateManager.saveLastPosition(player.currentPosition)
        }
        mediaSession?.release()
        player.release()
        super.onDestroy()
    }

    fun setPlayMode(mode: PlayMode) {
        currentMode = mode
        localStateManager.savePlayMode(mode)
    }

    fun getPlayMode(): PlayMode = currentMode

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(songs)

        if (queue.isNotEmpty()) {
            currentIndex = startIndex.coerceIn(0, queue.lastIndex)
            playAt(currentIndex)
        } else {
            currentIndex = -1
            player.stop()
        }
    }

    fun getQueue(): List<Song> = queue.toList()

    fun restoreFromSavedState(songId: Long, songs: List<Song>, positionMs: Long = 0L) {
        if (songs.isEmpty()) return

        val targetIndex = songs.indexOfFirst { it.id == songId }
        if (targetIndex == -1) {
            setQueue(songs, 0)
            if (positionMs > 0L) seekTo(positionMs)
            return
        }

        setQueue(songs, targetIndex)
        if (positionMs > 0L) {
            seekTo(positionMs)
        }
    }

    fun playAt(index: Int, autoSeekMs: Long = 0L) {
        if (queue.isNotEmpty() && index in queue.indices) {
            currentIndex = index
            val song = queue[index]
            val item = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .build()
            player.setMediaItem(item)
            player.prepare()
            if (autoSeekMs > 0L) player.seekTo(autoSeekMs)
            player.play()
            mediaSession?.setMetadata(buildMediaMetadata(song))
            updatePlaybackState()
            updateNotification()
        }
    }

    fun playNext() {
        if (queue.isEmpty()) return

        val nextIndex = when (currentMode) {
            PlayMode.LIST_LOOP -> {
                if (currentIndex >= 0) (currentIndex + 1) % queue.size else 0
            }
            PlayMode.SHUFFLE -> {
                if (queue.size > 1) {
                    var randomIndex = Random.nextInt(queue.size)
                    while (randomIndex == currentIndex && queue.size > 1) {
                        randomIndex = Random.nextInt(queue.size)
                    }
                    randomIndex
                } else {
                    0
                }
            }
            PlayMode.SINGLE_LOOP -> {
                currentIndex.takeIf { it >= 0 } ?: 0
            }
        }

        playAt(nextIndex)
    }

    fun playPrevious() {
        if (queue.isEmpty()) return

        val prevIndex = when (currentMode) {
            PlayMode.LIST_LOOP -> {
                if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
            }
            PlayMode.SHUFFLE -> {
                if (queue.size > 1) {
                    var randomIndex = Random.nextInt(queue.size)
                    while (randomIndex == currentIndex && queue.size > 1) {
                        randomIndex = Random.nextInt(queue.size)
                    }
                    randomIndex
                } else {
                    0
                }
            }
            PlayMode.SINGLE_LOOP -> {
                currentIndex.takeIf { it >= 0 } ?: 0
            }
        }

        playAt(prevIndex)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updatePlaybackState()
        updateNotification()
    }

    fun isPlaying(): Boolean = player.isPlaying

    fun getCurrentSong(): Song? = if (currentIndex in queue.indices) queue[currentIndex] else null

    fun getCurrentPositionMs(): Long = player.currentPosition

    fun getDurationMs(): Long = player.duration

    fun seekTo(positionMs: Long) {
        if (positionMs >= 0L) {
            player.seekTo(positionMs)
            updatePlaybackState()
        }
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        player.volume = clamped
        settingsManager.saveVolume(clamped)
    }

    fun getVolume(): Float = player.volume

    /**
     * Metadata published to the system through [MediaSessionCompat.setMetadata].
     * Lock screens, notifications, Bluetooth devices and Android Auto read from it.
     */
    private fun buildMediaMetadata(song: Song): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)

        loadArtworkBitmap(song)?.let { artwork ->
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
        }

        return builder.build()
    }

    private fun loadArtworkBitmap(song: Song): Bitmap? {
        val artworkUri = song.albumArtUri
        if (!artworkUri.isNullOrBlank()) {
            val decoded = try {
                contentResolver.openInputStream(Uri.parse(artworkUri))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {
                null
            }
            if (decoded != null) return decoded
        }

        val embedded = loadEmbeddedArtwork(song.uri) ?: return null
        return BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
    }

    private fun loadEmbeddedArtwork(uriString: String): ByteArray? {
        if (uriString.isBlank()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, Uri.parse(uriString))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Publishes playback state and position so system media controls can show
     * progress and the correct play/pause button.
     */
    private fun updatePlaybackState() {
        val session = mediaSession ?: return

        val state = when {
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                state,
                player.currentPosition.coerceAtLeast(0L),
                if (player.isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime()
            )
            .build()
        session.setPlaybackState(playbackState)
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "music_channel",
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val song = getCurrentSong()

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val previousIntent = Intent(this, MusicService::class.java).apply {
            action = "ACTION_PREVIOUS"
        }
        val previousPendingIntent = PendingIntent.getService(
            this,
            1,
            previousIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = "ACTION_PLAY_PAUSE"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = "ACTION_NEXT"
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "music_channel")
            .setContentTitle(song?.title ?: "YMusic")
            .setContentText(song?.artist ?: "Ready")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousPendingIntent)
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }
}
