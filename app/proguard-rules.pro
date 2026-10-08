# Get RBX - Counter &amp; Calc — R8 / ProGuard
# Wired from app/build.gradle.kts release proguardFiles.
# Covers ads, Room launcher DB, XML custom views, Firebase, OneSignal, Facebook, Play.

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Crashlytics needs exception types and line numbers.
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-dontwarn kotlin.**
-dontwarn kotlin.reflect.**

# ViewBinding (funnel / downloader / launcher layouts)
-keep class com.example.ffdiamond.databinding.** { *; }

# Manifest components — AGP keeps these; names stay for HOME / funnel routing.
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.appwidget.AppWidgetProvider
-keep public class * extends android.service.notification.NotificationListenerService

# XML custom views (workspace, drawer, spin, scratch)
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}
-keep class com.example.ffdiamond.ui.workspace.** { *; }
-keep class com.example.ffdiamond.ui.drag.** { *; }
-keep class com.example.ffdiamond.ui.drawer.** { *; }
-keep class com.example.ffdiamond.ui.folder.** { *; }
-keep class com.example.ffdiamond.downloader.SpinWheelView { *; }
-keep class com.example.ffdiamond.downloader.ScratchView { *; }
-keep class com.example.ffdiamond.widget.** { *; }

# Room: entities, DAOs, generated impl, enum names stored as TEXT
-keep class com.example.ffdiamond.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**
-keepclassmembers enum com.example.ffdiamond.model.Container { *; }
-keepclassmembers enum com.example.ffdiamond.model.HomeItemType { *; }
-keepclassmembers enum com.example.ffdiamond.funnel.FunnelStep { *; }
-keepclassmembers enum com.example.ffdiamond.ads.InstallKind { *; }
-keepclassmembers enum com.example.ffdiamond.ads.FullscreenResult { *; }
-keepclassmembers enum com.example.ffdiamond.downloader.ConvertKind { *; }
-keepclassmembers enum com.example.ffdiamond.onboarding.OnboardingPage { *; }

# DataStore / preferences keys
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Ads engine + AdMob native layouts (findViewById + NativeAdView)
-keep class com.example.ffdiamond.ads.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.cloudmessaging.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.ads.**

# Play Install Referrer (paid vs organic)
-keep class com.android.installreferrer.** { *; }
-dontwarn com.android.installreferrer.**

# Firebase Analytics + Firestore remote config
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.measurement.**

# OneSignal + FCM
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.messaging.**

# Facebook install / App Events
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**

# Custom Tabs (web ads / Play Game / feed)
-keep class androidx.browser.** { *; }
-dontwarn androidx.browser.**

# Coroutines (OneSignal / ads binders)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Material / AppCompat
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Optional / hidden APIs used only via Class.forName
-dontwarn android.os.ServiceManager
-dontwarn android.app.IWallpaperManager
-dontwarn android.app.IWallpaperManager$Stub
-dontwarn dalvik.system.VMRuntime
