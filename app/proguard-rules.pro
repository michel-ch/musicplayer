# Music Player ProGuard Rules

# Keep Media3 classes
-keep class androidx.media3.** { *; }

# Keep JAudioTagger
-keep class org.jaudiotagger.** { *; }

# jAudioTagger references Java desktop APIs that don't exist on Android — suppress
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# Keep Room entities
-keep class com.musicplayer.app.data.local.db.** { *; }
