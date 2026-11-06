# Signal & GNSS Field Logger - ProGuard / R8 Rules

# Preserve Room database entities and DAOs
-keep class com.example.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Preserve osmdroid OpenStreetMap classes
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep models for export and reflection
-keep class com.example.model.** { *; }
