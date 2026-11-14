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
| 2 | Home + Preview video | **Xong**; prompt 2b (API Trending thật) code xong nhưng server đang 404 (xem dưới) |
| 3 | Choose a Filter + New filter | **Xong** (đã kiểm trên Xiaomi; còn kiểm lại "đủ 30 filter" khi có dữ liệu thật) |
| 4 | Make a Filter + Room (thêm Room + KSP ở đây) | **Xong** (đã kiểm trên Xiaomi 14/11/2026) |
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
- `data/trending/`: `TrendingApi` (BASE, ghép URL, đổi `'`→`’` và `...`→`…` rồi `Uri.encode`, `matchFilterId`),
  `TrendingRepository` đọc `folders.json` (mảng chuỗi, HttpURLConnection + kotlinx.serialization), `TrendingVideo`
  (id = nguyên tên thư mục). URL lấy từ `AppConfig.TRENDING_FOLDERS_URL`. Lỗi mạng → dùng avatar filter làm dữ liệu dự phòng.
- `ui/common/ComingSoonFragment`: màn tạm cho newFilter, chooseFilter, gallery, setting (arg `title` kiểu reference).
- Insets: MainActivity KHÔNG còn padding chung; mỗi Fragment tự gọi `applySystemBarsPadding()` (ui/common/Insets.kt).
- Asset Figma: `drawable-nodpi/` (nền Home, logo, banner) và vector `ic_crown_gold/silver/bronze`, icon nav, settings.
  Ảnh Figma export qua MCP bị dính nền xám #444 → lấy ảnh gốc (rawImages) và tự cắt, vector thì chuyển tay.

Giai đoạn 3 đã có (chưa kiểm trên máy): `ui/filter/` (ChooseFilterFragment dùng chung cho chooseFilter và
newFilter, ChooseFilterViewModel, FilterAdapter, FilterCardItem), `data/SelectedFilterStore.kt`,
`fragment_choose_filter.xml`, `item_filter.xml`, nav_graph nối tới camera / makeFilter.
`app/src/main/assets/` hiện là DỮ LIỆU THỬ (8 filter, thư mục `filters/test`), thay bằng output của
`export_figma_topics.py` rồi mới kiểm mục "Discover hiện đủ 30 filter".

Giai đoạn 4 đã có (đã kiểm trên máy: tạo 12 ảnh → Save, còn sau khi tắt app, Edit đổi tên, 11 ảnh → Save xám,
Leave bỏ thay đổi, Delete xóa cả thư mục ảnh; CHƯA thử Take a Photo, Choose Photos cho ảnh bìa, xoay màn hình):
- 4a `data/userfilter/`: `UserFilter` + `UserFilterPhoto` (1-n, CASCADE), `UserFilterDao` (`save()` thay toàn bộ ảnh
  trong một transaction), `AppDatabase` (`ranking_filter.db`, schema xuất ở `app/schemas/`), `UserFilterRepository`
  copy ảnh vào `filesDir/user_filters/<id>/` và xóa file thừa khi sửa. Tab My Filters đọc từ Room.
- 4b `ui/makefilter/`: `MakeFilterFragment` (tạo mới và sửa, arg `filter_id`), `MakeFilterViewModel` (lưu nháp vào
  SavedStateHandle), `PhotoSlotAdapter` (luôn 12 ô: ảnh → ô Add → ô số), `AddPhotoDialogFragment`.
  Take a Photo: xin quyền CAMERA rồi `TakePicture` vào `cache/captures/` qua FileProvider (`res/xml/file_paths.xml`).
  Upload: `PickMultipleVisualMedia(12)`, thừa thì cắt và báo Toast.
- `ui/common/ConfirmDialogFragment` (popup Leave / Delete, kết quả qua Fragment Result API), `ui/filter/FilterMenuPopup`
  (nút "…" trên ô filter tự tạo → Edit / Delete, theo Figma 29:2008; dùng lại ở Gallery giai đoạn 7).
- Lưu xong → quay lại Choose a Filter và tự mở tab My Filters (`MakeFilterFragment.RESULT_FILTER_SAVED`).
- Nút Save Filter khi bấm được dùng gradient vàng theo Figma (29:1871), không phải xanh như sổ tay ghi.
- Chưa làm màn Successful (57:137) của Make a Filter: prompt 4b chỉ yêu cầu về My Filters.

Việc còn treo:
- Server Trending: từ 14/11/2026 mọi đường dẫn dưới `https://lvtglobal.site/public/app/` (cả `folders.json`, `avatar.png`)
  bị Cloudflare chuyển 302 → `/404.html`. App vẫn chạy với dữ liệu dự phòng + dải lỗi Retry. Cần hỏi người quản lý
  server đường dẫn mới; code 2b (`TrendingApi`) giữ nguyên, chỉ đổi `BASE` nếu server dời chỗ.
- Thay `app/src/main/assets/` bằng output `export_figma_topics.py`, kiểm lại Discover 30 filter, New filter 6 filter.
- Chưa thử trên máy: Take a Photo (camera hệ thống), chọn ảnh bìa, Toast khi chọn quá 12 ảnh.

Lỗi đã gặp khi chạy thật (đừng lặp lại):
- Theme đặt `app:fontFamily` = Londrina và AppCompat ưu tiên nó hơn `android:fontFamily` → view dùng font khác
  phải ghi CẢ `android:fontFamily` và `app:fontFamily`.
- DialogFragment inflate không có view cha → `layout_width` của thẻ gốc bị bỏ; bọc layout popup trong FrameLayout.

Kiểm thử trên máy thật: dùng emulator nếu được (máy này chưa có AVD). Nếu dùng điện thoại của người dùng qua adb
(Xiaomi 24069RA21C, 1220x2712, density 480 → 1dp = 3px), kiểm tra app đang ở foreground trước mỗi lần `input tap`,
không nối nhiều phím BACK (đã từng chạm nhầm sang app khác); ẩn bàn phím bằng `keyevent 111` (ESC), không dùng BACK.
Git Bash đổi đường dẫn `/sdcard/...` → đặt `MSYS_NO_PATHCONV=1` khi gọi `adb shell`. Không tự thao tác Photo Picker
(lộ ảnh riêng của người dùng): nhờ người dùng chọn ảnh.

Sau mỗi giai đoạn: cập nhật bảng trên, commit, push.

## Quyết định kỹ thuật đã chốt

- Kotlin + XML Views (không Compose), ViewBinding, minSdk 24, compile/targetSdk 37, Java 17.
- Package / applicationId: `com.example.rankingfilter`.
- AGP 9.5.1 có Kotlin tích hợp: KHÔNG apply plugin `kotlin-android` trong `app/build.gradle.kts`.
  Kotlin 2.4.21 được ghim bằng `apply false` ở `build.gradle.kts` gốc; serialization plugin apply ở module app.
- Phiên bản thư viện nằm trong `gradle/libs.versions.toml`. Room 2.8.5 + KSP 2.3.12 (KSP2 không còn gắn số phiên bản
  với Kotlin; chạy được với Kotlin 2.4.21). Plugin `androidx.room` đặt `schemaDirectory`.
- Coil 3 (`io.coil-kt.coil3`) kèm `coil-network-okhttp`.
- Mọi chữ hiển thị nằm trong `strings.xml` (6 ngôn ngữ: en, es, fr, hi, pt, in). Tên 30 chủ đề giữ tiếng Anh.
- Mọi filter có đúng 12 ảnh → Camera luôn 12 ô.
- Không có quảng cáo (bỏ AdMob, bỏ dòng "This action can contain ads").
- Video Trending trên server, đọc qua `folders.json` (không có config.json); nhạc 74 bài trong `assets/music/`.

## Build

```
export JAVA_HOME=/snap/android-studio/current/jbr   # hoặc JBR của Android Studio trên máy đó
./gradlew :app:assembleDebug
./gradlew :app:installDebug                         # cài lên máy thật đang cắm USB
```

Cấu trúc hiện tại:
- `MainActivity` chứa `NavHostFragment` (`res/layout/activity_main.xml`, `res/navigation/nav_graph.xml`).
- `ui/placeholder/PlaceholderFragment` là màn tạm, thay bằng Home ở giai đoạn 2.
