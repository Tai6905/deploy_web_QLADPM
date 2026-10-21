# Catalog nhạc cụ ShopFlow

Bộ `moc-studio-catalog-v2` dùng cho website bán nhạc cụ. Có 24 sản phẩm thuộc 6 danh mục: guitar acoustic, guitar điện, piano, trống acoustic, violin và trumpet.

Đây là dữ liệu mẫu phục vụ đồ án: tên mẫu Mộc, giá vốn, giá bán, thông số và ảnh minh họa cần được xác nhận với nhạc cụ thực tế trước khi kinh doanh. Mô tả công khai của từng sản phẩm có ghi chú này.

## Danh sách nhạc cụ

| SKU | Nhạc cụ | Giá bán mẫu | Giá vốn mẫu | Tồn ban đầu |
| --- | --- | ---: | ---: | ---: |
| MOC-ACOU-001 | Guitar acoustic Mộc A100 | 2.790.000 ₫ | 1.900.000 ₫ | 12 |
| MOC-ACOU-002 | Guitar acoustic Mộc A200 | 3.890.000 ₫ | 2.700.000 ₫ | 10 |
| MOC-ACOU-003 | Guitar acoustic Mộc A300 | 5.190.000 ₫ | 3.600.000 ₫ | 6 |
| MOC-ACOU-004 | Guitar acoustic Mộc A400 | 6.890.000 ₫ | 4.800.000 ₫ | 3 |
| MOC-ELEC-001 | Guitar điện Mộc E100 | 4.190.000 ₫ | 2.900.000 ₫ | 10 |
| MOC-ELEC-002 | Guitar điện Mộc E200 | 5.590.000 ₫ | 3.900.000 ₫ | 8 |
| MOC-ELEC-003 | Guitar điện Mộc E300 | 7.490.000 ₫ | 5.200.000 ₫ | 5 |
| MOC-ELEC-004 | Guitar điện Mộc E400 | 9.290.000 ₫ | 6.500.000 ₫ | 2 |
| MOC-PIANO-001 | Piano cơ Mộc P100 | 24.900.000 ₫ | 18.000.000 ₫ | 4 |
| MOC-PIANO-002 | Piano cơ Mộc P200 | 34.900.000 ₫ | 25.000.000 ₫ | 3 |
| MOC-PIANO-003 | Piano cơ Mộc P300 | 45.900.000 ₫ | 33.000.000 ₫ | 2 |
| MOC-PIANO-004 | Piano cơ Mộc P400 | 59.900.000 ₫ | 43.000.000 ₫ | 1 |
| MOC-DRUM-001 | Bộ trống acoustic Mộc D100 | 9.290.000 ₫ | 6.500.000 ₫ | 7 |
| MOC-DRUM-002 | Bộ trống acoustic Mộc D200 | 12.900.000 ₫ | 9.000.000 ₫ | 5 |
| MOC-DRUM-003 | Bộ trống acoustic Mộc D300 | 16.900.000 ₫ | 12.000.000 ₫ | 3 |
| MOC-DRUM-004 | Bộ trống acoustic Mộc D400 | 22.900.000 ₫ | 16.500.000 ₫ | 2 |
| MOC-VIOL-001 | Violin Mộc V100 cỡ 1/2 | 1.390.000 ₫ | 950.000 ₫ | 14 |
| MOC-VIOL-002 | Violin Mộc V200 cỡ 3/4 | 1.990.000 ₫ | 1.350.000 ₫ | 10 |
| MOC-VIOL-003 | Violin Mộc V300 cỡ 4/4 | 3.190.000 ₫ | 2.200.000 ₫ | 8 |
| MOC-VIOL-004 | Violin Mộc V400 cỡ 4/4 | 5.290.000 ₫ | 3.700.000 ₫ | 4 |
| MOC-TRUMP-001 | Trumpet Mộc T100 | 3.190.000 ₫ | 2.200.000 ₫ | 9 |
| MOC-TRUMP-002 | Trumpet Mộc T200 | 4.690.000 ₫ | 3.300.000 ₫ | 7 |
| MOC-TRUMP-003 | Trumpet Mộc T300 | 6.690.000 ₫ | 4.700.000 ₫ | 4 |
| MOC-TRUMP-004 | Trumpet Mộc T400 | 9.290.000 ₫ | 6.500.000 ₫ | 2 |

Tổng tồn kho mẫu mới: **141 đơn vị**. Giá trị phiếu nhập mẫu mới: **734.400.000 ₫**.

## Sửa bộ dữ liệu sai ngành trước đây

Manifest liệt kê đúng 24 SKU đồ gia dụng `MOC-BINH/TUI/SO/DEN/COC/NEN-*` và máy sấy tóc `MST001` cần lưu trữ. Công cụ chỉ đặt `is_deleted=TRUE`, giữ các bản ghi sản phẩm, tồn kho, phiếu nhập, stock movement, đơn hàng và đánh giá để bảo toàn lịch sử. Danh mục cũ chỉ được ẩn khi không còn sản phẩm đang bán. Không đổi tên sản phẩm trong đơn cũ thành một nhạc cụ khác.

Phiếu nhập cũ vẫn là lịch sử; các thống kê tiền nhập theo thời gian có thể bao gồm phiếu mẫu cũ. Catalog và tồn kho khả dụng chỉ hiển thị sản phẩm đang hoạt động. Muốn làm sạch toàn bộ lịch sử kế toán mẫu cần một yêu cầu riêng về phạm vi dữ liệu.

## Chạy công cụ

- Dữ liệu: `db/seeds/moc-studio-catalog.json`.
- Công cụ: `backend/tools/SeedMocStudioCatalog.java`. Cần backend đã build và cấu hình Aiven riêng trong `backend/application-local.properties`.
- Chạy thử từ thư mục gốc: `powershell -ExecutionPolicy Bypass -File scripts/seed-catalog.ps1`. Cả phần ẩn sản phẩm cũ và tạo nhạc cụ mới đều rollback.
- Lưu chính thức: thêm `-Apply` vào lệnh trên. Toàn bộ thay đổi nằm trong một transaction.
- SKU nhạc cụ đã có được bỏ qua, không ghi đè giá/nội dung, không cộng thêm tồn hoặc phiếu nhập. Danh sách SKU được ẩn có whitelist trong công cụ để tránh tác động sản phẩm khác.
- So sánh tài khoản, đơn hàng và đánh giá dùng cùng snapshot transaction, tránh nhầm hoạt động từ người dùng khác với thay đổi của seed.
- Seed không chạy tự động khi deploy hoặc khi khởi động backend.

## Đồng bộ frontend

- Trang chủ, Về chúng tôi, Cẩm nang và dữ liệu demo dùng nội dung nhạc cụ.
- Demo dùng namespace lưu trữ mới; không ghi đè dữ liệu demo cũ trong trình duyệt.
- Giỏ hàng API loại bỏ đúng các SKU đã ngừng bán trong manifest; nhạc cụ và số lượng còn lại được giữ nguyên.
- Ảnh minh họa gồm 6 ảnh Unsplash đã kiểm tra và xem nội dung: guitar acoustic, guitar điện, piano, bộ trống, violin và trumpet. Sản phẩm cùng nhóm có thể dùng chung ảnh.
- Aiven thay đổi sẽ có trên API ngay; nội dung giao diện và xử lý giỏ hàng mới cần push/redeploy frontend Vercel.

## Kết quả sửa ngày 21/10/2026

- Đã lưu trữ 25 sản phẩm ngoài ngành và 7 danh mục cũ; thêm 24 nhạc cụ, 6 danh mục, 141 đơn vị tồn kho và phiếu nhập mẫu tương ứng.
- Lần chạy thử đã rollback cả sản phẩm mới lẫn trạng thái lưu trữ cũ thành công. So sánh trong transaction xác nhận tài khoản, đơn hàng và đánh giá giữ nguyên.
- Chạy lại sau khi lưu: bỏ qua đủ 24 SKU, không thêm danh mục/sản phẩm/tồn kho/phiếu nhập và không lưu trữ thêm bản ghi.
- API local và proxy Vercel đều trả đúng 24 nhạc cụ, 6 danh mục; SKU cũ không còn trong tìm kiếm, máy sấy tóc không còn truy cập bằng API sản phẩm đang bán.
- Kiểm tra chi tiết đủ mô tả, hướng dẫn, ảnh, giá bán cho 24 sản phẩm; giá vốn vẫn ẩn khỏi API công khai. Tìm không dấu hoạt động.
- 20 kiểm thử frontend và build TypeScript/Vite thành công; có kiểm thử loại SKU cũ khỏi giỏ mà vẫn giữ nhạc cụ cùng số lượng.
