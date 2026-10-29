package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    isPlaying: Boolean,
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
    showAlbumArt: Boolean,
    showPath: Boolean,
    themeColor: Int,
    language: String,
    onToggleTheme: () -> Unit,
    onSetThemeColor: (Int) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetAutoPlay: (Boolean) -> Unit,
    onSetShowAlbumArt: (Boolean) -> Unit,
    onSetShowPath: (Boolean) -> Unit,
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
    var excludedExpanded by remember { mutableStateOf(true) }

    val playingHighlight =
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f)

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showAboutDialog = true }
            ) {
                Image(
                    painter = painterResource(R.drawable.icon_king),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Y³Music",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Row {
                ActionIconButton(
                    drawableRes = if (isDark) R.drawable.icon_day_day else R.drawable.icon_night,
                    contentDescription = if (isDark) {
                        if (language == LanguageManager.LANG_ZH) "切换到浅色" else "Switch to light"
                    } else {
                        if (language == LanguageManager.LANG_ZH) "切换到深色" else "Switch to dark"
                    },
                    onClick = onToggleTheme,
                    iconSize = if (isDark) 40.dp else 24.dp,
                    iconTint = Color.White
                )
                Spacer(modifier = Modifier.width(3.dp))
                ActionIconButton(
                    drawableRes = R.drawable.icon_scan,
                    contentDescription = if (language == LanguageManager.LANG_ZH) "扫描" else "Scan",
                    onClick = onScanClick,
                    iconSize = 24.dp,
                    iconTint = Color.White
                )
                Spacer(modifier = Modifier.width(3.dp))
                ActionIconButton(
                    drawableRes = R.drawable.icon_settings,
                    contentDescription = if (language == LanguageManager.LANG_ZH) "设置" else "Settings",
                    onClick = { showSettings = !showSettings },
                    iconSize = 24.dp,
                    iconTint = Color.White
                )
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
                        Text(
                            if (autoPlay) {
                                if (language == LanguageManager.LANG_ZH) "关闭自动播放" else "Turn off autoplay"
                            } else {
                                if (language == LanguageManager.LANG_ZH) "开启自动播放" else "Turn on autoplay"
                            }
                        )
                    }

                    Text(if (language == LanguageManager.LANG_ZH) "专辑图片" else "Album art")
                    Button(onClick = { onSetShowAlbumArt(!showAlbumArt) }) {
                        Text(
                            if (showAlbumArt) {
                                if (language == LanguageManager.LANG_ZH) "隐藏专辑图片" else "Hide album art"
                            } else {
                                if (language == LanguageManager.LANG_ZH) "显示专辑图片" else "Show album art"
                            }
                        )
                    }

                    Text(if (language == LanguageManager.LANG_ZH) "歌曲路径" else "Song path")
                    Button(onClick = { onSetShowPath(!showPath) }) {
                        Text(
                            if (showPath) {
                                if (language == LanguageManager.LANG_ZH) "隐藏歌曲路径" else "Hide song path"
                            } else {
                                if (language == LanguageManager.LANG_ZH) "显示歌曲路径" else "Show song path"
                            }
                        )
                    }

                    Text(if (language == LanguageManager.LANG_ZH) "主题色" else "Theme color")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            0xFFEF4444.toInt(),
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
                        text = currentSong?.title
                            ?: if (language == LanguageManager.LANG_ZH) "未播放" else "Not playing",
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentSong?.artist
                            ?: if (language == LanguageManager.LANG_ZH) "请选择一首歌" else "Select a song",
                        color = if (isDark) Color(0xFFBDBDBD) else Color(0xFF555555),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabButton(
                    text = if (language == LanguageManager.LANG_ZH) "全部" else "All",
                    selected = selectedTab == "all",
                    onClick = { selectedTab = "all" }
                )
                TabButton(
                    text = if (language == LanguageManager.LANG_ZH) "收藏" else "Favorites",
                    selected = selectedTab == "favorite",
                    onClick = { selectedTab = "favorite" }
                )
//                Button(onClick = {
//                    selectedTab = "recent"
//                }) { Text(if (language == LanguageManager.LANG_ZH) "最近" else "Recent") }
                TabButton(
                    text = if (language == LanguageManager.LANG_ZH) "专辑" else "Albums",
                    selected = selectedTab == "album",
                    onClick = { selectedTab = "album"; selectedAlbum = null }
                )
                TabButton(
                    text = if (language == LanguageManager.LANG_ZH) "歌手" else "Artists",
                    selected = selectedTab == "artist",
                    onClick = { selectedTab = "artist"; selectedArtist = null }
                )
//                Button(onClick = {
//                    selectedTab = "queue"
//                }) { Text(if (language == LanguageManager.LANG_ZH) "列表" else "Queue") }
//            Button(onClick = { selectedTab = "playlist" }) { Text(if (language == LanguageManager.LANG_ZH) "歌单" else "Playlists") }
            }

//            Text(if (language == LanguageManager.LANG_ZH) "当前模式：${playMode.name}" else "Mode: ${playMode.name}")

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (language == LanguageManager.LANG_ZH) "搜索歌曲 / 歌手 / 专辑" else "Search song / artist / album") }
            )

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionIconButton(
                        drawableRes = R.drawable.icon_pre_normal,
                        pressedDrawableRes = R.drawable.icon_pre_pressed,
                        contentDescription = if (language == LanguageManager.LANG_ZH) "上一首" else "Previous",
                        onClick = onPreviousClick,
                        buttonSize = 48.dp,
                        iconSize = 48.dp
                    )
                    ActionIconButton(
                        drawableRes = if (isPlaying) R.drawable.icon_pause_normal else R.drawable.icon_play_normal,
                        pressedDrawableRes = if (isPlaying) R.drawable.icon_pause_pressed else R.drawable.icon_play_pressed,
                        contentDescription = if (language == LanguageManager.LANG_ZH) {
                            if (isPlaying) "暂停" else "播放"
                        } else {
                            if (isPlaying) "Pause" else "Play"
                        },
                        onClick = onPlayPauseClick,
                        buttonSize = 56.dp,
                        iconSize = 48.dp
                    )
                    ActionIconButton(
                        drawableRes = R.drawable.icon_next_normal,
                        pressedDrawableRes = R.drawable.icon_next_pressed,
                        contentDescription = if (language == LanguageManager.LANG_ZH) "下一首" else "Next",
                        onClick = onNextClick,
                        buttonSize = 48.dp,
                        iconSize = 48.dp
                    )
                }
                ActionIconButton(
                    drawableRes = when (playMode) {
                        PlayMode.LIST_LOOP -> R.drawable.icon_mode_list_loop_normal
                        PlayMode.SHUFFLE -> R.drawable.icon_mode_shuffle_normal
                        PlayMode.SINGLE_LOOP -> R.drawable.icon_mode_single_loop_normal
                    },
                    pressedDrawableRes = when (playMode) {
                        PlayMode.LIST_LOOP -> R.drawable.icon_mode_list_loop_pressed
                        PlayMode.SHUFFLE -> R.drawable.icon_mode_shuffle_pressed
                        PlayMode.SINGLE_LOOP -> R.drawable.icon_mode_single_loop_pressed
                    },
                    contentDescription = when (playMode) {
                        PlayMode.LIST_LOOP -> if (language == LanguageManager.LANG_ZH) "列表循环" else "List loop"
                        PlayMode.SHUFFLE -> if (language == LanguageManager.LANG_ZH) "随机" else "Shuffle"
                        PlayMode.SINGLE_LOOP -> if (language == LanguageManager.LANG_ZH) "单曲" else "Single loop"
                    },
                    onClick = {
                        val next = when (playMode) {
                            PlayMode.LIST_LOOP -> PlayMode.SHUFFLE
                            PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                            PlayMode.SINGLE_LOOP -> PlayMode.LIST_LOOP
                        }
                        onPlayModeChange(next)
                    },
                    buttonSize = 24.dp,
                    iconSize = 16.dp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
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
                Spacer(modifier = Modifier.width(3.dp))
                ActionIconButton(
                    drawableRes = R.drawable.icon_add,
                    contentDescription = if (language == LanguageManager.LANG_ZH) "添加" else "Add",
                    onClick = {
                        if (folderInput.isNotBlank()) {
                            onAddExcludedFolder(folderInput)
                            folderInput = ""
                        }
                    },
                    buttonSize = 40.dp,
                    iconSize = 18.dp,
                    iconTint = Color.White
                )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp)
                        .clickable { excludedExpanded = !excludedExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (if (excludedExpanded) "▾ " else "▸ ") +
                                if (language == LanguageManager.LANG_ZH) "已排除目录" else "Excluded folders",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (excludedExpanded) {
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
                                ActionIconButton(
                                    drawableRes = R.drawable.icon_remove,
                                    contentDescription = if (language == LanguageManager.LANG_ZH) "删除" else "Remove",
                                    onClick = { folderToRemove = folder },
                                    modifier = Modifier.align(Alignment.Top)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = if (language == LanguageManager.LANG_ZH) "歌曲 (${listToShow.size})" else "Songs (${listToShow.size})",
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                fontWeight = FontWeight.Bold
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
                                ActionIconButton(
                                    drawableRes = R.drawable.icon_remove,
                                    contentDescription = if (language == LanguageManager.LANG_ZH) "删除" else "Delete",
                                    onClick = { onDeletePlaylist(playlist.name) }
                                )
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
                                ActionIconButton(
                                    drawableRes = R.drawable.icon_remove,
                                    contentDescription = if (language == LanguageManager.LANG_ZH) "从列表移除" else "Remove from queue",
                                    onClick = { onRemoveFromQueue(song.id) }
                                )
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
                            if (showAlbumArt) {
                                SongAlbumArt(song = song, size = 48.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = song.title,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(song.artist)
                                if (showPath) {
                                    Text(
                                        text = song.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color(0xFFBDBDBD) else Color(0xFF555555)
                                    )
                                }
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

                            Spacer(modifier = Modifier.width(3.dp))
                            val isFavorite = favoriteIds.contains(song.id)
                            ActionIconButton(
                                drawableRes = if (isFavorite) {
                                    R.drawable.icon_add_favorite
                                } else {
                                    R.drawable.icon_delete_favorite
                                },
                                contentDescription = when {
                                    isFavorite && language == LanguageManager.LANG_ZH -> "取消收藏"
                                    isFavorite -> "Remove from favorites"
                                    language == LanguageManager.LANG_ZH -> "收藏"
                                    else -> "Add to favorites"
                                },
                                onClick = { onToggleFavorite(song) },
                                buttonSize = 40.dp,
                                iconSize = 28.dp,
                                iconTint = if (isFavorite) Color.Red else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongAlbumArt(song: Song, size: Dp = 48.dp) {
    val context = LocalContext.current
    val targetSizePx = with(LocalDensity.current) { size.roundToPx() }
    val artwork by produceState<Bitmap?>(
        initialValue = null,
        song.id,
        song.uri,
        song.albumArtUri
    ) {
        value = AlbumArtLoader.load(context, song, targetSizePx)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = artwork
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = song.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "♪",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionIconButton(
    drawableRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    iconTint: Color? = null,
    pressedDrawableRes: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconRes = if (pressed && pressedDrawableRes != null) pressedDrawableRes else drawableRes
    Button(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            colorFilter = iconTint?.let { ColorFilter.tint(it) }
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Text(
            text = text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
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
            val showAlbumArt by viewModel.showAlbumArt.collectAsStateWithLifecycle()
            val showPath by viewModel.showPath.collectAsStateWithLifecycle()
            val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

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
                        isPlaying = isPlaying,
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
                        showAlbumArt = showAlbumArt,
                        showPath = showPath,
                        themeColor = themeColor,
                        language = language,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onSetThemeColor = { viewModel.setThemeColor(it) },
                        onSetVolume = {
                            viewModel.setVolume(it)
                            withService { service -> service.setVolume(it) }
                        },
                        onSetAutoPlay = { viewModel.setAutoPlay(it) },
                        onSetShowAlbumArt = { viewModel.setShowAlbumArt(it) },
                        onSetShowPath = { viewModel.setShowPath(it) },
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
                        onPlayPauseClick = {
                            withService { service ->
                                service.togglePlayPause()
                                viewModel.setIsPlaying(service.isPlaying())
                            }
                        },
                        onNextClick = {
                            withService { service ->
                                service.playNext()
                                val next = service.getCurrentSong()
                                viewModel.setCurrentSong(next)
                                viewModel.setIsPlaying(true)
                                next?.let { viewModel.markRecentPlay(it) }
                            }
                        },
                        onPreviousClick = {
                            withService { service ->
                                service.playPrevious()
                                val previous = service.getCurrentSong()
                                viewModel.setCurrentSong(previous)
                                viewModel.setIsPlaying(true)
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
                        viewModel.setIsPlaying(service.isPlaying())
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
