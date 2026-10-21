# Hướng phát triển website nhạc cụ ShopFlow

ShopFlow bán nhạc cụ: guitar acoustic, guitar điện, piano, trống, violin và kèn. Catalog, nội dung giới thiệu và đề xuất phát triển cần bám vào nhu cầu học, luyện tập và biểu diễn âm nhạc.

## Hoàn thiện mua hàng và vận hành

| Ưu tiên | Chức năng | Phạm vi |
| --- | --- | --- |
| 1 | Chủ shop quản lý voucher | Tạo/sửa/tắt mã, thời hạn, điều kiện và lượt dùng. Checkout đã có áp voucher và kiểm tra ở backend. |
| 2 | Yêu thích và mua lại | Lưu nhạc cụ quan tâm theo tài khoản, mua lại từ đơn trước với giá/tồn hiện tại. |
| 3 | Hủy đơn có điều kiện | Giới hạn mốc hủy, hoàn giữ hàng/tồn và lượt voucher đúng một lần, giữ lịch sử trạng thái. |
| 4 | Thông báo trong web | Trạng thái đơn, đổi trả và nhạc cụ có hàng trở lại. |
| 5 | Quản lý sản phẩm đầy đủ | Sửa tên, giá, danh mục, thông số, ảnh; lưu lịch sử giá và ngừng bán mà vẫn giữ đơn cũ. |
| 6 | Báo cáo nhạc cụ | Bán chạy theo dòng đàn, hàng tồn lâu, nhu cầu nhập và giá vốn tại thời điểm bán. |

Trang thanh toán đã có sổ địa chỉ theo tài khoản, voucher, COD và thẻ/chuyển khoản mô phỏng. Tích hợp ngân hàng thực tế cần giai đoạn riêng cho xác minh webhook, xử lý lặp và hoàn tiền.

## Trải nghiệm riêng cho cửa hàng nhạc cụ

### Chọn nhạc cụ theo mục tiêu và ngân sách

Khách chọn nhạc cụ muốn học, mức độ kinh nghiệm, phong cách âm nhạc, nơi luyện tập và ngân sách. Web đưa ra vài lựa chọn kèm lý do, cỡ đàn và thông tin cần kiểm tra trước khi mua. MVP có thể dùng tag và bộ quy tắc do chủ shop cấu hình, chỉ gợi ý hàng đang còn.

### Bộ nhạc cụ khởi đầu

Ghép nhạc cụ với phụ kiện cần thiết: guitar điện + ampli + cáp, acoustic + bao + tuner, violin + vĩ + hộp theo cấu hình thực tế. Hiển thị rõ món nào đã kèm sẵn để không mua trùng. Backend phải kiểm tra tương thích, giá và tồn từng thành phần khi đặt.

### Đăng ký tư vấn hoặc thử nhạc cụ

Khách chọn nhạc cụ và giờ hẹn còn trống, chủ shop xác nhận lịch. Có thể ghi cỡ đàn, tay thuận, trình độ hoặc yêu cầu vận chuyển. Chỉ cam kết dịch vụ khi cửa hàng thực tế có cung cấp.

### Thẻ chăm sóc nhạc cụ sau mua

Trong Đơn mua có hướng dẫn vệ sinh/bảo quản, lịch sử hỗ trợ và phụ kiện thay thế phù hợp. QR mở hướng dẫn công khai; dữ liệu đơn vẫn cần đăng nhập. Nguồn gốc, thông số và bảo hành chỉ ghi khi có dữ liệu xác nhận.

Thứ tự phù hợp đồ án: quản lý voucher → yêu thích/hủy đơn → chọn nhạc cụ → bộ khởi đầu. Mô hình combo có thể tham khảo [Shopify Product bundles](https://help.shopify.com/en/manual/products/bundles); phần đặc thù của ShopFlow là nhu cầu chơi nhạc và độ tương thích nhạc cụ/phụ kiện.
