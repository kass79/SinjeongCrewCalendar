# Firestore model classes (reflection-based mapping)
-keepclassmembers class com.sinjeong.crewcalendar.data.remote.dto.** { *; }
-keep class com.sinjeong.crewcalendar.domain.model.** { *; }

# Google API client
-dontwarn com.google.api.client.**
-dontwarn org.apache.**
-keep class com.google.api.services.calendar.** { *; }
