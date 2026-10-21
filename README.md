# YMusic

YMusic 是一个 Android 本地音乐播放器项目，涵盖了从基础本地音频扫描到完整播放体验的一系列迭代版本，最终版本 v19 已具备中英文切换、播放列表管理、专辑/歌手页、音乐播放状态恢复、自动播放、音量控制等能力。

## 项目概览

该项目目前基于 Android Jetpack Compose + Media3 + Room 构建，具备以下特性：

- 本地音乐扫描与加载
- 搜索歌曲 / 歌手 / 专辑
- 收藏与最近播放
- 专辑页与歌手页
- 播放列表与歌单管理
- 播放控制（上一首 / 播放 / 暂停 / 下一首）
- 进度条与进度恢复
- 播放模式：列表循环 / 随机 / 单曲循环
- 排除目录过滤
- 断点播放恢复
- 设置页（音量、自动播放、主题色、深浅模式）
- 中英文语言切换
- MediaSession / Foreground Service 基础支持

## 项目结构

```text
YMusic/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ AndroidManifest.xml
│  │  │  ├─ java/com/example/musicplayer/
│  │  │  │  ├─ AlbumInfo.kt
│  │  │  │  ├─ AlbumArtManager.kt
│  │  │  │  ├─ ArtistInfo.kt
│  │  │  │  ├─ LanguageManager.kt
│  │  │  │  ├─ LocalStateManager.kt
│  │  │  │  ├─ MainActivity.kt
│  │  │  │  ├─ PlayMode.kt
│  │  │  │  ├─ Playlist.kt
│  │  │  │  ├─ PlaylistManager.kt
│  │  │  │  ├─ SettingsManager.kt
│  │  │  │  ├─ UriExt.kt
│  │  │  │  ├─ data/
│  │  │  │  │  ├─ ExcludedFolder.kt
│  │  │  │  │  ├─ ExcludedFolderDao.kt
│  │  │  │  │  ├─ FavoriteSong.kt
│  │  │  │  │  ├─ FavoriteSongDao.kt
│  │  │  │  │  ├─ RecentSong.kt
│  │  │  │  │  ├─ RecentSongDao.kt
│  │  │  │  │  ├─ Song.kt
│  │  │  │  │  ├─ SongDao.kt
│  │  │  │  │  ├─ SongDatabase.kt
│  │  │  │  │  └─ SongRepository.kt
│  │  │  │  ├─ service/
│  │  │  │  │  └─ MusicService.kt
│  │  │  │  └─ ui/
│  │  │  │     └─ MusicViewModel.kt
│  │  │  └─ res/
│  │  │     └─ values/
│  │  │        └─ strings.xml
│  ├─ build.gradle.kts
│  └─ proguard-rules.pro
├─ build.gradle.kts
├─ gradle.properties
├─ settings.gradle.kts
├─ .gitignore
├─ README.md
└─ LICENSE (可选)
```

## 版本迭代记录

以下是从 v1 到 v19 版本的功能演进历史，展示了项目如何从基础播放器逐步成长为具备本地音乐播放器核心能力的 Android App。

### v1
- 基础音乐扫描
- 歌曲列表展示
- 播放器最基础模型

### v2
- 搜索功能加持
- 歌曲筛选与查询
- 本地文件流式展示

### v3
- 收藏功能
- 最近播放记录
- 主题浅深模式

### v4
- 扫描后本地文件持久化
- 排除目录功能
- 专辑页初步构建

### v5
- 继续完善播放页
- 增强播放控制
- 新增播放队列基础能力

### v6
- 歌单页（playlist page）
- 收藏歌单/播放列表展示
- 对播放列表进行基本管理

### v7
- 播放进度展示
- 当前播放时间与总时长管理
- 更完整的服务与播放器状态同步

### v8
- 播放列表（Queue）增强
- 当前播放进度条
- 播放页大卡片化
- 歌单页与播放列表收藏支持
- 保留搜索 / 扫描 / 收藏 / 最近播放 / 排除目录 / 专辑页 / 主题切换

### v9
- 歌单管理：创建、删除、保存歌单
- 随机播放 / 列表循环 / 单曲循环
- 断点续播（记录上次播放位置）
- 保留核心音乐应用能力

### v10
- 专辑封面 / 图标展示方案
- 歌单支持添加和删除歌曲
- 进度条与 Service 同步
- 更稳定的后台播放
- 车载/Android Auto 基础增强

### v11
- 恢复上次播放歌曲和进度
- 播放模式完整落地
- MainActivity 自动恢复之前暂停位置
- Service 统一处理 queue / mode / progress
- 继续保留搜索 / 扫描 / 收藏 / 最近播放等能力

### v12
- 多功能整合版本
- 本地音乐扫描、搜索、收藏、最近播放
- 专辑页、歌单、播放列表、断点恢复
- 播放模式：列表循环 / 随机 / 单曲循环
- 排除目录
- 主题切换
- 车载/Android Auto 基础兼容

### v13
- 按歌手分组
- 专辑封面读取（MediaMetadataRetriever）
- 歌单点击直接播放
- 歌单中的歌曲操作增强

### v14
- 播放列表排序与删除
- 音量控制
- 设置页
- 深浅模式与主题配置
- 自动播放开关

### v15
- 播放列表拖动式重新排序
- 队列中歌曲删除与排序增强
- 播放列表管理更稳定

### v16
- 真实专辑封面支持
- 画廊式封面 / 大图播放器设计方向
- 大图播放卡片准备

### v17
- 全屏播放器页
- 当前播放区域设计
- 大播放器控制区增强

### v18
- 设置页增强
- 音量设置
- 深浅主题切换
- 主题色选择
- 自动播放管理

### v19
- 中英文语言切换
- 设置中支持中文 / English 切换
- 语言设置会持续保存
- 界面按语言即时更新
- 保持 v12-v18 所有功能稳定

## 当前状态

当前仓库中已落地 YMusic 的 v19 版本结构与核心工程代码，具备：

- Android Compose UI
- Media3 / ExoPlayer 播放器
- Room 数据持久化
- 本地音乐扫描
- 收藏、最近播放、歌单、播放队列
- 断点恢复与播放模式
- 设置与中英文语言切换

## 快速构建

在 Android Studio 中打开项目，或在终端中执行：

```bash
./gradlew assembleRelease
```

生成的 APK 文件会位于：

```text
app/build/outputs/apk/release/
```

## 发布与下载

如需发布 APK，可将生成的 release 文件上传到：

- GitHub Releases
- Android 应用分发平台
- 自定义静态下载页

## 说明

这是一个学习与工程迭代型音乐播放器项目，主要用于演示：

- Android 本地音乐播放实现
- Media3 与前台服务使用
- 数据持久化设计
- Compose UI 组织方式
- 逐步迭代的播放器能力扩展

适合�� Android 音乐播放器原型、增强学习、演示项目或继续扩展为正式产品。
