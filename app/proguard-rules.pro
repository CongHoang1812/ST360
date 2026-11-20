# R8 cho Ranking Filter. Thư viện (Room, kotlinx-serialization, CameraX, ML Kit, Media3, Coil)
# đã tự kèm luật consumer; file này chỉ thêm những gì app tự dùng.

# Giữ số dòng trong stack trace của Crashlytics / Logcat (mapping.txt dùng để dịch ngược).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx-serialization: FilterDto đọc từ assets/filters.json bằng serializer sinh sẵn.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.funny.rank.filters.data.**$$serializer { *; }
-keepclassmembers class com.funny.rank.filters.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.funny.rank.filters.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room không cần luật riêng: code sinh bởi KSP gọi thẳng vào entity/DAO, luật của thư viện đủ.
