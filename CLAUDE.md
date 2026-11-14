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
| 1 | Theme, font, dữ liệu 30 chủ đề | **Code xong, còn thiếu dữ liệu** (xem dưới) |
| 2 | Home + Preview video | **Xong** (đã chạy trên máy thật Xiaomi Android 16; còn điền `AppConfig.CONFIG_URL`) |
| 3 | Choose a Filter + New filter | **Tiếp theo** |
| 4 | Make a Filter + Room (thêm Room + KSP ở đây) | Chưa |
| 5 | Camera (5a → 5d) | Chưa |
| 6 | Add Sound, Preview, Successful | Chưa |
| 7 | Gallery | Chưa |
| 8 | Splash, Language, Intro, Permission, Setting | Chưa |
| 9 | Hoàn thiện, release | Chưa |

Giai đoạn 1 đã có: font (Londrina Solid Regular/Light, Roboto Regular/Medium, tải từ Google Fonts),
colors/dimens/themes/type/styles, drawable dùng chung, `data/FilterDto.kt`, `data/BuiltInFilters.kt`
(`loadBuiltInFilters()`), thư mục `values-es/fr/hi/pt/in`. Màn tạm `PlaceholderFragment` hiển thị mẫu
các style và log số filter (tag `Phase1`).

Còn thiếu để đóng giai đoạn 1:
- Chạy `export_figma_topics.py` (người dùng giữ file này, không có trong repo) rồi chép `out/assets/*`
  vào `app/src/main/assets/`. Khi đó Logcat tag `Phase1` phải báo 30 filter.
- Chạy app trên máy thật, nhìn màn tạm để xác nhận font Londrina, nút Back, tab pill, nút xám khi disabled.

Ghi chú style (tên trong `res/values/styles.xml`): `Button.Primary` (mặc định cho mọi MaterialButton,
disabled tự xám), `Button.Back` (ImageButton), `Card.Blue`, `Input.Rounded`, `Tab.Pill`
(dùng trong MaterialButtonToggleGroup), `Text.ScreenTitle`, `Shape.Rounded4/8/12`, `Shape.Pill`.
Theme chỉ có bản sáng (đã bỏ values-night). Màu nút Back lấy từ Figma node 25:222.

Giai đoạn 2 đã có:
- `ui/home/HomeFragment` + `HomeViewModel` + `TrendingAdapter`; layout `fragment_home.xml`,
  `view_new_filter_banner.xml` (đặt theo % khung 328:114), `item_trending_video.xml` (tỉ lệ 156:240).
- `ui/common/BottomNav.kt` + `view_bottom_nav.xml`: bottom nav tự vẽ, dùng lại ở Gallery (giai đoạn 7) với `BottomTab.GALLERY`.
- `ui/preview/PreviewVideoFragment`: ExoPlayer lặp video, cache đĩa 200 MB (`video/VideoCache.kt`), nút Try now → chooseFilter (truyền `filter_id`).
- `data/trending/`: đọc `config.json` (HttpURLConnection + kotlinx.serialization). Thêm 2 trường tùy chọn
  cho mỗi video: `title`, `filter_id`. Lỗi mạng hoặc `CONFIG_URL` rỗng → dùng avatar filter làm dữ liệu dự phòng.
- `ui/common/ComingSoonFragment`: màn tạm cho newFilter, chooseFilter, gallery, setting (arg `title` kiểu reference).
- Insets: MainActivity KHÔNG còn padding chung; mỗi Fragment tự gọi `applySystemBarsPadding()` (ui/common/Insets.kt).
- Asset Figma: `drawable-nodpi/` (nền Home, logo, banner) và vector `ic_crown_gold/silver/bronze`, icon nav, settings.
  Ảnh Figma export qua MCP bị dính nền xám #444 → lấy ảnh gốc (rawImages) và tự cắt, vector thì chuyển tay.

Kiểm thử trên máy thật: dùng emulator nếu được. Nếu dùng điện thoại của người dùng qua adb, kiểm tra app đang
ở foreground trước mỗi lần `input tap`, không nối nhiều phím BACK (đã từng chạm nhầm sang app khác).

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
