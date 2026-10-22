package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.data.Song
import com.example.musicplayer.service.MusicService
import com.example.musicplayer.ui.MusicViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YMusicScreen(
    songs: List<Song>,
    albums: List<AlbumInfo>,
    artists: List<ArtistInfo>,
    currentSong: Song?,
    favoriteIds: Set<Long>,
    recentSongs: List<Song>,
    searchText: String,
    excludedFolders: List<String>,
    isDark: Boolean,
    queue: List<Song>,
    currentPositionMs: Long,
    durationMs: Long,
    playMode: PlayMode,
    playlists: List<Playlist>,
    volume: Float,
    autoPlay: Boolean,
    themeColor: Int,
    language: String,
    onToggleTheme: () -> Unit,
    onSetThemeColor: (Int) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetAutoPlay: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSortQueue: () -> Unit,
    onRemoveFromQueue: (Long) -> Unit,
    onReorderQueue: (Int, Int) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    val filteredSongs = songs.filter {
        it.title.contains(searchText, ignoreCase = true) ||
        it.artist.contains(searchText, ignoreCase = true) ||
        it.album.contains(searchText, ignoreCase = true)
    }

    var selectedTab by remember { mutableStateOf("all") }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var folderInput by remember { mutableStateOf("") }
    var playlistNameInput by remember { mutableStateOf("") }
    var selectedPlaylistName by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var folderToRemove by remember { mutableStateOf<String?>(null) }
    var localVolume by remember { mutableStateOf(volume) }

    val playingHighlight = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f)

    val listToShow = when (selectedTab) {
        "favorite" -> songs.filter { favoriteIds.contains(it.id) }
        "recent" -> recentSongs
        "album" -> {
            if (selectedAlbum == null) {
                albums.map { album ->
                    Song(
                        id = -1L,
                        title = album.name,
                        artist = album.artist,
                        album = album.name,
                        duration = 0L,
                        uri = "",
                        path = ""
                    )
                }
            } else {
                songs.filter { it.album == selectedAlbum }
            }
        }
        "artist" -> {
            if (selectedArtist == null) {
                artists.map { artist ->
                    Song(
                        id = -1L,
                        title = artist.name,
                        artist = artist.name,
                        album = "Artist",
                        duration = 0L,
                        uri = "",
                        path = ""
                    )
                }
            } else {
                songs.filter { it.artist == selectedArtist }
            }
        }
        "queue" -> queue
        "playlist" -> {
            if (selectedPlaylistName == null) {
                songs
            } else {
                val found = playlists.find { it.name == selectedPlaylistName }
                val map = songs.associateBy { it.id }
                found?.songs?.mapNotNull { map[it] } ?: emptyList()
            }
        }
        else -> filteredSongs
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Y³Music",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable { showAboutDialog = true }
            )
            Row {
                Button(onClick = onToggleTheme) {
                    Text(if (isDark) "浅色" else "深色")
                }
                Button(onClick = onScanClick) {
                    Text(if (language == LanguageManager.LANG_ZH) "扫描" else "Scan")
                }
                Button(onClick = { showSettings = !showSettings }) {
                    Text(if (language == LanguageManager.LANG_ZH) "设置" else "Settings")
                }
            }
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Y³Music by R_Y\r\n\r\nPower by Android & Github & Deepseek",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { showAboutDialog = false }) {
                            Text(if (language == LanguageManager.LANG_ZH) "确定" else "OK")
                        }
                    }
                }
            )
        }

        val targetFolder = folderToRemove
        if (targetFolder != null) {
            AlertDialog(
                onDismissRequest = { folderToRemove = null },
                title = {
                    Text(if (language == LanguageManager.LANG_ZH) "删除已排除目录" else "Remove excluded folder")
                },
                text = {
                    Text(
                        if (language == LanguageManager.LANG_ZH) {
                            "确定要删除以下已排除目录吗？\n\n$targetFolder"
                        } else {
                            "Remove this excluded folder?\n\n$targetFolder"
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemoveExcludedFolder(targetFolder)
                            folderToRemove = null
                        }
                    ) {
                        Text(if (language == LanguageManager.LANG_ZH) "删除" else "Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToRemove = null }) {
                        Text(if (language == LanguageManager.LANG_ZH) "取消" else "Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {

        if (showSettings) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(if (language == LanguageManager.LANG_ZH) "音量" else "Volume")
                Slider(
                    value = localVolume,
                    onValueChange = {
                        localVolume = it
                        onSetVolume(it)
                    }
                )

                Text(if (language == LanguageManager.LANG_ZH) "自动播放" else "Auto play")
                Button(onClick = { onSetAutoPlay(!autoPlay) }) {
                    Text(if (autoPlay) "关闭自动播放" else "开启自动播放")
                }

                Text(if (language == LanguageManager.LANG_ZH) "主题色" else "Theme color")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        0xFF4F46E5.toInt(),
                        0xFF06B6D4.toInt(),
                        0xFF10B981.toInt(),
                        0xFFF59E0B.toInt()
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .background(Color(color), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                                .clickable { onSetThemeColor(color) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { onLanguageChange(LanguageManager.LANG_ZH) }) { Text("中文") }
                    Button(onClick = { onLanguageChange(LanguageManager.LANG_EN) }) { Text("English") }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .background(
                    color = if (isDark) Color(0xFF1F1F1F) else Color(0xFFF0F0F0),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = currentSong?.title ?: if (language == LanguageManager.LANG_ZH) "未播放" else "Not playing",
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = currentSong?.artist ?: if (language == LanguageManager.LANG_ZH) "请选择一首歌" else "Select a song",
                    color = if (isDark) Color(0xFFBDBDBD) else Color(0xFF555555)
                )
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { selectedTab = "all" }) { Text(if (language == LanguageManager.LANG_ZH) "全部" else "All") }
            Button(onClick = { selectedTab = "favorite" }) { Text(if (language == LanguageManager.LANG_ZH) "收藏" else "Favorites") }
            Button(onClick = { selectedTab = "recent" }) { Text(if (language == LanguageManager.LANG_ZH) "最近" else "Recent") }
            Button(onClick = { selectedTab = "album"; selectedAlbum = null }) { Text(if (language == LanguageManager.LANG_ZH) "专辑" else "Albums") }
            Button(onClick = { selectedTab = "artist"; selectedArtist = null }) { Text(if (language == LanguageManager.LANG_ZH) "歌手" else "Artists") }
            Button(onClick = { selectedTab = "queue" }) { Text(if (language == LanguageManager.LANG_ZH) "列表" else "Queue") }
//            Button(onClick = { selectedTab = "playlist" }) { Text(if (language == LanguageManager.LANG_ZH) "歌单" else "Playlists") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { onPlayModeChange(PlayMode.LIST_LOOP) }) { Text(if (language == LanguageManager.LANG_ZH) "列表循环" else "List") }
            Button(onClick = { onPlayModeChange(PlayMode.SHUFFLE) }) { Text(if (language == LanguageManager.LANG_ZH) "随机" else "Shuffle") }
            Button(onClick = { onPlayModeChange(PlayMode.SINGLE_LOOP) }) { Text(if (language == LanguageManager.LANG_ZH) "单曲" else "Single") }
        }

        Text(if (language == LanguageManager.LANG_ZH) "当前模式：${playMode.name}" else "Mode: ${playMode.name}")

        if (currentSong != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Slider(
                    value = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()) else 0f,
                    onValueChange = {
                        val target = (it * durationMs).toLong()
                        onSeek(target)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentPositionMs))
                    Text(formatDuration(durationMs))
                }
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (language == LanguageManager.LANG_ZH) "搜索歌曲 / 歌手 / 专辑" else "Search song / artist / album") }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onPreviousClick) { Text(if (language == LanguageManager.LANG_ZH) "上一首" else "Prev") }
            Button(onClick = onPlayPauseClick) { Text(if (language == LanguageManager.LANG_ZH) "播放/暂停" else "Play/Pause") }
            Button(onClick = onNextClick) { Text(if (language == LanguageManager.LANG_ZH) "下一首" else "Next") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = folderInput,
                onValueChange = { folderInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("/storage/emulated/0/Download") }
            )
            Button(
                onClick = {
                    if (folderInput.isNotBlank()) {
                        onAddExcludedFolder(folderInput)
                        folderInput = ""
                    }
                }
            ) {
                Text(if (language == LanguageManager.LANG_ZH) "添加" else "Add")
            }
        }

//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            OutlinedTextField(
//                value = playlistNameInput,
//                onValueChange = { playlistNameInput = it },
//                modifier = Modifier.weight(1f),
//                placeholder = { Text(if (language == LanguageManager.LANG_ZH) "新歌单名称" else "New playlist name") }
//            )
//            Button(
//                onClick = {
//                    if (playlistNameInput.isNotBlank()) {
//                        onCreatePlaylist(playlistNameInput)
//                        playlistNameInput = ""
//                    }
//                }
//            ) {
//                Text(if (language == LanguageManager.LANG_ZH) "创建" else "Create")
//            }
//        }

        if (excludedFolders.isNotEmpty()) {
            Text(
                text = if (language == LanguageManager.LANG_ZH) "已排除目录" else "Excluded folders",
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                excludedFolders.forEachIndexed { _, folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = folder,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Button(
                            onClick = { folderToRemove = folder },
                            modifier = Modifier.align(Alignment.Top)
                        ) {
                            Text(if (language == LanguageManager.LANG_ZH) "删除" else "Remove")
                        }
                    }
                }
            }
        }

        Text(
            text = if (language == LanguageManager.LANG_ZH) "歌曲 (${listToShow.size})" else "Songs (${listToShow.size})",
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        if (selectedTab == "playlist") {
            if (playlists.isEmpty()) {
                Text(if (language == LanguageManager.LANG_ZH) "暂无歌单" else "No playlists yet")
            } else {
                Column {
                    playlists.forEachIndexed { _, playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { selectedPlaylistName = playlist.name },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(playlist.name)
                            Button(onClick = { onDeletePlaylist(playlist.name) }) {
                                Text(if (language == LanguageManager.LANG_ZH) "删除" else "Delete")
                            }
                        }
                    }
                }
            }
            return
        }

        if (selectedTab == "album" && selectedAlbum == null) {
            Column {
                albums.forEachIndexed { _, album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { selectedAlbum = album.name },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(album.name)
                            Text(album.artist)
                        }
                        Text("${album.count} ${if (language == LanguageManager.LANG_ZH) "首" else "songs"}")
                    }
                }
            }
            return
        }

        if (selectedTab == "artist" && selectedArtist == null) {
            Column {
                artists.forEachIndexed { _, artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { selectedArtist = artist.name },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(artist.name)
                        Text("${artist.count} ${if (language == LanguageManager.LANG_ZH) "首" else "tracks"}")
                    }
                }
            }
            return
        }

        if (selectedTab == "queue") {
            Column {
                queue.forEachIndexed { index, song ->
                    val isPlaying = song.id == currentSong?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isPlaying) playingHighlight else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .clickable { onSongClick(song) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = song.title,
                                color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                fontWeight = if (isPlaying) FontWeight.Bold else null
                            )
                            Text(song.artist)
                        }
                        Row {
                            Button(onClick = { onReorderQueue(index, index - 1) }) { Text("↑") }
                            Button(onClick = { onReorderQueue(index, index + 1) }) { Text("↓") }
                            Button(onClick = { onRemoveFromQueue(song.id) }) { Text("X") }
                            Text(formatDuration(song.duration))
                        }
                    }
                }
            }
            return
        }

        Column {
            listToShow.forEachIndexed { _, song ->
                if (song.id == -1L) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                if (selectedTab == "album") selectedAlbum = song.album
                                if (selectedTab == "artist") selectedArtist = song.artist
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(song.title)
                            Text(song.artist)
                        }
                        Text(if (language == LanguageManager.LANG_ZH) "查看" else "Open")
                    }
                    return@forEachIndexed
                }

                val isPlaying = song.id == currentSong?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isPlaying) playingHighlight else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSongClick(song) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = song.title,
                                color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                fontWeight = if (isPlaying) FontWeight.Bold else null
                            )
                            Text(song.artist)
                            Text(
                                text = song.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color(0xFFBDBDBD) else Color(0xFF555555)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (song.duration > 0L) {
                            Text(formatDuration(song.duration))
                        }

                        if (selectedPlaylistName != null) {
                            Button(
                                onClick = { onAddToPlaylist(selectedPlaylistName!!, song) }
                            ) {
                                Text(if (language == LanguageManager.LANG_ZH) "加入歌单" else "Add")
                            }
                        }

                        Button(
                            onClick = { onToggleFavorite(song) },
                            modifier = Modifier.width(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (favoriteIds.contains(song.id)) "♥" else "♡")
                        }
                    }
                }
            }
        }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%02d:%02d", minutes, seconds)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    private var musicService: MusicService? = null
    private var isBound = false
    private var pendingServiceAction: ((MusicService) -> Unit)? = null

    private val requiredAudioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.refreshSongs()
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicService.LocalBinder ?: return
            val connected = binder.getService()
            musicService = connected
            connected.setPlayMode(viewModel.playMode.value)
            pendingServiceAction?.invoke(connected)
            pendingServiceAction = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.apply(this)

        if (hasAudioPermission()) {
            viewModel.refreshSongs()
        } else {
            permissionLauncher.launch(requiredAudioPermission)
        }
        viewModel.refreshFavorites()
        viewModel.refreshRecent()

        setContent {
            val songs by viewModel.songs.collectAsStateWithLifecycle()
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            val artists by viewModel.artists.collectAsStateWithLifecycle()
            val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
            val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
            val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()
            val searchText by viewModel.searchText.collectAsStateWithLifecycle()
            val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
            val isDark by viewModel.isDark.collectAsStateWithLifecycle()
            val queue by viewModel.queue.collectAsStateWithLifecycle()
            val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
            val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
            val playMode by viewModel.playMode.collectAsStateWithLifecycle()
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            val volume by viewModel.volume.collectAsStateWithLifecycle()
            val autoPlay by viewModel.autoPlay.collectAsStateWithLifecycle()
            val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()

            val colorScheme = if (isDark) {
                darkColorScheme(primary = Color(themeColor))
            } else {
                lightColorScheme(primary = Color(themeColor))
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    YMusicScreen(
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        currentSong = currentSong,
                        favoriteIds = favoriteIds,
                        recentSongs = recentSongs,
                        searchText = searchText,
                        excludedFolders = excludedFolders,
                        isDark = isDark,
                        queue = queue,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        playMode = playMode,
                        playlists = playlists,
                        volume = volume,
                        autoPlay = autoPlay,
                        themeColor = themeColor,
                        language = language,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onSetThemeColor = { viewModel.setThemeColor(it) },
                        onSetVolume = {
                            viewModel.setVolume(it)
                            withService { service -> service.setVolume(it) }
                        },
                        onSetAutoPlay = { viewModel.setAutoPlay(it) },
                        onSearchChange = { viewModel.setSearchText(it) },
                        onScanClick = {
                            if (hasAudioPermission()) {
                                viewModel.refreshSongs()
                            } else {
                                permissionLauncher.launch(requiredAudioPermission)
                            }
                        },
                        onAddExcludedFolder = { viewModel.addExcludedFolder(it) },
                        onRemoveExcludedFolder = { viewModel.removeExcludedFolder(it) },
                        onSongClick = { song -> playSong(song) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onAddToPlaylist = { name, song -> viewModel.addSongToPlaylist(name, song) },
                        onPlayPauseClick = { withService { it.togglePlayPause() } },
                        onNextClick = {
                            withService { service ->
                                service.playNext()
                                val next = service.getCurrentSong()
                                viewModel.setCurrentSong(next)
                                next?.let { viewModel.markRecentPlay(it) }
                            }
                        },
                        onPreviousClick = {
                            withService { service ->
                                service.playPrevious()
                                val previous = service.getCurrentSong()
                                viewModel.setCurrentSong(previous)
                                previous?.let { viewModel.markRecentPlay(it) }
                            }
                        },
                        onSeek = { position -> withService { it.seekTo(position) } },
                        onCreatePlaylist = { name -> viewModel.createPlaylist(name, emptyList()) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onSortQueue = { viewModel.sortQueueByTitle() },
                        onRemoveFromQueue = { viewModel.removeSongFromQueue(it) },
                        onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
                        onPlayModeChange = { mode ->
                            viewModel.setPlayMode(mode)
                            withService { it.setPlayMode(mode) }
                        },
                        onLanguageChange = { viewModel.setLanguage(it) }
                    )
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    musicService?.let { service ->
                        val position = service.getCurrentPositionMs().coerceAtLeast(0L)
                        val duration = service.getDurationMs().coerceAtLeast(0L)
                        viewModel.setProgress(position, duration)
                        val playing = service.getCurrentSong()
                        if (playing != null && playing.id != viewModel.currentSong.value?.id) {
                            viewModel.setCurrentSong(playing)
                        }
                    }
                    delay(500L)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindMusicService()
    }

    override fun onStop() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        musicService = null
        super.onStop()
    }

    private fun bindMusicService() {
        if (isBound) return
        val intent = Intent(this, MusicService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            requiredAudioPermission
        ) == PackageManager.PERMISSION_GRANTED

    private fun withService(action: (MusicService) -> Unit) {
        val service = musicService
        if (service != null) {
            action(service)
        } else {
            pendingServiceAction = action
            bindMusicService()
        }
    }

    private fun playSong(song: Song) {
        if (song.id < 0L) return
        val list = viewModel.songs.value
        val index = list.indexOfFirst { it.id == song.id }
        if (index < 0) return

        viewModel.setQueue(list)
        viewModel.setCurrentSong(song)
        viewModel.markRecentPlay(song)
        withService { it.setQueue(list, index) }
    }
}
