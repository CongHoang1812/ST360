# Ranking Filter (Android)

App quay video có filter xếp hạng: ảnh đổi ngẫu nhiên trên đầu người dùng, người dùng chạm ô 1–12 để chốt hạng.

## Tài liệu chính

- Sổ tay (10 giai đoạn, prompt từng bước, Figma node, code RankingGame ở Phụ lục):
  https://claude.ai/artifact/7ccb3dff-347a-41b0-b300-acce02fd0f37
  Đọc bằng Artifact tool (`action: "read"`). Làm theo đúng thứ tự giai đoạn, mỗi lần một việc.
- Figma: file `joLKfYJ3vbxrB4wiDoFnE2`, trang UI (màn hình) và trang data (30 chủ đề).
- GitHub: `git@github.com:CongHoang1812/ST360.git`, nhánh `main`.

## Tiến độ

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 0 | Khởi tạo project, dependency, Navigation | Xong (build OK, chưa thử trên máy thật) |
| 1 | Theme, font, dữ liệu 30 chủ đề | **Tiếp theo** |
| 2 | Home | Chưa |
| 3 | Choose a Filter | Chưa |
| 4 | Make a Filter + Room (thêm Room + KSP ở đây) | Chưa |
| 5 | Camera (5a → 5d) | Chưa |
| 6 | Add Sound, Preview, Successful | Chưa |
| 7 | Gallery | Chưa |
| 8 | Splash, Language, Intro, Permission, Setting | Chưa |
| 9 | Hoàn thiện, release | Chưa |

Giai đoạn 1 cần người dùng chuẩn bị trước:
- Font vào `app/src/main/res/font/`: `londrina_solid_regular.ttf`, `londrina_solid_light.ttf`, `roboto_regular.ttf`, `roboto_medium.ttf`.
- Chạy `export_figma_topics.py` (người dùng giữ file này, không có trong repo) rồi chép `out/assets/*` vào `app/src/main/assets/`.

Sau mỗi giai đoạn: cập nhật bảng trên, commit, push.

## Quyết định kỹ thuật đã chốt

- Kotlin + XML Views (không Compose), ViewBinding, minSdk 24, compile/targetSdk 37, Java 17.
- Package / applicationId: `com.example.rankingfilter`.
- AGP 9.5.1 có Kotlin tích hợp: KHÔNG apply plugin `kotlin-android` trong `app/build.gradle.kts`.
  Kotlin 2.4.21 được ghim bằng `apply false` ở `build.gradle.kts` gốc; serialization plugin apply ở module app.
- Phiên bản thư viện nằm trong `gradle/libs.versions.toml`. Room + KSP chưa thêm (giai đoạn 4; KSP phải khớp Kotlin).
- Coil 3 (`io.coil-kt.coil3`) kèm `coil-network-okhttp`.
- Mọi chữ hiển thị nằm trong `strings.xml` (6 ngôn ngữ: en, es, fr, hi, pt, in). Tên 30 chủ đề giữ tiếng Anh.
- Mọi filter có đúng 12 ảnh → Camera luôn 12 ô.
- Không có quảng cáo (bỏ AdMob, bỏ dòng "This action can contain ads").
- Video Trending trên server, đọc qua `config.json`; nhạc 74 bài trong `assets/music/`.

## Build

```
export JAVA_HOME=/snap/android-studio/current/jbr   # hoặc JBR của Android Studio trên máy đó
./gradlew :app:assembleDebug
./gradlew :app:installDebug                         # cài lên máy thật đang cắm USB
```

Cấu trúc hiện tại:
- `MainActivity` chứa `NavHostFragment` (`res/layout/activity_main.xml`, `res/navigation/nav_graph.xml`).
- `ui/placeholder/PlaceholderFragment` là màn tạm, thay bằng Home ở giai đoạn 2.
