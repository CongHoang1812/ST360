# Cơ chế đa màn hình hoạt động thế nào

## 1. Ý tưởng chung

- Thiết kế một lần ở điện thoại **360 × 800** (1px Figma = 1dp).
- Layout XML không có số cứng: mọi kích thước là `@dimen`, chữ là `@dimen` đơn vị `sp`.
- `res/values/dimens.xml` giữ giá trị điện thoại. Đây là **nguồn duy nhất** do người viết.
- Script Python đọc file đó, nhân với hệ số theo nhóm máy, rồi sinh:
  - `res/values-sw600dp/dimens.xml` và `integers.xml` (tablet 7–8", 600 × 960)
  - `res/values-sw800dp/dimens.xml` và `integers.xml` (tablet 10–11", 800 × 1280)
- Android tự chọn thư mục theo `smallestScreenWidthDp` của máy. Layout dùng chung, không cần `layout-sw600dp` trừ vài ca đặc biệt (mục 6).

```
values/dimens.xml  ──(script × hệ số)──►  values-sw600dp/dimens.xml
   (người viết)                          values-sw800dp/dimens.xml   (sinh tự động, không sửa tay)
values/integers.xml (số cột phone)       values-sw*/integers.xml     (số cột tablet, từ CONFIG)
```

Vì sao dùng `smallestScreenWidth` (sw) chứ không phải `w`: nó không đổi khi xoay máy, nên một máy luôn rơi vào một nhóm.

## 2. Hệ số và các ngoại lệ

Mỗi nhóm máy (`BUCKETS`) có `scale`. Chọn `scale ≤ rộng màn / 360` để mọi hàng ngang của bản điện thoại vẫn vừa:

| Nhóm | Màn tham chiếu | scale | Chiều cao dùng được |
|---|---|---|---|
| sw600dp | 600 × 960 | 1.6 | 960 − status 24 − nav 48 = 888 |
| sw800dp | 800 × 1280 | 2.0 | 1208 |

Mặc định mọi dimen = giá trị điện thoại × scale. Có ba cách lệch khỏi mặc định:

- **`fixed`** (theo nhóm): giá trị chọn tay, thay cho giá trị nhân scale. Dùng khi nhân scale làm màn tràn dọc, ví dụ chiều cao panel đáy.
- **`PHONE`**: dimen giữ nguyên giá trị điện thoại ở mọi nhóm. Dùng cho toạ độ dọc tuyệt đối mà nhân scale sẽ rơi ra ngoài màn.
- **`GROUPS`**: một nhóm dimen dùng chung một hệ số tính riêng (không phải `scale`). Ví dụ lưới ô lấp đầy chiều ngang: hệ số = `(rộng màn − 2·padding·scale) / (cột·ô + (cột−1)·khe)`; ô, khe và thumbnail cùng nhóm để ảnh nền (có bóng) và ảnh trong ô vẫn khớp nhau. Tab to hơn phần còn lại cũng làm bằng một nhóm với hệ số `scale × boost`.

Nên làm tròn: số ≥ 4 thì lấy nguyên, nhỏ hơn thì giữ một chữ số thập phân.

## 3. Màn xếp dọc cố định và `STACKS`

Tablet 16:10 **thấp hơn** điện thoại 20:9 theo tỉ lệ. Nhân đều theo chiều ngang (scale) sẽ làm chiều cao tăng nhiều hơn chỗ có sẵn, nên màn nào xếp dọc cố định (không cuộn, không co giãn) có thể tràn xuống dưới.

`STACKS` là danh sách các màn đó: mỗi màn là các dimen (hoặc số dp tối thiểu) cộng dồn theo chiều dọc từ dưới status bar. Bước `check` của script cộng tổng và so với chiều cao dùng được; vượt thì in dòng `!`:

```
  ! sw600dp home: cao 912 > 888
```

Cách sửa, theo thứ tự ưu tiên:
1. Đưa màn vào `STACKS` nếu chưa có (để script bắt).
2. Đặt `fixed` cho dimen lớn nhất trong chuỗi, hoặc `GROUPS`/`PHONE` cho dimen không nên nhân.
3. Đổi layout sang ràng buộc co giãn (`0dp` + constraint) cho phần nhân vật / ảnh để nó ăn phần dư.
4. Cuối cùng mới làm layout riêng.

Phần co giãn (ví dụ canvas nhân vật) đưa vào `STACKS` dưới dạng **số dp tối thiểu** (ví dụ `300`), để đảm bảo nó vẫn còn ít nhất chừng đó.

## 4. Lưới và số cột

Số cột `RecyclerView` không đi theo `scale`. Mỗi lưới có một `integer` (ví dụ `part_grid_columns`):

- `values/integers.xml`: số cột điện thoại.
- CONFIG `columns` của từng nhóm: số cột tablet, script sinh `values-sw*/integers.xml`.
- Code đọc bằng `resources.getInteger(R.integer.xxx_columns)`, **không** hardcode số cột trong Kotlin.

Với lưới lấp đầy chiều ngang, rộng ô được tính theo số cột (xem `GROUPS` ở mục 2), nên đổi số cột thì hệ số đổi theo. Script cũng kiểm tra tổng rộng lưới không vượt rộng màn.

## 5. Quy tắc khi viết layout / thêm màn

- Chỉ dùng `@dimen`, `@color`, `@string`. Ngoại lệ duy nhất cho số cứng là `0dp` (match constraint) và `0`.
- Dimen mới thêm vào `values/dimens.xml` (giá trị điện thoại), rồi **chạy lại script**. Không sửa tay `values-sw*`.
- Đặt tên theo vai trò (`permission_row_gap`), không theo số (`dp_19`).
- Màn xếp dọc cố định phải vào `STACKS`.
- Lưới mới: khai báo integer ở `values/` và `columns` trong CONFIG.
- Chữ dùng `sp` qua dimen `text_*`; chiều cao dòng qua `line_*` (cũng nhân scale).

## 6. Khi nào cần layout riêng

Chỉ khi bố cục **khác về cấu trúc** chứ không chỉ khác kích thước, và không biểu diễn được bằng dimen. Ví dụ ở app này là màn Intro: ở điện thoại, indicator và nút Next đặt cố định ở toạ độ dọc 640dp; ở tablet đặt theo **% chiều cao màn** bằng một `Guideline`. File `layout-sw600dp/fragment_intro.xml` (áp cho cả sw800dp vì Android lấy thư mục gần nhất nhỏ hơn) phải **giữ cùng view id** với bản `layout/`, nếu không ViewBinding sẽ lỗi.

## 7. Giới hạn

- Chỉ xử lý **cỡ màn**, không xử lý **hướng màn**: app khoá dọc nên chưa có xoay ngang, split-screen, màn gập.
- Tablet = điện thoại phóng to, không đổi bố cục. Kiểu hai cột (danh sách + chi tiết) cần layout riêng, ngoài phạm vi.
- Script chỉ bắt tràn chiều cao và tràn lưới, không bắt lỗi thẩm mỹ. Vẫn phải xem trên máy ảo / máy thật 600 × 960 và 800 × 1280.
