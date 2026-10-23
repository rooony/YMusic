package com.example.musicplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.AlbumArtManager
import com.example.musicplayer.AlbumInfo
import com.example.musicplayer.ArtistInfo
import com.example.musicplayer.LanguageManager
import com.example.musicplayer.LocalStateManager
import com.example.musicplayer.PlayMode
import com.example.musicplayer.Playlist
import com.example.musicplayer.PlaylistManager
import com.example.musicplayer.SettingsManager
import com.example.musicplayer.data.Song
import com.example.musicplayer.data.SongDatabase
import com.example.musicplayer.data.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ymusic_pref", Context.MODE_PRIVATE)
    private val localStateManager = LocalStateManager(application.applicationContext)
    private val playlistManager = PlaylistManager(application.applicationContext)
    private val albumArtManager = AlbumArtManager(application.applicationContext)
    private val settingsManager = SettingsManager(application.applicationContext)

    private val repository: SongRepository =
        SongRepository(application.applicationContext, SongDatabase.getInstance(application))

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<Song>>(emptyList())
    val recentSongs: StateFlow<List<Song>> = _recentSongs.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _excludedFolders = MutableStateFlow<List<String>>(emptyList())
    val excludedFolders: StateFlow<List<String>> = _excludedFolders.asStateFlow()

    private val _isDark = MutableStateFlow(settingsManager.getDarkMode())
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumInfo>>(emptyList())
    val albums: StateFlow<List<AlbumInfo>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistInfo>>(emptyList())
    val artists: StateFlow<List<ArtistInfo>> = _artists.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playMode = MutableStateFlow(localStateManager.getPlayMode())
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _playlists = MutableStateFlow(playlistManager.loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _volume = MutableStateFlow(settingsManager.getVolume())
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _autoPlay = MutableStateFlow(settingsManager.getAutoPlay())
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    private val _showAlbumArt = MutableStateFlow(settingsManager.getShowAlbumArt())
    val showAlbumArt: StateFlow<Boolean> = _showAlbumArt.asStateFlow()

    private val _themeColor = MutableStateFlow(settingsManager.getThemeColor())
    val themeColor: StateFlow<Int> = _themeColor.asStateFlow()

    private val _language = MutableStateFlow(settingsManager.getLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllSongs().collect { list ->
                _songs.value = list
                updateAlbums(list)
                updateArtists(list)
            }
        }

        viewModelScope.launch {
            repository.addDefaultExcludedFoldersIfMissing()
            refreshExcludedFolders()
        }
    }

    fun setLanguage(languageCode: String) {
        _language.value = languageCode
        settingsManager.saveLanguage(languageCode)
    }

    fun setSearchText(text: String) {
        _searchText.value = text
    }

    fun toggleTheme() {
        val newValue = !_isDark.value
        _isDark.value = newValue
        settingsManager.saveDarkMode(newValue)
    }

    fun setThemeColor(color: Int) {
        _themeColor.value = color
        settingsManager.saveThemeColor(color)
    }

    fun setVolume(value: Float) {
        _volume.value = value
        settingsManager.saveVolume(value)
    }

    fun setAutoPlay(value: Boolean) {
        _autoPlay.value = value
        settingsManager.saveAutoPlay(value)
    }

    fun setShowAlbumArt(value: Boolean) {
        _showAlbumArt.value = value
        settingsManager.saveShowAlbumArt(value)
    }

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        localStateManager.savePlayMode(mode)
    }

    fun saveSongState(song: Song?, positionMs: Long) {
        if (song != null) localStateManager.saveLastSongId(song.id)
        localStateManager.saveLastPosition(positionMs)
    }

    fun restorePlaybackState(): Pair<Long, Long> {
        val songId = localStateManager.getLastSongId()
        val position = localStateManager.getLastPosition()
        return Pair(songId, position)
    }

    fun createPlaylist(name: String, songs: List<Song>) {
        val newList = _playlists.value.toMutableList()
        newList.add(Playlist(name, songs.map { it.id }))
        _playlists.value = newList
        playlistManager.savePlaylists(newList)
    }

    fun addSongToPlaylist(playlistName: String, song: Song) {
        val newList = _playlists.value.toMutableList()
        val foundIndex = newList.indexOfFirst { it.name == playlistName }
        if (foundIndex >= 0) {
            val updated = newList[foundIndex].copy(
                songs = newList[foundIndex].songs + song.id
            )
            newList[foundIndex] = updated
            _playlists.value = newList
            playlistManager.savePlaylists(newList)
        }
    }

    fun deletePlaylist(name: String) {
        val newList = _playlists.value.filterNot { it.name == name }
        _playlists.value = newList
        playlistManager.savePlaylists(newList)
    }

    fun getPlaylistByName(name: String): List<Song> {
        val found = _playlists.value.find { it.name == name } ?: return emptyList()
        val map = _songs.value.associateBy { it.id }
        return found.songs.mapNotNull { map[it] }
    }

    fun getFilteredSongs(): List<Song> {
        val q = _searchText.value.trim()
        return if (q.isEmpty()) {
            _songs.value
        } else {
            _songs.value.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
            }
        }
    }

    fun refreshSongs() {
        viewModelScope.launch {
            repository.addDefaultExcludedFoldersIfMissing()
            val result = repository.scanAndSaveSongs()
            _songs.value = result
            updateAlbums(result)
            updateArtists(result)
            refreshFavorites()
            refreshRecent()
            refreshExcludedFolders()
        }
    }

    fun refreshFavorites() {
        viewModelScope.launch {
            val ids = repository.getFavoriteSongIds().toSet()
            _favoriteIds.value = ids
        }
    }

    fun refreshRecent() {
        viewModelScope.launch {
            val recent = repository.getRecentSongs()
            val songsMap = _songs.value.associateBy { it.id }
            _recentSongs.value = recent.mapNotNull { songsMap[it.songId] }
        }
    }

    fun refreshExcludedFolders() {
        viewModelScope.launch {
            _excludedFolders.value = repository.getExcludedFolders()
        }
    }

    fun addExcludedFolder(path: String) {
        viewModelScope.launch {
            repository.addExcludedFolder(path)
            refreshExcludedFolders()
            refreshSongs()
        }
    }

    fun removeExcludedFolder(path: String) {
        viewModelScope.launch {
            repository.removeExcludedFolder(path)
            refreshExcludedFolders()
            refreshSongs()
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id)
            refreshFavorites()
        }
    }

    fun markRecentPlay(song: Song) {
        viewModelScope.launch {
            repository.addRecentPlay(song)
            refreshRecent()
        }
    }

    fun setCurrentSong(song: Song?) {
        _currentSong.value = song
    }

    fun setQueue(queue: List<Song>) {
        _queue.value = queue
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in _queue.value.indices || toIndex !in _queue.value.indices) return
        val newList = _queue.value.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        _queue.value = newList
    }

    fun sortQueueByTitle() {
        _queue.value = _queue.value.sortedBy { it.title }
    }

    fun removeSongFromQueue(songId: Long) {
        _queue.value = _queue.value.filterNot { it.id == songId }
    }

    fun setProgress(positionMs: Long, durationMs: Long) {
        _currentPositionMs.value = positionMs
        _durationMs.value = durationMs
    }

    fun getAlbumArtManager(): AlbumArtManager = albumArtManager

    private fun updateAlbums(list: List<Song>) {
        val map = list.groupBy { it.album }
        _albums.value = map.map { (albumName, songsInAlbum) ->
            AlbumInfo(
                name = albumName,
                artist = songsInAlbum.firstOrNull()?.artist ?: "Unknown Artist",
                count = songsInAlbum.size
            )
        }.sortedBy { it.name }
    }

    private fun updateArtists(list: List<Song>) {
        val map = list.groupBy { it.artist }
        _artists.value = map.map { (artistName, artistSongs) ->
            ArtistInfo(
                name = artistName,
                count = artistSongs.size
            )
        }.sortedBy { it.name }
    }
}
