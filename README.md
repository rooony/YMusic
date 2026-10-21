# YMusic

A local music player app for Android with:
- local file scanning
- search
- favorites
- recent plays
- albums and artists
- playlists
- queue management
- playback state restore
- language switching (Chinese / English)
- Android Auto / media session compatibility foundation

## Project structure

- app/
  - src/main/java/com/example/musicplayer/
  - src/main/res/

## Notes

This project is designed for Android Studio and Gradle. To build a release APK, run:

```bash
./gradlew assembleRelease
```

The APK will be generated under:

```text
app/build/outputs/apk/release/
```
