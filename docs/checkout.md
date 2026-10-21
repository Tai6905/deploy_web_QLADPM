# Trang thanh toán ShopFlow

Từ giỏ hàng chọn **Tiến hành đặt hàng**, web mở `/checkout`. Khách cần đăng nhập; sau đăng nhập sẽ quay lại đúng trang thanh toán và giữ giỏ hàng.

## Thông tin nhận hàng

- Điền tên người nhận, số điện thoại, số nhà/đường/phường xã, quận huyện và tỉnh/thành phố; email liên hệ tùy chọn.
- Có thể lưu tối đa 10 địa chỉ theo tài khoản, thêm, sửa, xóa và đặt mặc định. Địa chỉ mặc định được tự điền ở lần mua sau, kể cả trên thiết bị khác sau đăng nhập.
- Bỏ chọn **Lưu thông tin nhận hàng vào tài khoản cho lần mua sau** nếu chỉ dùng thông tin này cho đơn hiện tại. Nút **Lưu địa chỉ này** là thao tác lưu riêng, có thể dùng trước khi đặt.
- Địa chỉ và thông tin liên hệ không được lưu trong localStorage. Backend giới hạn mọi thao tác địa chỉ theo người đang đăng nhập. Đơn hàng giữ bản chụp thông tin nhận hàng tại lúc đặt, nên sửa địa chỉ sau này không đổi đơn cũ.

## Voucher và tổng tiền

| Mã mẫu | Ưu đãi | Điều kiện |
| --- | --- | --- |
| `MOC10` | Giảm 10%, tối đa 50.000đ | Đơn từ 150.000đ |
| `MOC30K` | Giảm 30.000đ | Đơn từ 300.000đ |

Hai mã có hiệu lực từ năm 2026 đến hết ngày 31/12/2030 UTC, mỗi tài khoản tối đa 3 lượt/mã và toàn bộ chương trình tối đa 10.000 lượt/mã. Đây là ưu đãi mẫu phục vụ đồ án. Mỗi đơn dùng một voucher; tiền được tính bằng VND nguyên, phần trăm làm tròn xuống. Phí giao hàng của bản đồ án hiện là 0đ.

Backend tính giá từ sản phẩm hiện tại, kiểm tra hiệu lực/mức tối thiểu/lượt dùng và khóa voucher khi đặt. Đơn lưu riêng tạm tính, mã ưu đãi, tiền giảm và tổng thanh toán. Nếu tổng tiền đổi so với lúc khách xác nhận, yêu cầu đặt hàng được rollback và giao diện yêu cầu kiểm tra lại giá. Thanh toán mô phỏng thất bại hoặc được xử lý hết hạn sẽ trả lại lượt voucher của đơn; đơn đang chờ thanh toán giữ lượt đã đặt.

## Phương thức thanh toán

- **COD**: giữ tồn kho khi tạo đơn, chờ trả tiền khi nhận. Kho/nhân viên chuyển lần lượt Chuẩn bị → Đang giao → **Đã giao & thu tiền**. Bước cuối mới trừ tồn thực tế, ghi nhận thanh toán và doanh thu. API mô phỏng thanh toán không được đánh dấu đơn COD đã trả tiền trước đó.
- **Thẻ ngân hàng · mô phỏng** và **Chuyển khoản · mô phỏng**: tạo đơn chờ thanh toán và giữ hàng 15 phút. Khách bấm xác nhận thanh toán mô phỏng trên trang kết quả hoặc trong Đơn mua. Chưa tích hợp ngân hàng/cổng thanh toán; không yêu cầu nhập số thẻ hay chuyển tiền thật.

Nút đặt hàng bị khóa khi đang gửi. Mỗi lượt checkout có một khóa yêu cầu: gửi lại cùng nội dung trả về đơn đã tạo, không trừ thêm tồn hoặc dùng thêm voucher; dùng lại khóa với nội dung khác bị từ chối. Nếu tải lại trang sau lỗi mạng mà chưa biết đơn đã tạo hay chưa, nên kiểm tra Đơn mua trước khi đặt lại.

## Cấu trúc và triển khai

Migration thứ 11: `db/migrations/add_checkout_addresses_and_vouchers.sql`. Bổ sung các cột snapshot ưu đãi và khóa checkout vào `orders`, mở rộng phương thức thanh toán và thêm hai mã mẫu. Sử dụng bảng `user_addresses`, `vouchers`, `voucher_usages` đã có; không tạo lại tài khoản hoặc catalog.

`subtotal_amount` cho phép null để backend cũ vẫn tạo được đơn thẻ trong lúc triển khai; backend mới đọc giá trị cũ bằng tổng đơn và luôn ghi tạm tính cho đơn mới. Production vẫn không tự chạy migration. Cần áp dụng migration rồi redeploy backend Railway và frontend Vercel cùng bản mới.

API mới:

- `GET/POST /api/account/addresses`, `PUT/DELETE /api/account/addresses/{id}`.
- `GET /api/checkout/vouchers`, `POST /api/checkout/quote`, `POST /api/checkout/orders`.

Tất cả yêu cầu đăng nhập; các lệnh ghi yêu cầu CSRF. `backend/tools/CheckoutSmoke.java` chạy migration còn thiếu và kiểm tra checkout thật trên PostgreSQL trong transaction rollback, chỉ giữ thay đổi cấu trúc và hai voucher mẫu.

## Kết quả kiểm tra

- Đã áp dụng migration thứ 11 lên Aiven và tạo hai voucher mẫu. Không xóa dữ liệu cũ.
- 35 kiểm thử backend và 19 kiểm thử frontend thành công; TypeScript/Vite build và đóng gói JAR thành công.
- PostgreSQL: lưu địa chỉ, quote voucher không phân biệt hoa thường, chống trùng đơn/địa chỉ, COD giữ hàng rồi thu tiền lúc giao, snapshot địa chỉ không đổi khi sửa sổ địa chỉ, trả lượt voucher khi chuyển khoản mô phỏng thất bại và xác nhận thẻ mô phỏng đều thành công. Toàn bộ dữ liệu thử đã rollback.
- JAR production đọc được cấu hình môi trường Aiven, PORT và CORS cho Vercel; schema initializer vẫn tắt trong production. Java chạy kiểm tra là 24, mã build nhắm Java 21.
- HTTP qua frontend local: đăng nhập tài khoản có sẵn, đọc sổ địa chỉ, tải hai voucher, tính quote từ giá backend và mở route `/checkout` thành công; người chưa đăng nhập bị từ chối API checkout. Kiểm tra này không lưu thêm địa chỉ hoặc đơn thật.
- Bản local hiện ở `http://127.0.0.1:5174/customer`: thêm vào giỏ và bấm đặt hàng để mở trang mới. Cần push mã và redeploy Railway/Vercel để cập nhật giao diện online. Chưa kiểm tra trực quan bằng trình duyệt trong lượt này.
