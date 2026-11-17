# Mang cơ chế đa màn hình sang app khác

Làm tay hoặc đối chiếu khi Claude làm. Đọc `01_HOW_IT_WORKS.md` trước nếu chưa nắm cơ chế.

Điều kiện: app XML + ViewBinding (không Compose), có một thiết kế điện thoại gốc.

## Bước 1. Dọn số cứng trong layout

Mục tiêu: không còn `dp` / `sp` viết thẳng trong layout, drawable, style. Phần phải làm trước vì script chỉ nhân được thứ nằm trong `values/dimens.xml`.

Đếm và liệt kê:

```bash
grep -rnE '"-?[0-9.]+(dp|sp)"' app/src/main/res/layout app/src/main/res/drawable app/src/main/res/values/styles.xml app/src/main/res/values/themes.xml
```

(Trên Windows PowerShell dùng `Select-String -Pattern '"-?[0-9.]+(dp|sp)"' -Path ... -Recurse`.)

Với mỗi chỗ:
- Chuyển thành `@dimen/ten_theo_vai_tro` có **đúng giá trị cũ**, không làm tròn lại. Giao diện điện thoại không được đổi.
- Dimen đã có cùng vai trò thì dùng lại.
- `0dp` giữ nguyên.
- Đừng quên `layout_margin*`, `padding*`, `textSize`, `lineHeight`, `cornerRadius`, `strokeWidth`, `<corners>`, `<stroke>`, `<size>` trong drawable, và kích thước đặt trong Kotlin (`dpToPx(..)`).

Báo cáo số chỗ đã đổi trước khi sang bước 2.

## Bước 2. Gom số cột lưới vào integer

Với mỗi `GridLayoutManager(context, N)` hoặc `spanCount`:

1. Thêm `values/integers.xml`: `<integer name="xxx_grid_columns">N</integer>`.
2. Sửa code: `resources.getInteger(R.integer.xxx_grid_columns)`.

## Bước 3. Chép script và điền CONFIG

1. Chép `gen_screen_dimens.template.py` thành `scripts/gen_screen_dimens.py`.
2. Điền phần CONFIG ở đầu file:
   - `BUCKETS`: rộng, `usable_h`, `scale`, `fixed` (để trống ban đầu), `columns` cho từng nhóm. Mặc định sw600dp = 600 × 960, scale 1.6; sw800dp = 800 × 1280, scale 2.0. Bề rộng thiết kế khác 360 thì sửa `DESIGN_W`.
   - `PHONE`: dimen giữ nguyên (ban đầu để trống).
   - `GROUPS`: nhóm dimen có hệ số riêng, ví dụ lưới lấp đầy ngang (để trống nếu chưa cần).
   - `STACKS`: màn xếp dọc cố định. Lập từ danh sách ứng viên ở Prompt A.

## Bước 4. Chạy script và xử lý cảnh báo

```bash
python3 scripts/gen_screen_dimens.py     # Windows: python scripts/gen_screen_dimens.py
```

Script sinh `values-sw600dp/` và `values-sw800dp/`. Dòng bắt đầu bằng `!` là màn tràn: sửa `fixed` / `PHONE` / `GROUPS` hoặc đổi layout sang co giãn (thứ tự ưu tiên ở `01_HOW_IT_WORKS.md` mục 3), chạy lại đến khi hết `!`.

Báo cáo kết quả chạy script (số dimen mỗi nhóm, các cảnh báo còn lại).

## Bước 5. Nối số cột cho tablet

Điền `columns` trong từng nhóm `BUCKETS` cho **mọi** integer đã tạo ở bước 2 (thiếu thì tablet rơi về giá trị điện thoại do không có file ghi đè). Chạy lại script.

## Bước 6. Layout riêng (chỉ khi cần)

Chỉ tạo `layout-sw600dp/<tên>.xml` khi không biểu diễn được bằng dimen (ví dụ đặt theo %, hoặc đổi cấu trúc). Nguyên tắc:
- Cùng view id với bản `layout/`.
- Ghi chú đầu file nêu lý do.
- Mỗi file riêng là một chỗ phải sửa hai lần khi đổi UI, nên giữ tối thiểu.

## Bước 7. Xem trên máy

Máy ảo hoặc máy thật: 600 × 960 (Nexus 7), 800 × 1280 (Pixel Tablet), cộng một điện thoại 360 × 800 để chắc chắn không đổi so với trước. Đi qua mọi màn xếp dọc cố định và mọi lưới.

## Bước 8. Ghi vào CLAUDE.md của project

Thêm 3 dòng (sửa tên cho đúng app):

```
- Đa màn hình: layout chỉ dùng @dimen; dimen điện thoại ở values/dimens.xml. Không sửa tay values-sw600dp / values-sw800dp.
- Thêm / đổi dimen thì chạy `python3 scripts/gen_screen_dimens.py`; có dòng "!" thì chỉnh `fixed` / `STACKS`.
- Màn xếp dọc cố định phải vào STACKS; số cột lưới lấy từ R.integer.*_columns.
```

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| Điện thoại đổi giao diện sau khi dọn | Chuyển số cứng sang dimen nhưng làm tròn khác | Trả đúng giá trị cũ |
| Tablet tràn dọc, nút cuối mất | Màn xếp cố định chưa vào `STACKS` hoặc `fixed` chưa đủ | Thêm `STACKS`, đặt `fixed`, hoặc để phần nhân vật co giãn |
| Tablet có khoảng trống lớn dưới | Màn có chỗ dư vì tablet cao hơn dự tính, mọi thứ bám trên | Cho phần ảnh/nhân vật co giãn, hoặc đặt theo % bằng Guideline |
| Chữ bị cắt | Ô cố định nhưng chữ ×scale | Cho ô `wrap_content` / đặt `minHeight`, hoặc giảm scale chữ |
| Lưới lệch, ảnh nền và thumbnail không khớp | Hai dimen cùng ô nhưng khác hệ số | Đưa chúng vào cùng một `GROUPS` |
| Tablet vẫn hiện số cột của điện thoại | Thiếu `columns` trong CONFIG, hoặc code vẫn hardcode | Bước 2 và 5 |
| ViewBinding báo thiếu view khi build tablet | Layout riêng khác view id so với bản gốc | Đồng bộ id |
| Sửa tay `values-sw*` rồi mất khi chạy script | File sinh tự động ghi đè | Sửa CONFIG, không sửa file sinh |
| Máy 768 × 1024 hoặc 720 × 1280 hơi lạ | Rơi vào sw600dp nhưng tỉ lệ khác 16:10 | Xem thêm; ưu tiên màn co giãn thay vì thêm nhóm |
