# Ranking Filter: Funny Game (Android)

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
| 1 | Theme, font, dữ liệu 30 chủ đề | **Xong**: dữ liệu 30 chủ đề đã có và đã kiểm trên Xiaomi (15/11/2026) |
| 2 | Home + Preview video | **Xong**; prompt 2b (API Trending thật) code xong nhưng server đang 404 (xem dưới) |
| 3 | Choose a Filter + New filter | **Xong** (đã kiểm trên Xiaomi; còn kiểm lại "đủ 30 filter" khi có dữ liệu thật) |
| 4 | Make a Filter + Room (thêm Room + KSP ở đây) | **Xong** (đã kiểm trên Xiaomi 14/11/2026) |
| 5 | Camera (5a → 5d) | **5a xong** (đã kiểm trên Xiaomi); **5b xong**; **5c xong**; **5d xong** (đã kiểm trên Xiaomi 15/11/2026, camera trước) |
| 6 | Add Sound, Preview, Successful | **Xong phần không cần nhạc** (đã kiểm trên Xiaomi); nhạc chưa kiểm vì server 404 |
| 7 | Gallery | **Xong** (đã kiểm trên Xiaomi 15/11/2026) |
| 8 | Splash, Language, Intro, Permission, Setting | **Xong** (đã kiểm trên Xiaomi 15/11/2026; còn Share app, Rate us, Android < 13) |
| 9 | Hoàn thiện, release | Chưa |

Giai đoạn 1 đã có: font (Londrina Solid Regular/Light, Roboto Regular/Medium, tải từ Google Fonts),
colors/dimens/themes/type/styles, drawable dùng chung, `data/FilterDto.kt`, `data/BuiltInFilters.kt`
(`loadBuiltInFilters()`), thư mục `values-es/fr/hi/pt/in`. Màn tạm `PlaceholderFragment` hiển thị mẫu
các style và log số filter (tag `Phase1`).

Dữ liệu 30 chủ đề (đã kiểm trên Xiaomi 15/11/2026: Logcat `BuiltInFilters` báo 30 filter; Home banner hiện 3 filter mới; New filter đủ 6; Discover cuộn hết đủ 30; chạm filter → Camera báo 12 ảnh): lấy từ trang "data" của Figma (node 31:2751): 24 section + 6 section trong
"New Filter" (pack `New Filter`: Rank Your Favorite Players, Funny Cat Memes, Dessert Ranking, Christmas Decor, Favorite Vietnamese
Foods, Vacation Vibes; 24 còn lại pack `Discover`). Mỗi section có khung `avatar` + 12 khung ảnh 500x500. Không có export_figma_topics.py
trong repo, nên tôi tự xuất: `download_assets` mỗi section (PNG, bị giới hạn 4096px rộng → mỗi ảnh ~275px), cắt theo cột khác màu nền #444
(script `build_filters.py` ở scratchpad, Pillow), lưu `assets/filters/<slug>/avatar.webp` + `01..12.webp` (tổng ~13 MB) và `assets/filters.json`
(id = slug tiêu đề). Thứ tự ảnh = thứ tự x trong Figma (khung tên "13", "25", "37" là ảnh số 1). Ảnh 275px hơi mềm khi hiện lớn
(muốn nét hơn: xuất từng khung ở scale 1, 390 lần gọi). Ảnh xuất section có lề ~21px và viền; mép trên khung phải dò từ đoạn dọc liên tục dài nhất
(lần đầu giả định 180*scale → ảnh dính dải xám trên đầu).

Từng là mục "còn thiếu để đóng giai đoạn 1" (đã xong phần dữ liệu):
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
  Tab Home / Gallery mỗi tab phủ kín một nửa thanh (Guideline 50%) để dễ chạm; nút camera đè giữa nên vẫn nhận chạm giữa; nội dung lệch `bottom_nav_tab_content_offset` 12dp giữ tâm icon như Figma (19/11/2026, build OK, đã cài, CHƯA kiểm trên máy).
- `ui/preview/PreviewVideoFragment`: ExoPlayer lặp video, cache đĩa 200 MB (`video/VideoCache.kt`), nút Try now → chooseFilter (truyền `filter_id`).
- `data/trending/`: `TrendingApi` (BASE, ghép URL, đổi `'`→`’` và `...`→`…` rồi `Uri.encode`, `matchFilterId`),
  `TrendingRepository` đọc `folders.json` (mảng chuỗi, HttpURLConnection + kotlinx.serialization), `TrendingVideo`
  (id = nguyên tên thư mục). URL lấy từ `AppConfig.TRENDING_FOLDERS_URL`. Lỗi mạng → dùng avatar filter làm dữ liệu dự phòng.
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
  Tạo mới xong → màn Successful (Figma 57:137, `ui/makefilter/FilterSuccessFragment`, `fragment_filter_success.xml`; Make a Filter bị
  gỡ khỏi back stack bằng popUpTo inclusive): ảnh bìa + tên, My Filters / Back = về màn trước (tab My Filters), Try now → Camera
  (popUpTo Successful, Back từ Camera về danh sách), Home. Build OK, CHƯA kiểm trên máy (19/11/2026). Sửa filter: Toast "Filter updated successfully" (`make_filter_updated`) rồi quay lại; xóa filter (Choose a Filter và Gallery)
  báo "Filter deleted successfully" (`filter_deleted`) khi Room + thư mục ảnh đã xóa xong (19/11/2026, build OK, chưa kiểm trên máy).
- Nút Save Filter khi bấm được dùng gradient vàng theo Figma (29:1871), không phải xanh như sổ tay ghi.

Giai đoạn 5a đã có (đã kiểm trên máy: preview trước/sau, lật, đèn pin ở camera sau, chọn thời lượng, nút chọn filter, Back,
bảng xin quyền khi từ chối → Open Settings → cấp quyền → quay lại camera tự mở, Logcat báo 12 ảnh filter có sẵn):
- `ui/camera/CameraFragment` + `fragment_camera.xml` (Figma 5:9): PreviewView toàn màn, hàng trên (Back, pill Add Sound,
  lật camera, flash), cột 12 ô số, dải 60s/30s/Free/15s, nút quay, nút chọn filter. Hàng trên bám đỉnh, cụm dưới bám đáy.
- 12 ô số tạo bằng vòng lặp từ `item_rank_slot.xml` (có sẵn `ivPhoto` ẩn cho 5c); `sizeRankSlots()` tự thu nhỏ ô
  nếu cột không đủ cao. Kích thước lấy từ `dimens.xml` (`rank_slot_*`, `rank_column_*`); ô 42dp, số 22sp, khe 5dp (từng 36dp/20sp/6dp, tăng 16/11/2026 cho dễ nhìn).
- CameraX: chỉ `Preview`, mặc định camera trước; `bindCamera()` gọi lại khi lật. Flash = torch
  (`cameraControl.enableTorch`), nút tự mờ khi camera không có đèn (camera trước của Xiaomi không có đèn → không bật được, đúng như thiết kế code);
  icon `ic_flash_toggle` (tắt = tia chớp gạch chéo Figma 14:533, bật = tia chớp 5:9), nền tối cả hai trạng thái (19/11/2026, đã kiểm trên Xiaomi camera sau); nút lật mờ khi máy chỉ có một camera.
- Quyền CAMERA + RECORD_AUDIO: tự hỏi một lần khi vào màn; bị từ chối thì hiện bảng giải thích, nút "Allow" hỏi lại,
  hoặc "Open Settings" khi đã bị từ chối hẳn. Kiểm lại quyền mỗi lần `onStart`.
- `CameraViewModel`: đọc `filter_id` / `is_user_filter` (thiếu thì lấy `SelectedFilterStore`), giải mã ảnh filter thành
  bitmap phần mềm 384px bằng Coil (`state.filter.photos`) để 5b–5d vẽ; log tag `CameraViewModel` báo số ảnh.
  Giữ thời lượng đã chọn (`RecordDuration`) và camera trước/sau trong SavedStateHandle.
- Nút chọn filter (19/11/2026, phương án người dùng chọn; build OK, CHƯA kiểm trên máy): màn chọn filter là "bộ chọn" của Camera. Camera mở
  `action_camera_to_chooseFilter` (hoặc `_newFilter` nếu màn trước Camera là New filter) với `pick_for_camera = true`. Back = về Camera giữ filter cũ;
  chọn filter = `ChooseFilterFragment` đặt `RESULT_IS_USER_FILTER` rồi `RESULT_FILTER_ID` vào savedStateHandle của Camera và popBackStack,
  Camera (`listenFilterResult`) gọi `CameraViewModel.changeFilter`. Back stack luôn Home → Choose → Camera, không chồng nhiều Camera.
  (Đã thử phương án "chọn filter = mở Camera mới chồng lên, Back đi đúng thứ tự", người dùng bảo đổi lại như cũ, 19/11/2026.)
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
- Ảnh đặt dọc theo hướng đỉnh đầu (không chỉ xoay tại chỗ): cạnh = 0,55 × bề rộng mặt (từng 0,9 → 0,65 → 0,55) (cạnh ngắn của khung),
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
  (Đã đổi 16/11/2026: xếp đủ 12 KHÔNG còn tự dừng; video quay tiếp tới khi bấm dừng hoặc hết giờ, ảnh trên đầu ẩn khi đủ 12 ô.
  Ghi chú cũ "chờ 700ms rồi sang Preview" không còn đúng.)
- Giao diện đang chơi theo Figma 8:102/8:192/8:235: ẩn Back, Add Sound, lật camera, flash, dải thời lượng, nút chọn filter;
  nút quay thành hình vuông bo 8dp 32dp; ô trống viền trắng; ô đã chốt hiện ảnh nhỏ + số hạng đứng BÊN TRÁI ô
  (`item_rank_slot.xml` = [số 28dp][ô 42dp], `rank_column_start` 14dp). Nút làm lại lúc quay đã BỎ (19/11/2026, đúng design; muốn quay lại thì dừng rồi bấm nút thử lại ở Preview). Hiển thị điều khiển do `CameraFragment.updateControls()` quản lý (đã bỏ Group).
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
  hết giờ (xếp đủ 12 ô thì KHÔNG tự dừng nữa) → lưu → màn Preview tạm nhận `video_uri`. Làm lại = bỏ video, quay lại từ đầu.
  Rời màn / xoay màn hình giữa chừng = bỏ video và ván. Đồng hồ `timerPill` (Figma 14:683). Theo dõi CameraState để báo camera bị chiếm.
- Manifest thêm WRITE_EXTERNAL_STORAGE (maxSdk 28) cho Android 9 trở xuống; nhánh này không có máy để thử.
- CẦN KIỂM TRÊN MÁY: overlay có hiện trên preview không và có đúng vị trí/chiều (camera trước lật gương) không; video lưu ra có
  ảnh trên đầu + cột hạng không (mở bằng app Ảnh); đồng hồ; tự dừng 15s; (xếp đủ 12 ô không còn tự dừng); làm lại; có tiếng; file nằm ở
  Movies/RankingFilter; nhận diện mặt còn khớp không khi có stream sharing.

Giai đoạn 6 đã kiểm trên Xiaomi (15/11/2026): Add Sound đúng design; nghe thử báo lỗi êm khi server 404 (không crash);
quay → Preview phát video có overlay → Save → bản nháp chuyển sang files/videos → Successful đúng design → Download chép ra
Movies/RankingFilter, nút thành "Downloaded" → "…" mở bảng chia sẻ → Try More Filters mở Camera đúng filter → Back về Home (19/11/2026: từng đổi thành về lại Successful, rồi người dùng bảo đổi lại về Home; `action_success_to_camera` có popUpTo Home; Home cuộn về đầu nhờ `KEY_SCROLL_TO_TOP` đặt trong `SuccessFragment.tryFilter`).
SERVER NHẠC ĐÃ CHẠY LẠI (16/11/2026) và luồng nhạc đã kiểm trên Xiaomi: nghe thử (dòng được chọn + nút Pause, hệ thống thấy app phát âm thanh),
tick → tải `cache/music/music_1.mp3` (~964 KB) → Camera hiện pill "Music 1", quay → nhạc phát ra loa, Preview phát video + nhạc, Save → trộn xong →
Successful; file `files/videos/*.mp4` (9,9 MB) có track `vide` 15,50s + `soun` 15,55s. CHƯA kiểm: nghe nội dung file đã trộn có đúng là nhạc (không phải tiếng mic),
bài ngắn hơn video (lặp lại), bỏ nhạc đã chọn bằng nút X. Server Trending vẫn 404 (xem dưới).
Lỗi đã gặp: theme đặt `materialCardViewStyle` = Card.Blue (contentPadding 16dp) cho MỌI MaterialCardView → khung video
có viền đen dày; đã thêm `app:contentPadding="0dp"` (chưa nhìn lại trên máy vì mất kết nối).
Trên điện thoại người dùng có app TurboVPN tự bật quảng cáo toàn màn hình: tap.sh đã chặn chạm nhầm, nhờ người dùng tắt.
Quyết định (người dùng chưa chọn, lấy phương án đề xuất): Quyết định (người dùng chưa chọn, lấy phương án đề xuất):
(1) chọn nhạc thì nhạc THAY tiếng micro, nhạc phát ra loa lúc quay; (2) quay vào bộ nhớ app, Save lưu vào thư viện của app,
Download mới chép ra thư viện ảnh; (3) Successful làm theo design (Download, Share to, Try More Filters, Home).
- `data/music/MusicRepository`: 74 `MusicTrack` theo quy tắc link; thời lượng đọc từng bài khi dòng hiện ra (MediaMetadataRetriever,
  tối đa 2 bài cùng lúc, nhớ trong bộ nhớ); `localFile()` tải về `cacheDir/music/` (ghi .part rồi đổi tên).
- `data/video/VideoLibrary`: `filesDir/drafts/` (video vừa quay, chỉ 1 file) → Save → `filesDir/videos/` (Gallery giai đoạn 7 đọc ở đây)
  → Download → MediaStore `Movies/RankingFilter`. Trộn nhạc bằng Media3 Transformer (`media3-transformer` 1.11.1): chuỗi 1 = video
  bỏ tiếng, chuỗi 2 = nhạc lặp (`setIsLooping`), thử `setTransmuxVideo(true)` trước, lỗi thì mã hóa lại.
- `VideoRecorder` giờ ghi `FileOutputOptions` vào bản nháp (không còn MediaStore); Camera không còn xin quyền bộ nhớ.
- Thời lượng nhạc lưu vào Room (16/11/2026, đã kiểm trên Xiaomi: sau Splash cả 74 bài có thời lượng, tắt hẳn app mở lại Add Sound hiện đủ ngay; SERVER NHẠC ĐÃ CHẠY LẠI): bảng `music_meta(id, duration_ms)` (AppDatabase v2, `MIGRATION_1_2` chỉ thêm bảng, không đụng filter
  người dùng; schema `app/schemas/.../2.json`). `MusicRepository.warmUp()` gọi ở Splash: nạp Room vào bộ nhớ rồi đọc nền các bài còn thiếu (2 bài cùng lúc) và ghi
  lại; `ensureLoaded()` để AddSoundViewModel hiện số liệu đã lưu ngay khi mở màn. Chưa kiểm: nâng cấp v1→v2 trên dữ liệu thật (lần kiểm dữ liệu app đã bị xóa từ trước, DB tạo mới v2), nghe thử/tải bài/phát nhạc lúc quay/trộn nhạc khi Save.
- `ui/sound/` AddSound (Figma 14:620/22:738): chạm dòng = chọn + nghe thử (ExoPlayer qua `VideoCache`), chạm lại = tạm dừng;
  tick xám tới khi chọn; bấm tick = tải bài về máy rồi trả `RESULT_MUSIC_ID` qua savedStateHandle cho Camera.
  Bài đang nghe thử hiện thời gian đã phát đếm từ 00:00 (cập nhật 200ms, tạm dừng thì đứng yên, phát hết dừng đúng ở tổng thời lượng),
  bài khác hiện tổng thời lượng (`MusicRow.positionMs`, 19/11/2026, đã kiểm trên Xiaomi).
  Pill trên Camera hiện tên bài đã chọn + vạch ngăn + nút X (Figma 14:576, `btnClearMusic`, `ic_close_20`): bấm X = `setMusic(null)`, pill về "Add Sound" (19/11/2026, build OK, CHƯA kiểm trên máy).
- `ui/result/PreviewFragment` (8:334): phát lặp bản nháp; có nhạc thì tắt tiếng video + phát nhạc, về giây 0 mỗi vòng.
  Nút vòng tròn trái = hỏi popup "Leave Video?" (Figma 4045:35, ConfirmDialogFragment PRIMARY: Cancel / Try Again) rồi bỏ + quay lại Camera; Home / Back = hỏi trước rồi bỏ. Save → Transformer → Successful (popUpTo Home).
- `ui/result/SuccessFragment` (14:451): ảnh thu nhỏ (giây 1), Download (Android 9- xin WRITE_EXTERNAL_STORAGE), Share tới đúng
  app nếu đã cài (FileProvider `files/videos`), không có thì bảng chia sẻ; Try More Filters (10 filter ngẫu nhiên) → Camera.
- Nền Preview/Successful dùng lại `bg_home` (ảnh nền trong Figma giống hệt). Icon mạng xã hội: `drawable-nodpi/ic_share_*.png` 64px.

Giai đoạn 7 đã kiểm trên Xiaomi (15/11/2026): Gallery mở từ bottom nav, tab dạng thẻ đúng design, lưới video có ảnh thu nhỏ
(thấy cột hạng), chạm mở màn xem video, chạm để dừng → nút phát + `00:04/00:08` + SeekBar, Download thêm 1 file vào
Movies/RankingFilter, xóa hỏi trước rồi về Gallery "No records yet" (bản đã Download vẫn còn), My Filters trống đúng design,
Create Filter → Make a Filter → Back giữ tab My Filters. Có filter tự tạo: ô Create Filter vàng đứng đầu + ô filter có "…";
Edit mở Make a Filter đúng tên và 12/12 ảnh; Delete hiện popup "Delete Filter?" (đã Cancel, giữ filter của người dùng);
chạm filter → Camera, Logcat `CameraViewModel` báo 12 ảnh của filter tự tạo (đóng mục treo 5a). CHƯA kiểm: Share, kéo SeekBar.
Lỗi đã gặp: thuộc tính trên thẻ `<include>` (translationZ) bị bỏ → bottom nav nằm dưới khung nội dung, không thấy;
đặt `translationZ` trong code. Ô video: `clickable` đặt trên card nên phải gắn click vào `binding.card`, không phải root.

Giai đoạn 7 đã có:
- `ui/gallery/GalleryFragment` (21:281): header như Home, 2 tab dạng thẻ (`Text.GalleryTab`, tab không chọn thấp 8dp, khung
  `panel` che đáy), bottom nav `BottomTab.GALLERY`. My Videos đọc `VideoLibrary.listSaved` (filesDir/videos), KHÔNG đọc MediaStore
  (prompt yêu cầu MediaStore; đổi vì màn xem video có nút Download → video trong Gallery chưa ở thư viện ảnh) → không cần quyền đọc media.
  Ảnh thu nhỏ bằng `coil-video` (`decoderFactory(VideoFrameDecoder.Factory())`, `videoFrameMillis(1000)`).
  My Filters: ô "Create Filter" vàng đầu lưới (ConcatAdapter) + FilterAdapter có nút "…" (FilterMenuPopup Edit/Delete);
  chạm filter → Camera với filter đó. Trạng thái trống: "No records yet" / "Make Your Own Filter" + nút Create Filter.
  Design KHÔNG có chọn nhiều để xóa → không làm. Xóa video hỏi "Delete Video? / This can't be undone." (28:1565).
- `ui/gallery/VideoViewerFragment` (8:278 / 21:368): ExoPlayer lặp, chạm để dừng/phát; khi dừng hiện nút phát + thanh
  thời gian `00:12/00:50` có SeekBar; Share (bảng chia sẻ), Download (Movies/RankingFilter), xóa (hỏi trước).

Giai đoạn 8 đã kiểm trên Xiaomi (15/11/2026): Splash đúng design (không còn dòng quảng cáo) → Language (chọn French: giao diện đổi sang
tiếng Pháp ngay) → Intro 3 trang (chấm đúng, Passer ẩn ở trang cuối) → Permission (công tắc phản ánh quyền thật) → Home → Setting →
Language trong Setting (có Back, tiêu đề giữa, đúng dòng đang chọn) → đổi lại English → Setting đã tiếng Anh; tắt rồi mở lại app vào
thẳng Home. CHƯA kiểm: Rate us, Share app, Privacy (URL rỗng → Toast), bật quyền Photos, Android dưới 13 (lưu ngôn ngữ qua
AppLocalesMetadataHolderService), 4 ngôn ngữ còn lại (es, hi, pt, in) trên máy. Máy Redmi 21061119AG (1080x2400) không chạm được qua adb
(thiếu INJECT_EVENTS, cần bật "USB debugging (Security settings)"); dùng Xiaomi 24069RA21C.
Không dùng `pm clear` khi kiểm (xóa filter/video của người dùng).

Giai đoạn 8 đã có:
- Luồng: Splash (2s) → Language → Intro → Permission → Home; đã xem intro (`AppPrefs.isIntroSeen`, SharedPreferences) thì Splash → Home.
  Nav start = `splashFragment`; mỗi màn onboarding popUpTo chính nó (inclusive). (`ComingSoonFragment` đã xóa 17/11/2026.)
- `ui/splash`: `splash_full.webp` là ảnh khung Figma 3:2 đã vá (thanh trạng thái + dòng quảng cáo thay bằng nền `bg_home`) + vòng tròn tải native.
- `ui/language/LanguageFragment` (arg `from_settings`): 6 dòng (`APP_LANGUAGES`: en es fr hi pt id) tên ngôn ngữ cố định tiếng Anh như design (`lang_*` translatable=false, KHÔNG dịch: trước đây đổi sang French thì các dòng thành "Anglais, Espagnol…", người dùng báo lỗi 19/11/2026), tick lưu bằng `AppCompatDelegate.setApplicationLocales`,
  `res/xml/locales_config.xml` + `android:localeConfig` + `AppLocalesMetadataHolderService` (manifest). Điều hướng TRƯỚC rồi mới đổi ngôn ngữ (đổi làm tạo lại Activity).
- `ui/intro`: ViewPager2 3 trang (`intro_art_1..3.webp` = ảnh khung Figma cắt 360x550 đã vá thanh trạng thái), chấm, Next (đã bỏ nút Skip 19/11/2026 theo yêu cầu người dùng, design không có).
  Câu trang 1: "Drag, drop, and rank them from #1 to #10." đúng như design (người dùng yêu cầu 19/11/2026, dù app thật là chạm ô và có 12 ô).
- `ui/permission`: 3 SwitchCompat (Camera, Photos, Microphone; núm `drawable/switch_thumb*` luôn 20dp, ray `switch_track` 56x28 theo Figma 27:616, đổi từ MaterialSwitch 19/11/2026 vì núm M3 co còn 16dp khi tắt; PHẢI có `app:thumbTint="@null"` + `app:trackTint="@null"`, không thì switchStyle của theme Material3 tô cả núm lẫn ray màu navy, núm biến mất; đã kiểm trên Xiaomi); chạm = xin quyền, đã cấp/bị từ chối hẳn = mở cài đặt app. Continue luôn đi tiếp.
- `ui/setting/RateDialogFragment` (16/11/2026, Figma 4034:33; build OK, CHƯA kiểm trên máy): bấm Rate us trong Setting mở popup 5 sao; emoji là ảnh PNG 160px lấy từ Figma (`drawable-nodpi/rate_emoji_0..5.png`), sao là vector theo path Figma (vàng #FFDC4E / xám #C4C4C4, kéo ngón tay để chấm), lời nhắn đổi theo số sao; chọn 1–5 sao + Rate → chỉ Toast cảm ơn (`rate_thanks`) rồi đóng, KHÔNG mở Play Store; 0 sao bấm Rate → không làm gì. Rate xong lưu cờ `AppPrefs.isRated` và ẩn dòng Rate us trong Setting (cả những lần mở app sau).
- Back ở Home (19/11/2026, build OK, CHƯA kiểm trên máy): thoát = `requireActivity().finish()` (không để mặc định Android 12+ chỉ đưa xuống nền),
  nên mở lại đi Splash → Intro. Chưa Rate: lần lẻ thoát, lần chẵn hiện popup Rate (`AppPrefs.nextHomeBackCount`, đếm qua các lần mở app);
  đã Rate (`AppPrefs.isRated`) thì luôn thoát. Rate xong trong popup mở từ Back ở Home (chấm sao + Rate) → Toast cảm ơn rồi thoát app luôn; Exit chỉ đóng popup (19/11/2026, build OK, CHƯA kiểm trên máy). Code ở `HomeFragment.setupRateOnBack()`.
- `ui/setting`: Language / Rate us (popup, ẩn sau khi đã rate) / Share app (ACTION_SEND) / Privacy & Policy (`AppConfig.PRIVACY_POLICY_URL` đang RỖNG, điền sau; rỗng thì báo Toast).
- Đã dịch toàn bộ `strings.xml` sang es, fr, hi, pt, in (bản dịch do tôi viết, nên nhờ người bản ngữ rà lại trước khi phát hành).
- Ảnh Figma export bị dính nền #444: ảnh Splash/Intro lấy từ ảnh KHUNG đầy đủ (có nền thật) rồi cắt; Pillow cài ở scratchpad, không có trong repo.

Cách chơi (16/11/2026, theo yêu cầu người dùng): lúc CHƯA quay, ảnh filter nháy đổi liên tục trên đầu mỗi 100ms (`PREVIEW_INTERVAL_MS`, bo góc, không còn "?"
nếu filter có ảnh; đã kiểm trên máy). Lúc QUAY ảnh đổi mỗi 50ms (`SPIN_INTERVAL_MS`), sau 0,6–1,5s ngẫu nhiên (`SPIN_MIN_MS`/`SPIN_MAX_MS`) thì DỪNG ở một hình
(`spinStopped`), người chơi chạm ô số để chốt hình đó (chạm khi ảnh đang chạy không có tác dụng), chốt xong ảnh chạy tiếp. Thời gian chỉ đếm
khi thấy mặt. Đã kiểm trên Xiaomi (bản 1,5–3,5s/100ms): ảnh dừng, chạm ô 1 → hình vào ô 1 có số bên trái, ảnh trên đầu chạy tiếp.
Bản 50ms + 0,6–1,5s CHƯA kiểm lúc quay (TurboVPN bật quảng cáo đè lên giữa chừng).

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
- Bấm nhanh 2 ô Trending ở Home → crash `action_home_to_preview cannot be found from the current destination` (19/11/2026). Đã sửa:
  `ui/common/Navigation.kt` `NavController.navigateSafe()` (bỏ qua nếu màn hiện tại không có action) dùng cho mọi điều hướng ở Home,
  `rvTrending` có `splitMotionEvents="false"`. Đã kiểm trên Xiaomi. 19/11/2026: lỗi này còn ở mọi màn (bấm nhiều filter cùng lúc) → MỌI
  `navigate()` trong app đổi sang `navigateSafe()`, mọi RecyclerView có `splitMotionEvents="false"`, ChooseFilter ở chế độ chọn cho Camera
  chỉ popBackStack khi màn hiện tại vẫn là chooseFilter/newFilter (không thì ô thứ hai pop luôn Camera về Home). Build OK, CHƯA kiểm trên máy.
  Điều hướng mới: luôn dùng `navigateSafe()`, không gọi `navigate()` trực tiếp.
  Đã thử chặn bấm Back 2 lần (navigateUpSafe/isTopOfNav), người dùng bảo bỏ, trả về như cũ (19/11/2026): nút Back gọi `navigateUp()` thường.
- Tablet: mở video Trending thứ 2 trở đi thì lộ mép video trên/dưới (19/11/2026, người dùng báo). Nghi khung ViewPager2 đổi chiều cao
  sau `setCurrentItem` (thanh điều hướng / taskbar ẩn hiện) nên trang lệch theo pixel. Đã thêm `PreviewPagerFragment.snapToCurrentPage()`
  (căn trang hiện tại khít mép trên khi pager đổi kích thước và sau lần layout đầu). Build OK, CHƯA tái hiện/kiểm trên tablet
  (giả lập bằng `adb shell wm density 240` trên Xiaomi bị chặn vì máy khóa; nhớ `wm density reset` sau khi thử).
- DialogFragment inflate không có view cha → `layout_width` của thẻ gốc bị bỏ; bọc layout popup trong FrameLayout.

Kiểm thử trên máy thật: dùng emulator nếu được (máy này chưa có AVD). Nếu dùng điện thoại của người dùng qua adb
(Xiaomi 24069RA21C, 1220x2712, density 480 → 1dp = 3px), kiểm tra app đang ở foreground trước mỗi lần `input tap`,
không nối nhiều phím BACK (đã từng chạm nhầm sang app khác); ẩn bàn phím bằng `keyevent 111` (ESC), không dùng BACK.
Nếu adb treo: `taskkill //PID <pid adb.exe> //F` rồi `adb start-server`; bọc lệnh adb trong `timeout`.
Thu hồi quyền để thử luồng bị từ chối: `adb shell pm revoke com.funny.rank.filters android.permission.CAMERA`
(và `RECORD_AUDIO`); hộp thoại quyền hiện từng quyền một (camera rồi micro).
Git Bash đổi đường dẫn `/sdcard/...` → đặt `MSYS_NO_PATHCONV=1` khi gọi `adb shell`. Không tự thao tác Photo Picker
(lộ ảnh riêng của người dùng): nhờ người dùng chọn ảnh.

Sau mỗi giai đoạn: cập nhật bảng trên, commit, push.

- Đa kích thước màn hình (16/11/2026, theo `docs/multi-screen/`; build OK chưa chạy, CHƯA kiểm trên máy/emulator tablet): thiết kế 360dp, mọi kích thước là `@dimen`
  (491 chỗ số cứng đã chuyển, tên dạng `<màn>_<view>_<thuộc tính>`, một số tên chưa đẹp như `*_framelayout1_*`). `scripts/gen_screen_dimens.py` nhân với 1,6 (sw600dp) / 2,0 (sw800dp)
  và sinh `values-sw600dp|sw800dp/dimens.xml` + `integers.xml`. Số cột lưới giữ như điện thoại (2 và 3). `content_max_width` ở `PHONE` (không giới hạn bề rộng).
  Layout riêng duy nhất: `layout-sw600dp/fragment_intro.xml` + `item_intro_page.xml` (tỉ lệ 360:660 vượt chiều cao tablet; ảnh co theo chiều cao còn lại). Camera: `sizeRankSlots()` tự thu nhỏ ô.
- Đa màn hình: layout chỉ dùng @dimen; dimen điện thoại ở values/dimens.xml. Không sửa tay values-sw600dp / values-sw800dp.
- Thêm / đổi dimen thì chạy `python3 scripts/gen_screen_dimens.py`; có dòng "!" thì chỉnh `fixed` / `STACKS`.
- Màn xếp dọc cố định phải vào STACKS; số cột lưới lấy từ R.integer.*_columns (hiện là `span_filters`, `span_make_filter_photos`).

## Quyết định kỹ thuật đã chốt

- Không hiệu ứng chạm (ripple): theme đặt `selectableItemBackground(Borderless)` trong suốt, `Button.Primary` và nút pill có
  `rippleColor` trong suốt (người dùng yêu cầu 16/11/2026, áp dụng cả thẻ video/filter/dòng nhạc). Chưa kiểm trên máy.
- Animation chuyển màn (19/11/2026; build OK, CHƯA kiểm trên máy, người dùng tự thử): `res/animator/nav_*.xml` gắn vào MỌI action trong `nav_graph.xml`.
  Dùng animator (translationX + alpha) chứ không dùng `anim`, để SurfaceView (PreviewView của Camera, PlayerView của Trending) đi theo.
  PlayerView của Trending (`fragment_preview_video.xml`) phải là `app:surface_type="texture_view"`: SurfaceView không theo alpha/translationX → Back về Home bị đen màn. Thêm: `PreviewVideoFragment.onStop` khi đang rời màn (`isLeaving`) chỉ dừng, release player ở onDestroyView (sau animation); release() chặn luồng chính và pager có 2–3 player → animation giật/đen. Xuống nền vẫn release ngay (19/11/2026, đã cài lên Xiaomi, CHƯA nhìn kết quả).
  Màn thường: trượt 48dp (`nav_slide_offset`) + mờ dần, 300ms (`@integer/nav_anim_duration` trong `values/motion.xml`); onboarding (Splash/Language/Intro/Permission)
  và Home ↔ Gallery: chỉ mờ dần. Action mới phải thêm đủ 4 thuộc tính enter/exit/popEnter/popExit; `navOptions` trong code cũng phải có `anim { }` (xem `CameraFragment.openChooseFilter`).
- Thanh điều hướng hệ thống bị ẩn (`MainActivity.hideNavigationBar`, vuốt từ đáy để hiện tạm). Chưa kiểm trên máy: nội dung sát đáy ở Camera/Home/Gallery.

- Kotlin + XML Views (không Compose), ViewBinding, minSdk 24, compile/targetSdk 37, Java 17.
- Package / applicationId: `com.funny.rank.filters`.
- AGP 9.5.1 có Kotlin tích hợp: KHÔNG apply plugin `kotlin-android` trong `app/build.gradle.kts`.
  Kotlin 2.4.21 được ghim bằng `apply false` ở `build.gradle.kts` gốc; serialization plugin apply ở module app.
- Phiên bản thư viện nằm trong `gradle/libs.versions.toml`. Room 2.8.5 + KSP 2.3.12 (KSP2 không còn gắn số phiên bản
  với Kotlin; chạy được với Kotlin 2.4.21). Plugin `androidx.room` đặt `schemaDirectory`.
- Coil 3 (`io.coil-kt.coil3`) kèm `coil-network-okhttp`.
- Mọi chữ hiển thị nằm trong `strings.xml` (6 ngôn ngữ: en, es, fr, hi, pt, in). Tên 30 chủ đề giữ tiếng Anh.
- Filter có sẵn: đúng 12 ảnh. Filter tự tạo: 10–12 ảnh (19/11/2026, theo Figma 57:247 "Minimum 10 photos"; `FilterDto.MIN_PHOTO_COUNT`).
  Make a Filter: Add + ô mờ tới ô 10, Save bật từ 10 ảnh. Camera: số ô hạng = `FilterDto.slotCountFor(số ảnh)` (dựng lại khi filter nạp xong,
  không đổi giữa ván). Build OK, CHƯA kiểm trên máy.
- Không có quảng cáo (bỏ AdMob, bỏ dòng "This action can contain ads").
- Video Trending trên server, đọc qua `folders.json` (không có config.json).
- Nhạc 74 bài trên server (KHÔNG có trong assets): `https://lvtglobal.site/public/app/ST314_FunnyFaceFilters/music/music_<1..74>.mp3`
  (quy tắc lấy từ `config.json` của ST314; người dùng dặn không cần gọi thử API). Hằng số trong `data/music/MusicRepository`.

## Build

```
export JAVA_HOME=/snap/android-studio/current/jbr   # hoặc JBR của Android Studio trên máy đó
./gradlew :app:assembleDebug
./gradlew :app:installDebug                         # cài lên máy thật đang cắm USB
```

Cấu trúc hiện tại:
- `MainActivity` chứa `NavHostFragment` (`res/layout/activity_main.xml`, `res/navigation/nav_graph.xml`).
- `ui/placeholder/PlaceholderFragment` là màn tạm, thay bằng Home ở giai đoạn 2.

- Icon app (20/11/2026, đổi sang logo Figma node 36:592 "Option 1", 512x512, ảnh cô gái + 3 món ăn có vương miện; build OK, đã cài lên Xiaomi, CHƯA nhìn trên màn hình chính):
  `ic_launcher_foreground` = ảnh 288/432px (đúng 72/108dp, vùng hiển thị) vì ảnh đã kín khung, phóng 81dp thì mất vương miện số 1 khi mask tròn;
  nền `ic_launcher_background` màu #B0E0FF (trung vị màu viền ảnh). `mipmap-*/ic_launcher.webp` = ảnh thu nhỏ, `ic_launcher_round.webp` = cắt tròn.
  Script tạo icon: `make_icon.py` ở scratchpad (Pillow), không có trong repo.
