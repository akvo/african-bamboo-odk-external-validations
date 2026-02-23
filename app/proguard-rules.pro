# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.akvo.afribamodkvalidator.**$$serializer { *; }
-keepclassmembers class org.akvo.afribamodkvalidator.** {
    *** Companion;
}
-keepclasseswithmembers class org.akvo.afribamodkvalidator.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable data classes and their serializers
-keep @kotlinx.serialization.Serializable class org.akvo.afribamodkvalidator.** { *; }
-keep class org.akvo.afribamodkvalidator.data.dto.** { *; }
-keep class org.akvo.afribamodkvalidator.data.model.** { *; }

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep Retrofit service interfaces
-keep interface org.akvo.afribamodkvalidator.data.network.KoboApiService { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }
-keep class org.akvo.afribamodkvalidator.data.entity.** { *; }
-keep class org.akvo.afribamodkvalidator.data.dao.** { *; }
-keep class org.akvo.afribamodkvalidator.data.database.** { *; }
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.InstallIn class *
-keep @dagger.Module class *

# Mapbox
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# JTS (geometry library)
-dontwarn org.locationtech.jts.**
-keep class org.locationtech.jts.** { *; }

# Keep network interceptors (used via reflection by OkHttp/Hilt)
-keep class org.akvo.afribamodkvalidator.data.network.** { *; }

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
