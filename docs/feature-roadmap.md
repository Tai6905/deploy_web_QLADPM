# Hướng phát triển Mộc Studio

Các mục dưới đây là đề xuất cho giai đoạn tiếp theo, chưa được triển khai trong lượt bổ sung dữ liệu sản phẩm.

## Hoàn thiện trải nghiệm mua hàng

| Ưu tiên | Chức năng | Phạm vi cần làm |
| --- | --- | --- |
| 1 | Danh sách yêu thích và địa chỉ đã lưu | Lưu theo tài khoản, đặt địa chỉ mặc định, chọn nhanh lúc thanh toán; khách chỉ đọc/sửa dữ liệu của mình. |
| 2 | Mã giảm giá và phí giao hàng | Kiểm tra điều kiện/hạn dùng/số lượt ở backend, hiển thị cách tính tổng tiền, giữ nguyên giá và ưu đãi của đơn sau khi tạo. Database đã có bảng voucher nhưng cần API và giao diện nghiệp vụ. |
| 3 | Hủy đơn và thông báo trạng thái | Xác định mốc được hủy, giải phóng giữ hàng hoặc hoàn tồn đúng một lần; thông báo trong web khi đơn, đổi trả hoặc nhập lại hàng đổi trạng thái. |
| 4 | Ảnh đánh giá và hỏi đáp sản phẩm | Khách đã nhận hàng được đính kèm ảnh; chủ shop trả lời câu hỏi. Cần lưu ảnh và công cụ xử lý nội dung thay vì chỉ thêm ô nhập URL. |

Thanh toán hiện là mô phỏng cho đồ án. Nếu chuyển sang bán hàng thật, cần một giai đoạn riêng để tích hợp cổng thanh toán, xác minh callback/webhook, chống xử lý lặp và xử lý hoàn tiền.

## Ba trải nghiệm tạo dấu ấn cho Mộc Studio

### Phối góc Mộc theo ngân sách

Khách chọn góc học tập/góc đọc sách/góc thư giãn, phong cách và ngân sách. Web tạo 2–3 bộ sản phẩm gồm đèn, cốc, sổ hoặc nến, giải thích vì sao hợp nhau, tổng tiền và cho thay từng món trước khi thêm cả bộ vào giỏ.

MVP có thể dùng các tag và bộ quy tắc do chủ shop cấu hình: loại góc sống, màu, kích thước, mức giá và độ tương thích. Chỉ gợi ý món đang còn hàng. Backend phải kiểm tra giá, khuyến mãi và tồn của từng thành phần khi đặt, đặc biệt nếu sau này có giá combo.

### Chọn quà trong 30 giây

Trả lời 3 câu: tặng ai, dịp gì, ngân sách bao nhiêu. Hiển thị vài gợi ý kèm lý do, lựa chọn gói quà và lời nhắn. Cho phép người nhận mở lời nhắn qua một link riêng không tiết lộ giá hoặc thông tin tài khoản người gửi.

MVP dùng cùng tag sản phẩm với Phối góc Mộc, giảm lượng dữ liệu chủ shop cần quản lý. Có thể bắt đầu bằng quy tắc, sau đó bổ sung cá nhân hóa khi có dữ liệu sử dụng đủ tốt.

### Hộ chiếu chăm sóc món đồ

Sau khi mua, khách có thẻ sản phẩm gồm hướng dẫn riêng, ngày nhận, lịch sử hỗ trợ/đổi trả và nút mua lại. QR trên phiếu giao hoặc thẻ quà mở hướng dẫn công khai; dữ liệu đơn mua vẫn yêu cầu đăng nhập. Các mốc bảo hành, nguồn gốc và vật liệu chỉ hiển thị khi chủ shop có thông tin xác nhận.

## Thứ tự triển khai phù hợp đồ án

1. Hoàn thiện địa chỉ lưu sẵn, yêu thích và mã giảm giá để luồng mua hàng trọn vẹn hơn.
2. Làm **Phối góc Mộc** làm tính năng trình diễn chính: có thể trình bày rõ đầu vào, thuật toán chọn bộ, ràng buộc ngân sách và kết quả.
3. Dùng lại dữ liệu tag để làm **Chọn quà nhanh**; sau đó mở rộng thẻ chăm sóc và thông báo.

Nên đo kết quả bằng số khách hoàn thành tạo set, thêm set vào giỏ và đặt được đơn; chưa cần đặt mục tiêu tối ưu doanh thu khi dữ liệu vẫn là mẫu.

Combo và gợi ý mua kèm đã là những tính năng thương mại phổ biến, được mô tả trong [Shopify Product bundles](https://help.shopify.com/en/manual/products/bundles) và [Shopify product recommendations](https://help.shopify.com/en/manual/online-store/storefront-search/search-and-discovery-recommendations). Phần đề xuất riêng cho dự án là cách ghép theo góc sống, ngân sách và nhu cầu tặng quà; cần kiểm chứng với người dùng thay vì tuyên bố độc nhất trên thị trường.
