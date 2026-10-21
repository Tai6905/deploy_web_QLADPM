# Hủy đơn hàng ShopFlow

Đã kiểm tra ngày 21/10/2026: migration Aiven đã áp dụng; dữ liệu smoke test được rollback. 28 kiểm thử frontend và 45 kiểm thử backend đạt; build frontend và JAR thành công. Bản online cần deploy cả Railway và Vercel để có giao diện và API mới.

## Sử dụng

- Khách hàng: **Đơn mua của tôi → Hủy đơn** ở bảng danh sách hoặc phần chi tiết. Chọn/nhập lý do và nhấn **Xác nhận hủy đơn** trên trang riêng.
- Xem lịch sử: **Đơn đã hủy** trong menu tài khoản, hoặc `/cancellations?scope=mine`.
- Chủ shop / nhân viên bán hàng: nút **Hủy đơn** trong quản lý đơn hàng; mục **Đơn đã hủy** trong thanh bên hoặc `/cancellations` để xem đơn hủy của cả cửa hàng. Có thể chuyển sang lịch sử đơn mua của chính mình.
- Nhân viên kho có thể xem đơn hủy của chính mình; không được hủy hay xem đơn của khách hàng khác.

Trang lịch sử có tìm kiếm, phân trang, thời điểm hủy, lý do, bên thực hiện và kết quả xử lý thanh toán. Đơn cũ và các dòng sản phẩm được giữ nguyên, không xóa khỏi lịch sử mua hàng.

## Quy tắc

Chỉ hủy trước khi bàn giao vận chuyển: trạng thái giao hàng `NONE` hoặc `PREPARING`. Không hủy đơn `SHIPPED` / `DELIVERED`; đơn đã nhận dùng luồng đổi trả hiện có.

Hủy đơn chờ thanh toán giải phóng stock hold. Hủy COD đang chuẩn bị hoặc đơn đã thanh toán thử nghiệm giải phóng `reserved_quantity`, không tăng số lượng vật lý trong kho. Lượt voucher của đơn được trả lại; voucher vẫn phải đáp ứng thời hạn, trạng thái và các điều kiện khác khi dùng lần sau.

CARD/BANK_TRANSFER của đồ án là thanh toán mô phỏng. Với đơn thử nghiệm đã thanh toán thành công, việc hủy ghi `REFUNDED` cùng số tiền giảm giá thực tế đã thanh toán và giữ nguyên thời điểm thu tiền ban đầu. Giao diện ghi rõ **hoàn tiền mô phỏng**, không chuyển tiền qua ngân hàng. Muốn triển khai thanh toán thật cần tích hợp quy trình hoàn tiền và đối soát với cổng thanh toán.

Thanh toán thất bại được hiển thị riêng với hủy đơn. Nếu khách chủ động hủy một đơn thanh toán lỗi, lý do thất bại trước đó vẫn được giữ và không giải phóng kho lần thứ hai.

Hủy được xử lý trong một transaction với thứ tự khóa order → payment → inventory → voucher. Hủy lặp trả lại kết quả ban đầu, giữ nguyên lý do/thời điểm và không trả kho/voucher/hoàn tiền hai lần. Nếu đơn đã được bàn giao trong lúc khách đang nhập lý do, API trả HTTP 409 và trang tải lại trạng thái mới. Nếu dữ liệu giữ hàng thiếu hoặc lệch, toàn bộ thao tác rollback để cửa hàng kiểm tra.

## Schema và deploy

Migration: `db/migrations/add_order_cancellations.sql`. Script thêm bảng audit `order_cancellations`, chỉ mục và mở rộng các CHECK constraint cho trạng thái mới. Nó không hủy hay xóa bất kỳ đơn hiện có nào.

Sau khi build/test backend và có cấu hình Aiven trong file riêng `backend/application-local.properties`, chạy từ thư mục dự án:

```powershell
.\scripts\check-order-cancellations.ps1
```

Công cụ áp dụng migration còn thiếu, kiểm tra các trường hợp CARD, COD, BANK_TRANSFER và chặn hủy sau bàn giao trên PostgreSQL. Tất cả tài khoản, sản phẩm, voucher, đơn và audit dùng để kiểm tra đều được rollback; không sử dụng đơn thật. Schema được giữ lại để backend mới hoạt động.

Production tắt schema initializer: cần áp dụng migration **trước khi deploy backend mới**. Sau đó deploy cả Railway và Vercel. `/cancellations` dùng SPA fallback hiện có.

## API

| API | Quyền |
| --- | --- |
| `POST /api/account/orders/{id}/cancel` | Tài khoản đã đăng nhập, đơn thuộc chính tài khoản đó |
| `POST /orders/{id}/cancel` | Chủ shop / nhân viên bán hàng |
| `GET /api/account/orders?status=CANCELLED` | Lịch sử của chính tài khoản |
| `GET /orders?status=CANCELLED` | Chủ shop / nhân viên bán hàng, toàn cửa hàng |
| `GET /orders/{id}` | Chủ shop / nhân viên bán hàng |

Body hủy: `{ "reason": "Đặt trùng đơn" }`, từ 1–500 ký tự, bắt buộc CSRF. Tham số tìm kiếm `q`, `page` (từ 0) và `size` được áp dụng cùng trạng thái trước khi phân trang. Thay `scope` trong URL giao diện không mở thêm quyền API.
