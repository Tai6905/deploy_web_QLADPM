# ShopFlow: sản phẩm, nhập hàng và đánh giá

## Sử dụng

- **Chủ shop → Sản phẩm → Thêm sản phẩm**: nhập giá vốn ban đầu, giá bán, mô tả, hướng dẫn sử dụng và tối đa 8 đường link ảnh. Mỗi dòng một URL HTTP/HTTPS; ảnh đầu tiên làm ảnh đại diện. Đây là gắn link ảnh, chưa phải tải file lên máy chủ.
- **Sửa sản phẩm đã đăng**: mở chi tiết sản phẩm → **Mở trang sản phẩm / chỉnh sửa nội dung** → **Chỉnh sửa ảnh & nội dung**. Có thể thay toàn bộ danh sách ảnh, mô tả và hướng dẫn. Chỉ chủ shop được lưu thay đổi.
- **Nhập hàng**: bắt buộc nhập đơn giá mua bằng VND nguyên không âm. Phiếu hiển thị số lượng nhập, đơn giá, thành tiền, nhà cung cấp và thời gian. Giá của phiếu cũ không đổi khi nhập đợt tiếp theo.
- **Tổng quan**: tiền nhập hàng tính theo khoảng thời gian đang chọn; giá trị tồn kho ước tính bằng tồn thực tế nhân giá nhập gần nhất của từng sản phẩm. Đây chưa phải báo cáo lợi nhuận hoặc giá vốn bình quân. Phiếu nhập từ trước bản nâng cấp được ghi là **chưa ghi giá**, không tự suy ra giá lịch sử.
- **Cửa hàng**: tìm theo tên, SKU, danh mục, hỗ trợ tìm tiếng Việt không dấu; lọc danh mục, giá, số sao và còn hàng; sắp xếp mới nhất, giá tăng/giảm hoặc đánh giá. Bộ lọc chạy tại backend cùng phân trang.
- **Trang sản phẩm** `/product/:id`: xem bộ ảnh và phóng to, giới thiệu, hướng dẫn, tồn khả dụng, chọn số lượng và thêm vào giỏ chung. Link ảnh hỏng có ảnh thay thế.
- **Đánh giá**: khách hàng có đơn đã thanh toán và giao thành công mới được đánh giá sản phẩm đã mua. Mỗi tài khoản có một đánh giá cho mỗi sản phẩm và có thể cập nhật. API công khai chỉ hiển thị tên, sao, nội dung, thời gian; không hiển thị email.
- **Về chúng tôi** `/about` và **Cẩm nang mua sắm** `/guide` có thể mở khi chưa đăng nhập. Thương hiệu hiển thị thống nhất là **ShopFlow**; các khóa lưu trữ và tên gói kỹ thuật cũ được giữ để không làm mất giỏ hàng.

## Database và triển khai

Migration mới: `db/migrations/enhance_storefront_and_receiving.sql`. Script bổ sung `products.usage_instructions`, `receiving_records.unit_cost`, bảng `product_reviews` và các index. Không xóa dữ liệu cũ.

Backend production không tự chạy migration. Cần áp dụng migration bằng schema initializer của dự án trước khi triển khai mã mới. Công cụ `backend/tools/OnlineSmoke.java` áp dụng script còn thiếu rồi kiểm tra nghiệp vụ trong transaction rollback; nó cần cấu hình Aiven cục bộ đã có và classpath của bản backend mới build. Không chạy riêng SQL lần nữa nếu `schema_migrations` đã ghi nhận script.

Sau khi schema sẵn sàng, đưa mã nguồn lên GitHub rồi redeploy backend Railway và frontend Vercel theo [hướng dẫn triển khai](deploy-vercel-railway.md). Giữ `VITE_API_BASE_URL=/backend` để frontend dùng proxy cùng domain. Không đưa file cấu hình mật khẩu cục bộ lên GitHub.

Thanh toán hiện vẫn là luồng mô phỏng phục vụ đồ án. Ảnh, mô tả và hướng dẫn thực tế cần được chủ shop nhập cho các sản phẩm cũ.

## Trạng thái kiểm tra của bản nâng cấp

- Aiven kết nối qua SSL; `schema_migrations` đã ghi nhận đủ 10 script, bao gồm `enhance_storefront_and_receiving.sql`.
- Backend: 32 kiểm thử thành công và đóng gói JAR thành công. Frontend: 15 kiểm thử thành công, TypeScript và Vite build thành công.
- Kiểm tra PostgreSQL thực tế: tạo/sửa bộ ảnh và hướng dẫn, nhận hàng có đơn giá, cập nhật giá nhập gần nhất, đặt hàng/thanh toán/giao hàng, đánh giá và cập nhật đánh giá, lọc kết hợp và thống kê chi phí đều thành công. Dữ liệu nghiệp vụ thử được rollback; số bản ghi trước/sau không đổi.
- Kiểm tra HTTP qua frontend: 4 vai trò đăng nhập, hồ sơ, lịch sử đơn, quyền truy cập và đăng xuất; API chi tiết/đánh giá, trang công khai và bộ lọc sai đều trả kết quả mong đợi. Sản phẩm cũ chưa có hướng dẫn vẫn đọc được, giá vốn không có trong JSON công khai.
- JAR production khởi động được với biến môi trường Aiven và cổng Railway; CORS chấp nhận domain frontend đã cấu hình và chặn origin lạ. Chạy kiểm tra trên Java 24, mã build nhắm Java 21. Chưa triển khai mã mới lên Railway/Vercel trong lượt này.
- Chưa kiểm tra trực quan bằng trình duyệt do công cụ trình duyệt không khả dụng. Cần rà lại giao diện thực tế trên máy tính/điện thoại khi mở bản xem trước.

Phiên xem trước hiện dùng `http://127.0.0.1:5174/customer` do cổng 5173 đang được dự án khác sử dụng. Backend chạy cổng 18081; origin 5174 được cho phép bằng tham số chạy của phiên local, không thay cấu hình production.
