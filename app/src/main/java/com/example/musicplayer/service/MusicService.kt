package com.example.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.musicplayer.LocalStateManager
import com.example.musicplayer.MainActivity
import com.example.musicplayer.PlayMode
import com.example.musicplayer.SettingsManager
import com.example.musicplayer.data.Song
import kotlin.random.Random

class MusicService : Service() {

    private val binder = LocalBinder()

    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: ForwardingPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var localStateManager: LocalStateManager
    private lateinit var settingsManager: SettingsManager

    private val queue = mutableListOf<Song>()
    private var currentIndex = -1
    private var currentMode = PlayMode.LIST_LOOP

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        localStateManager = LocalStateManager(this)
        settingsManager = SettingsManager(this)
        currentMode = localStateManager.getPlayMode()

        createNotificationChannel()

        player = ExoPlayer.Builder(this).build()
        player.volume = settingsManager.getVolume()

        // ExoPlayer only ever holds the current MediaItem (the app keeps its own queue),
        // so the timeline has no next/previous item. ForwardingPlayer exposes those
        // commands to MediaSession and routes them to the app's queue instead.
        sessionPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                if (queue.isNotEmpty()) {
                    builder.addAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    )
                }
                return builder.build()
            }

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> queue.isNotEmpty()
                else -> super.isCommandAvailable(command)
            }

            override fun seekToNext() {
                playNext()
            }

            override fun seekToPrevious() {
                playPrevious()
            }

            override fun seekToNextMediaItem() {
                playNext()
            }

            override fun seekToPreviousMediaItem() {
                playPrevious()
            }
        }

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(createSessionActivityPendingIntent())
            .setCallback(object : MediaSession.Callback {
                // media3 dispatches transport commands (play/pause/seek) from
                // controllers directly to the Player, and next/previous to the
                // sessionPlayer above. The callback only decides which commands
                // connected controllers are allowed to send.
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult =
                    // Grant all player commands; MediaSession intersects them with the
                    // commands actually exposed by [sessionPlayer].
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(
                            Player.Commands.Builder().addAllCommands().build()
                        )
                        .build()
            })
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (settingsManager.getAutoPlay()) {
                        playNext()
                    }
                }
                updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
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
            player.seekTo(positionMs)
        }
    }

    fun playAt(index: Int, autoSeekMs: Long = 0L) {
        if (queue.isNotEmpty() && index in queue.indices) {
            currentIndex = index
            val song = queue[index]
            val metadata = buildMediaMetadata(song)
            val item = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(metadata)
                .build()
            player.setMediaItem(item)
            player.prepare()
            if (autoSeekMs > 0L) player.seekTo(autoSeekMs)
            player.play()
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
        updateNotification()
    }

    fun isPlaying(): Boolean = player.isPlaying

    fun getCurrentSong(): Song? = if (currentIndex in queue.indices) queue[currentIndex] else null

    fun getCurrentPositionMs(): Long = player.currentPosition

    fun getDurationMs(): Long = player.duration

    fun seekTo(positionMs: Long) {
        if (positionMs >= 0L) player.seekTo(positionMs)
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        player.volume = clamped
        settingsManager.saveVolume(clamped)
    }

    fun getVolume(): Float = player.volume

    /**
     * Metadata mirrored to the system through MediaSession: title, artist, album,
     * duration and album artwork.
     */
    private fun buildMediaMetadata(song: Song): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

        val artworkUri = song.albumArtUri
        if (!artworkUri.isNullOrBlank()) {
            builder.setArtworkUri(Uri.parse(artworkUri))
        } else {
            loadEmbeddedArtwork(song.uri)?.let { data ->
                builder.setArtworkData(data, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }

        return builder.build()
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
            .build()
    }
}
