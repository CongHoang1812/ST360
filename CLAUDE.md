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
| 5 | Camera (5a → 5d) | **5a xong** (đã kiểm trên Xiaomi); **5b xong**; **5c xong**; **5d xong** (đã kiểm trên Xiaomi 15/11/2026, camera trước) |
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

Giai đoạn 5a đã có (đã kiểm trên máy: preview trước/sau, lật, đèn pin ở camera sau, chọn thời lượng, nút chọn filter, Back,
bảng xin quyền khi từ chối → Open Settings → cấp quyền → quay lại camera tự mở, Logcat báo 12 ảnh filter có sẵn):
- `ui/camera/CameraFragment` + `fragment_camera.xml` (Figma 5:9): PreviewView toàn màn, hàng trên (Back, pill Add Sound,
  lật camera, flash), cột 12 ô số, dải 60s/30s/Free/15s, nút quay, nút chọn filter. Hàng trên bám đỉnh, cụm dưới bám đáy.
- 12 ô số tạo bằng vòng lặp từ `item_rank_slot.xml` (có sẵn `ivPhoto` ẩn cho 5c); `sizeRankSlots()` tự thu nhỏ ô
  nếu cột không đủ cao. Kích thước lấy từ `dimens.xml` (`rank_slot_*`, `rank_column_*`).
- CameraX: chỉ `Preview`, mặc định camera trước; `bindCamera()` gọi lại khi lật. Flash = torch
  (`cameraControl.enableTorch`), nút tự mờ khi camera không có đèn; nút lật mờ khi máy chỉ có một camera.
- Quyền CAMERA + RECORD_AUDIO: tự hỏi một lần khi vào màn; bị từ chối thì hiện bảng giải thích, nút "Allow" hỏi lại,
  hoặc "Open Settings" khi đã bị từ chối hẳn. Kiểm lại quyền mỗi lần `onStart`.
- `CameraViewModel`: đọc `filter_id` / `is_user_filter` (thiếu thì lấy `SelectedFilterStore`), giải mã ảnh filter thành
  bitmap phần mềm 384px bằng Coil (`state.filter.photos`) để 5b–5d vẽ; log tag `CameraViewModel` báo số ảnh.
  Giữ thời lượng đã chọn (`RecordDuration`) và camera trước/sau trong SavedStateHandle.
- Nút chọn filter: quay về Choose a Filter (popBackStack, không có trong back stack thì mở mới).
- Nút quay và Add Sound hiện Toast "Coming soon" (làm ở 5d và giai đoạn 6).
- Thanh trạng thái đổi sang icon trắng khi ở màn Camera (`onStart`), trả lại icon đen ở `onStop`.

Giai đoạn 5b đã có (đã kiểm trên Xiaomi, camera trước: ảnh nằm trên đầu, căn giữa, nghiêng đầu sang phải/trái thì ảnh
nghiêng đúng chiều và dời theo hướng đỉnh đầu, che kín mặt thì ẩn, bỏ tay ra thì hiện lại):
- `ui/camera/face/FaceAnalyzer`: ImageAnalysis + ML Kit (fast, tracking, không landmark/contour/classification, minFaceSize 0.15).
  Đổi tọa độ bằng `CoordinateTransform(ImageProxyTransformFactory{isUsingRotationDegrees=true}, PreviewView.outputTransform)`;
  Preview và ImageAnalysis PHẢI chung `ViewPort` (`UseCaseGroup` trong `CameraFragment.bindCamera`), không thì khung bị lệch.
  `outputTransform` chỉ gọi được trên luồng chính (ML Kit trả Task về luồng chính nên ổn).
- Góc nghiêng: euler Z (dương = ngược chiều kim đồng hồ) → đổi dấu → dựng vector "đỉnh đầu" → biến đổi cùng ma trận
  → atan2. Lật gương camera trước tự đảo dấu, không if camera trước/sau. Toán nằm trong `HeadPlacements` (hàm thuần,
  7 test JUnit ở `app/src/test/.../HeadPlacementsTest`; chạy `./gradlew :app:testDebugUnitTest`).
- Ảnh đặt dọc theo hướng đỉnh đầu (không chỉ xoay tại chỗ): cạnh = 0,9 × bề rộng mặt (cạnh ngắn của khung),
  khe hở 0,12 × chiều cao mặt (`SIZE_RATIO`, `GAP_RATIO` trong `HeadPlacements`, chỉnh được).
- `FaceOverlayView`: làm mượt 0,5, ẩn sau 150ms không thấy mặt (tránh nhấp nháy), center-crop ảnh về hình vuông.
  Ảnh chờ: `drawable-nodpi/head_placeholder.jpg` ("?" trong Figma 5:13, ảnh JPEG 640x640 thật sự, không trong suốt).
- Quay video (5d) cần vẽ lên cả video bằng OverlayEffect: `FaceOverlayView` chỉ vẽ lên preview, phần tính vị trí
  (`HeadPlacements`) và nhận mặt (`FaceAnalyzer`) dùng lại được.

Giai đoạn 5c đã có (đã kiểm trên Xiaomi: bấm quay → ván bắt đầu, chốt ô, làm lại, chốt đủ 12 → sang Preview, Back về camera
chưa chơi, nút dừng bỏ ván):
- `ui/camera/game/RankingGame<T>`: lớp trong sổ tay, đổi `Bitmap` thành tham số kiểu để test bằng JUnit (9 test).
  Ván nằm trong `CameraViewModel` (`startGame/restartGame/stopGame/spin/placePhoto`, trạng thái `GameUi`) nên xoay màn hình
  không mất ván. `GameUi.isActive` = đã bấm quay (kể cả vừa xếp xong), `isPlaying` = đang chơi và còn ô trống.
- `CameraFragment`: vòng `spinLoop()` 150ms (cùng vòng đời STARTED, chỉ spin khi đang chơi VÀ thấy mặt); chạm ô chỉ chốt khi
  đang thấy mặt (mất mặt thì ảnh ẩn, người chơi không biết mình chốt ảnh nào mà chốt rồi không sửa được).
  Xếp đủ 12 → chờ 700ms (thấy cột đầy) → `stopGame()` → `action_camera_to_preview` (màn tạm `resultPreviewFragment`).
- Giao diện đang chơi theo Figma 8:102/8:192/8:235: ẩn Back, Add Sound, lật camera, flash, dải thời lượng, nút chọn filter;
  nút quay thành hình vuông bo 8dp 32dp; ô trống viền trắng; ô đã chốt hiện ảnh nhỏ + số hạng đứng BÊN TRÁI ô
  (`item_rank_slot.xml` = [số 28dp][ô 36dp], `rank_column_start` 14dp). Nút làm lại KHÔNG có trong design: tôi đặt ở chỗ nút lật
  camera (`btnRestart`, icon `ic_restart_20`). Hiển thị điều khiển do `CameraFragment.updateControls()` quản lý (đã bỏ Group).
- `FaceOverlayView`: vẽ bằng BitmapShader + drawRoundRect; `cornerRatio` 0 cho ảnh chờ "?", 0,12 cho ảnh của filter.
- Chưa có: đồng hồ 00:00:10 trên đầu màn (5d), quay video thật (5d), thời lượng 15/30/60s (5d, hiện chỉ chọn được).

Giai đoạn 5d đã có. Đã kiểm trên Xiaomi (15/11/2026, camera trước): overlay vẽ cột hạng đúng chỗ, chữ số không bị ngược;
quay → chốt đủ 12 ô → tự dừng, lưu `Movies/RankingFilter/RankingFilter_<ngày>_<giờ>.mp4` → sang Preview với `video_uri`;
đồng hồ chạy; lúc quay ô đã chốt hiện ảnh nhỏ + số bên trái, ô trống viền trắng. Kiểm thêm: mở video bằng app Ảnh thấy đủ cột hạng +
ảnh trên đầu, giống hệt preview; có track tiếng (`soun`); 15s tự dừng (file 14,999s); làm lại xóa video cũ, chỉ lưu 1 file.
CHƯA kiểm: các lỗi (hết bộ nhớ, camera bị chiếm, mất quyền micro giữa chừng), camera sau, Android 9 trở xuống. Video thử đầu tiên: 576x1280, 38s, 15MB (xem ghi chú độ phân giải).
LỖI ĐÃ GẶP (15/11): ảnh trên đầu bị đẩy ra góc phải trên màn hình khi có OverlayEffect. `PreviewView.outputTransform`
không còn khớp ảnh phân tích khi preview đi qua hiệu ứng (stream sharing). Đã sửa: FaceAnalyzer đổi tọa độ qua cảm biến
(ảnh thẳng đứng → bộ đệm → `ImageInfo.sensorToBufferTransformMatrix`⁻¹ → `PreviewView.sensorToViewTransform`).
Đã kiểm lại trên máy: ảnh nằm đúng trên đầu.
- Ảnh trên đầu vẽ TRƯỚC, cột hạng vẽ SAU (`OverlayRenderer.draw`): đầu nghiêng thì ảnh trùm sang cột hạng, che ô.
- Khi mặt quay nghiêng / cúi nhìn điện thoại, ảnh dời theo hướng đỉnh đầu nên lệch sang bên: đúng theo thiết kế
  của `HeadPlacements`, không phải lỗi tọa độ.
Ghi chú độ phân giải: video ra 576x1280 dù xin FHD. Nghi do stream sharing (Preview + VideoCapture chung một luồng qua
OverlayEffect) giới hạn ở 720p rồi cắt theo tỉ lệ màn hình (ViewPort). Chưa điều tra.
- `ui/camera/overlay/`: `CameraOverlay` (OverlayEffect nhắm PREVIEW|VIDEO_CAPTURE, luồng vẽ riêng), `OverlayRenderer`
  (vẽ cột hạng + ảnh trên đầu bằng Canvas), `OverlayScene` (bất biến, luồng chính tạo, luồng vẽ đọc).
  Vẽ bằng tọa độ PreviewView: canvas.setMatrix(frame.sensorToBufferTransform) rồi concat(nghịch đảo của
  PreviewView.sensorToViewTransform). Các View trong rankColumn giờ trong suốt (alpha 0), chỉ còn là vùng chạm.
  Đã xóa `FaceOverlayView`; làm mượt chuyển sang `HeadSmoother` (hàm thuần, 6 test).
- `ui/camera/record/VideoRecorder`: Recorder FHD→HD→SD, có tiếng, MediaStore Movies/RankingFilter, giới hạn thời lượng bằng
  setDurationLimitMillis, MIRROR_MODE_ON_FRONT_ONLY (video camera trước lật gương như preview, để overlay đúng chiều).
  Phân loại lỗi: NO_STORAGE (kiểm trước 150MB + lỗi giữa chừng), CAMERA_INTERRUPTED, NO_DATA, MIC_PERMISSION, UNKNOWN.
- `CameraFragment`: máy trạng thái RecPhase IDLE/STARTING/RECORDING/STOPPING. Ván bắt đầu khi video bắt đầu ghi. Dừng tay,
  hết giờ hoặc xếp đủ 12 ô (chờ 700ms) → lưu → màn Preview tạm nhận `video_uri`. Làm lại = bỏ video, quay lại từ đầu.
  Rời màn / xoay màn hình giữa chừng = bỏ video và ván. Đồng hồ `timerPill` (Figma 14:683). Theo dõi CameraState để báo camera bị chiếm.
- Manifest thêm WRITE_EXTERNAL_STORAGE (maxSdk 28) cho Android 9 trở xuống; nhánh này không có máy để thử.
- CẦN KIỂM TRÊN MÁY: overlay có hiện trên preview không và có đúng vị trí/chiều (camera trước lật gương) không; video lưu ra có
  ảnh trên đầu + cột hạng không (mở bằng app Ảnh); đồng hồ; tự dừng 15s; xếp đủ 12 ô tự dừng; làm lại; có tiếng; file nằm ở
  Movies/RankingFilter; nhận diện mặt còn khớp không khi có stream sharing.

Hạn chế đã biết của 5b:
- Mặt bị che MỘT PHẦN (tay đè lên trán/mắt): ML Kit vẫn báo có mặt, đôi khi với góc nghiêng sai nhiều → ảnh bay lệch
  khỏi đầu (thấy 1 khung hình lệch hẳn sang trái màn). Nghi do euler Z sai khi bị che; CHƯA xác nhận bằng log.
  Nếu cần xử lý: coi |roll| > ~75° là mặt không tin cậy (ẩn ảnh) hoặc kẹp góc.
- Khi đầu di chuyển nhanh ảnh hơi trễ phía sau đầu (làm mượt 0,5 + khung hình ~14 fps trong phòng thiếu sáng).
- Chưa thử camera SAU với mặt thật (cùng đường code, chỉ khác ma trận lật gương).

Việc còn treo (5c): chưa thử filter TỰ TẠO với trò chơi; filter thử trong assets chỉ là ảnh đơn sắc nên mắt thường không
phân biệt được "ảnh đã chốt bị loại khỏi danh sách chờ" (đã có unit test); chưa thử gating khi che mặt; chưa thử xoay màn hình giữa ván.

Việc còn treo:
- 5a chưa kiểm: Camera với filter TỰ TẠO (Logcat `CameraViewModel` phải báo 12 ảnh; cần người dùng tạo filter vì phải
  chọn ảnh qua Photo Picker), xoay màn hình / hệ thống đóng app khi đang ở Camera, camera trước có người thật
  (hướng và lật gương của preview; lúc kiểm điện thoại đặt nằm nên chỉ thấy trần nhà).
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
Nếu adb treo: `taskkill //PID <pid adb.exe> //F` rồi `adb start-server`; bọc lệnh adb trong `timeout`.
Thu hồi quyền để thử luồng bị từ chối: `adb shell pm revoke com.example.rankingfilter android.permission.CAMERA`
(và `RECORD_AUDIO`); hộp thoại quyền hiện từng quyền một (camera rồi micro).
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
