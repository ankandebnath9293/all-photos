# Library consumer rules cover most of what this app uses.
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.errorprone.annotations.**

# WorkManager builds its Room database and workers by reflection; full-mode R8 removes the
# no-argument constructors unless they are kept.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger { public <init>(); }

# ML Kit finds its on-device models and components by name.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-dontwarn com.google.mlkit.**
