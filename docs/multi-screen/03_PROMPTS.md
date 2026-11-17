# Prompt dán vào Claude Code ở project đích

Cách dùng: chép thư mục `docs/multi-screen/` vào project đích trước (giữ nguyên tên file, prompt tham chiếu theo đường dẫn này). Dán từng prompt theo thứ tự A → B → C. Prompt D dùng mỗi khi thêm màn.

Thay các chỗ trong `[ ]` trước khi dán.

---

## Prompt A. Kiểm tra khả năng áp dụng (chỉ đọc, chưa sửa)

```
Đọc docs/multi-screen/README.md, 01_HOW_IT_WORKS.md và 02_PORTING_GUIDE.md.
Project này là app Android; thiết kế điện thoại gốc rộng [360] dp, khoá [dọc].

Chưa sửa gì. Kiểm tra và báo cáo ngắn:
1. UI là XML + ViewBinding hay Compose? (Compose thì dừng và nói rõ.)
2. Đếm số chỗ hardcode dp/sp trong res/layout, res/drawable, styles.xml
   (lệnh grep ở bước 1 của guide), liệt kê 10 chỗ tiêu biểu.
3. Đã có values-sw600dp / values-sw800dp / layout-sw600dp chưa? Nội dung ra sao?
4. Liệt kê màn xếp dọc cố định (không cuộn, không co) làm ứng viên cho STACKS.
5. Liệt kê màn dùng RecyclerView lưới, số cột đang đặt ở đâu (code hay XML).
6. Ước lượng công việc: số file layout phải sửa, màn nào có thể cần layout riêng.

Kết thúc bằng khuyến nghị: làm / không làm, và rủi ro lớn nhất.
```

---

## Prompt B. Áp dụng cơ chế

```
Áp dụng cơ chế đa màn hình theo docs/multi-screen/02_PORTING_GUIDE.md, các bước 1 đến 6.
Bề rộng thiết kế: [360]dp. Nhóm máy: sw600dp (600x960, scale 1.6) và sw800dp (800x1280, scale 2.0).

Quy tắc:
- Không đổi giao diện điện thoại: sau khi dọn, mọi giá trị điện thoại phải bằng đúng số cũ
  (chuyển số cứng thành @dimen cùng giá trị, không làm tròn lại).
- Không đổi logic, ViewModel, id của view.
- Tên dimen đặt theo vai trò, theo kiểu đã có trong values/dimens.xml. Dimen nào đã có sẵn thì dùng lại.
- Chép docs/multi-screen/gen_screen_dimens.template.py thành scripts/gen_screen_dimens.py,
  điền CONFIG (BUCKETS, columns, PHONE, GROUPS, STACKS). Không sửa tay values-sw*.
- Số cột lưới chuyển sang values/integers.xml và đọc bằng resources.getInteger(...).
- Chỉ tạo layout-sw600dp khi thật sự không biểu diễn được bằng dimen; nói rõ lý do từng file.

Làm theo từng bước và dừng báo cáo sau mỗi bước 1, 3, 4 (số chỗ đã đổi, kết quả chạy script).
Hỏi tôi nếu một màn cần quyết định bố cục tablet.

Cuối cùng cập nhật CLAUDE.md của project (3 dòng ở bước 8 của guide). Không tự build / chạy test
trừ khi tôi bảo; chạy script Python thì được.
```

---

## Prompt C. Xử lý màn tràn / lệch trên tablet

```
Tôi chạy thử trên tablet và thấy lỗi:
- Máy: [Pixel Tablet 800x1280 / Nexus 7 600x960]
- Màn: [tên màn]
- Hiện tượng: [tràn dọc / chữ bị cắt / lưới lệch / khoảng trống dưới]
[đính kèm ảnh chụp]

Đọc docs/multi-screen/01_HOW_IT_WORKS.md mục 2, 3 và bảng "Lỗi thường gặp" trong 02_PORTING_GUIDE.md.
Xác định nguyên nhân trong layout hoặc CONFIG của scripts/gen_screen_dimens.py, đề xuất cách sửa
theo thứ tự ưu tiên: (1) thêm vào STACKS, (2) fixed / GROUPS, (3) đổi sang constraint co giãn,
(4) layout riêng. Giải thích vì sao chọn mức đó, rồi sửa, chạy lại script.
Không đổi giá trị điện thoại.
```

---

## Prompt D. Thêm màn mới mà vẫn đa màn hình

```
Thêm màn [tên màn] vào app. [Mô tả / Figma node.]

Yêu cầu đa màn hình (docs/multi-screen/01_HOW_IT_WORKS.md mục 5):
- Layout chỉ dùng @dimen, @color, @string; không số cứng ngoài 0dp.
- Dimen mới thêm vào values/dimens.xml; chạy lại scripts/gen_screen_dimens.py.
- Nếu màn xếp dọc cố định (không cuộn, không co), thêm vào STACKS và sửa fixed đến hết cảnh báo.
- Lưới RecyclerView: số cột qua R.integer.[tên]_columns, khai báo cả values/ và CONFIG columns.
- Báo cuối cùng: dimen đã thêm, màn có nằm trong STACKS không, có cần xem trên tablet không.
```

---

## Prompt E. Một lệnh cho app maker cùng họ (clone từ repo này)

Dùng khi app đích **được clone từ code base maker này** (đã có script, dimens, layout-sw600dp sẵn). Khi đó không phải áp dụng cơ chế, chỉ giữ cho nó đúng sau khi đổi giao diện.

```
Vừa reskin app theo Figma mới. Hãy giữ đa màn hình chạy đúng:
1. Chạy scripts/gen_tablet_dimens.py; nếu có dòng "!" thì chỉnh `fixed` / `STACKS` đến hết.
2. Đối chiếu STACKS với layout hiện tại: màn nào đổi cấu trúc dọc (thêm / bớt phần tử) thì cập nhật danh sách dimen.
3. Màn mới thêm: có trong STACKS chưa? Có hardcode dp nào không? (grep ở bước 1 của 02_PORTING_GUIDE.md)
4. layout-sw600dp/fragment_intro.xml: cùng view id với layout/fragment_intro.xml chưa?
Báo kết quả từng mục. Không tự build / chạy test.
```

---

## Mẹo viết prompt cho từng app

- Nói rõ **bề rộng thiết kế** và **hướng màn**; hai thứ này quyết định mọi hệ số.
- Đính ảnh thiết kế của 2–3 màn xếp dọc cố định nhất; Claude lập `STACKS` chính xác hơn khi thấy.
- Nếu giao diện app đích rất khác (ví dụ không có panel đáy), đừng bảo Claude "làm giống app mẫu"; bảo nó áp dụng **phương pháp** rồi tự tìm các màn xếp cố định của app đó.
- Yêu cầu dừng sau mỗi bước lớn: dễ bắt lỗi sớm hơn so với sửa cả loạt layout một lần.
