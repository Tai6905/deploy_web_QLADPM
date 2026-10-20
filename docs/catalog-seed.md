# Dữ liệu mẫu Mộc Studio

Bộ `moc-studio-catalog-v1` gồm 24 sản phẩm thuộc 6 danh mục: bình uống, túi canvas, sổ tay, đèn, cốc gốm và nến. Mỗi sản phẩm có giá bán, giá vốn, mô tả tiếng Việt, hướng dẫn sử dụng, ảnh và ngưỡng tồn kho.

Đây là dữ liệu mẫu phục vụ đồ án. Giá, dung tích, trọng lượng, mùi hương và ảnh cần được xác nhận với hàng thực tế trước khi kinh doanh. Các mô tả sản phẩm cũng có ghi chú này.

## Danh sách

| SKU | Sản phẩm | Giá bán | Giá vốn mẫu | Số lượng đầu kỳ |
| --- | --- | ---: | ---: | ---: |
| MOC-BINH-001 | Bình nước Mộc Rừng Xanh 500ml | 285.000 ₫ | 175.000 ₫ | 32 |
| MOC-BINH-002 | Bình nước Mộc Daily 600ml | 315.000 ₫ | 190.000 ₫ | 24 |
| MOC-BINH-003 | Bình nước Mộc Weekend 750ml | 359.000 ₫ | 215.000 ₫ | 18 |
| MOC-BINH-004 | Bình nước Mộc Journey 1L | 429.000 ₫ | 265.000 ₫ | 6 |
| MOC-TUI-001 | Túi canvas Mộc Daily | 169.000 ₫ | 95.000 ₫ | 36 |
| MOC-TUI-002 | Túi canvas Mộc Đi Học | 219.000 ₫ | 125.000 ₫ | 28 |
| MOC-TUI-003 | Túi canvas Mộc Mini | 139.000 ₫ | 75.000 ₫ | 20 |
| MOC-TUI-004 | Túi canvas Mộc Cuối Tuần | 249.000 ₫ | 145.000 ₫ | 14 |
| MOC-SO-001 | Sổ tay Mộc Ý Tưởng A5 | 79.000 ₫ | 39.000 ₫ | 48 |
| MOC-SO-002 | Sổ kế hoạch Mộc Tuần Mới | 109.000 ₫ | 55.000 ₫ | 36 |
| MOC-SO-003 | Sổ tay Mộc Bỏ Túi A6 | 49.000 ₫ | 25.000 ₫ | 40 |
| MOC-SO-004 | Sổ ghi chép Mộc Dòng Chảy A5 | 89.000 ₫ | 45.000 ₫ | 22 |
| MOC-DEN-001 | Đèn đọc sách Mộc Lumi Mini | 429.000 ₫ | 260.000 ₫ | 16 |
| MOC-DEN-002 | Đèn bàn Mộc Focus | 529.000 ₫ | 325.000 ₫ | 12 |
| MOC-DEN-003 | Đèn bàn Mộc Reading | 489.000 ₫ | 295.000 ₫ | 8 |
| MOC-DEN-004 | Đèn bàn Mộc Studio | 689.000 ₫ | 425.000 ₫ | 4 |
| MOC-COC-001 | Cốc gốm Mộc Ban Mai 280ml | 129.000 ₫ | 65.000 ₫ | 30 |
| MOC-COC-002 | Cốc gốm Mộc An Nhiên 350ml | 159.000 ₫ | 85.000 ₫ | 26 |
| MOC-COC-003 | Cốc gốm Mộc Chậm 400ml | 179.000 ₫ | 95.000 ₫ | 18 |
| MOC-COC-004 | Cốc gốm Mộc Ngày Thường 450ml | 209.000 ₫ | 115.000 ₫ | 6 |
| MOC-NEN-001 | Nến thơm Mộc Gỗ Ấm 120g | 169.000 ₫ | 85.000 ₫ | 24 |
| MOC-NEN-002 | Nến thơm Mộc Vườn Sớm 150g | 199.000 ₫ | 105.000 ₫ | 20 |
| MOC-NEN-003 | Nến thơm Mộc Trà Chiều 180g | 239.000 ₫ | 125.000 ₫ | 12 |
| MOC-NEN-004 | Nến thơm Mộc Tĩnh 200g | 279.000 ₫ | 145.000 ₫ | 5 |

Tổng tồn kho bổ sung: **505 đơn vị**. Tổng giá trị phiếu nhập mẫu: **56.957.000 ₫**.

## Nhập và kiểm tra

- Dữ liệu: `db/seeds/moc-studio-catalog.json`.
- Công cụ: `backend/tools/SeedMocStudioCatalog.java`; script Windows: `scripts/seed-catalog.ps1`.
- Công cụ dùng cấu hình Aiven cục bộ đã có, không in mật khẩu. Cần build backend kèm test trước (`.\mvnw.cmd package` trong thư mục `backend`). Classpath được lấy từ báo cáo kiểm thử và loại bỏ `target/test-classes` để tránh dùng cấu hình H2.
- Chạy thử từ thư mục gốc: `powershell -ExecutionPolicy Bypass -File scripts/seed-catalog.ps1`. Mọi dữ liệu nhập thử được rollback.
- Lưu chính thức: `powershell -ExecutionPolicy Bypass -File scripts/seed-catalog.ps1 -Apply`. Chỉ lệnh có `-Apply` mới commit.
- SKU đã tồn tại được bỏ qua, giữ nguyên nội dung/giá/tồn kho, kể cả sản phẩm đã lưu trữ. Chạy lại không cộng thêm tồn hoặc tạo thêm phiếu nhập cho các SKU này.
- Cả đợt nằm trong một transaction; lỗi trước commit sẽ rollback toàn bộ. Advisory lock ngăn hai lượt chạy công cụ nhập cùng lúc.
- Tạo sản phẩm, nhập kho và ghi stock movement thông qua service hiện có. Phiếu nhập có nguồn `Mộc Studio · dữ liệu mẫu` và ghi chú chứa `moc-studio-catalog-v1`, để phân biệt trên báo cáo.
- Các số liệu tiền nhập và giá trị tồn kho trên dashboard sẽ bao gồm dữ liệu mẫu này. Đơn hàng, người dùng và đánh giá được giữ nguyên; sao đánh giá tăng khi có luồng mua/giao hàng/đánh giá thực tế.
- Seed không chạy tự động khi khởi động backend hoặc khi deploy.

## Ảnh minh họa

Sử dụng 6 ảnh công khai từ Unsplash tương ứng với từng nhóm sản phẩm. Đã kiểm tra tải ảnh thành công và xem ảnh trước khi chọn. Các sản phẩm cùng nhóm có thể dùng chung ảnh minh họa. Mỗi sản phẩm hiện có một ảnh; chủ shop có thể thay bằng bộ ảnh thực tế trong trang chi tiết sản phẩm.

Sau khi dữ liệu được lưu lên Aiven, frontend dùng API chỉ cần tải lại danh mục. Nếu mã storefront mới chưa được deploy, hãy redeploy backend/frontend để dùng đầy đủ hướng dẫn và đánh giá.

## Kết quả nhập ngày 20/10/2026

- Đã commit 6 danh mục, 24 sản phẩm, 505 đơn vị tồn kho và 24 phiếu nhập mẫu, tổng giá trị 56.957.000 ₫.
- Lần chạy lại: 0 danh mục, 0 sản phẩm, 0 đơn vị tồn kho và 0 giá trị nhập thêm; bỏ qua đủ 24 SKU đã có.
- API local xác nhận đủ tên tiếng Việt, mô tả, hướng dẫn, giá bán và ảnh cho 24 sản phẩm. Tìm không dấu, lọc danh mục, giá và còn hàng hoạt động; giá vốn được ẩn khỏi API công khai.
- Catalog có 25 sản phẩm, gồm sản phẩm `MST001` đã có trước đó. API Railway trả đủ 24 SKU mới.
- Tài khoản, đơn hàng và đánh giá được giữ nguyên trong snapshot kiểm tra của transaction nhập sản phẩm; phép kiểm tra không phụ thuộc vào hoạt động đăng ký từ các phiên bên ngoài.
