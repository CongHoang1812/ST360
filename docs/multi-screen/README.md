# Bộ tài liệu: hỗ trợ đa màn hình (điện thoại + tablet)

Cách app này chạy được trên điện thoại 360dp, tablet 7–8" (≥600dp) và tablet 10–11" (≥800dp) mà **không viết layout riêng cho từng màn**. Bộ tài liệu này gói cách làm đó để áp vào app Android khác (giao diện khác, màn hình khác).

## Trả lời nhanh

- **Làm bằng gì:** thiết kế một lần ở 360 × 800, mọi kích thước là `@dimen`. Script Python nhân dimen điện thoại với hệ số theo nhóm máy, sinh ra `values-sw600dp/` và `values-sw800dp/`. Android tự chọn thư mục theo `smallestScreenWidth`. Layout XML dùng chung.
- **Áp vào app khác được không:** được, với app Android XML + ViewBinding (không Compose) có thiết kế một cỡ điện thoại. Phần dùng lại được là phương pháp và script; phần phải làm lại cho mỗi app là bảng số (hệ số, màn xếp dọc, số cột lưới).
- **Không áp thẳng được khi:** app dùng Compose, hoặc đã hardcode `dp` rải rác trong layout (phải dọn trước, xem `02_PORTING_GUIDE.md` bước 1).

## Đọc gì

| File | Dùng khi |
|---|---|
| `01_HOW_IT_WORKS.md` | Muốn hiểu cơ chế, hoặc giải thích cho người khác |
| `02_PORTING_GUIDE.md` | Làm tay / kiểm tra từng bước khi mang sang app khác |
| `03_PROMPTS.md` | Copy prompt dán vào Claude Code ở project đích |
| `gen_screen_dimens.template.py` | Script mẫu đã tách cấu hình, chép sang project đích |

## Quy trình ngắn

1. Chép cả thư mục `docs/multi-screen/` sang project đích (và `gen_screen_dimens.template.py` thành `scripts/gen_screen_dimens.py`).
2. Mở Claude Code ở project đích, dán Prompt A (kiểm tra) trong `03_PROMPTS.md`.
3. Dán Prompt B (áp dụng), rồi Prompt C (kiểm tra tràn màn) trên máy ảo / máy thật.
4. Từ đó về sau mỗi màn mới: Prompt D.

## Giới hạn cần biết

- App này khoá dọc (`screenOrientation="portrait"`). Chưa xử lý xoay ngang, chia đôi màn hình (split-screen), màn gập, vì vậy bộ này chỉ giải quyết **cỡ màn**, không giải quyết **hướng màn**.
- Hệ số nhân là đơn giản có chủ đích: tablet chỉ là điện thoại phóng to, không đổi bố cục. Giao diện kiểu hai cột (danh sách + chi tiết) cần layout riêng, ngoài phạm vi.
- Tablet 16:10 thấp hơn điện thoại 20:9 nên màn xếp dọc cố định dễ tràn; script có bước `check` để bắt. Vẫn phải xem trên máy thật trước khi phát hành.
