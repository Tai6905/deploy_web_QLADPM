# Bộ lọc nhạc cụ và so sánh ShopFlow

Đã áp dụng migration và bổ sung thông số cho 24 nhạc cụ mẫu trên Aiven ngày 21/10/2026. Kiểm tra: 27 bài frontend, 37 bài backend đạt; build frontend và JAR production thành công. JAR production đã kiểm tra đọc Aiven, bộ lọc và CORS. Website online vẫn cần deploy mã nguồn mới lên Railway và Vercel.

Trong **Cửa hàng → Lọc theo thông số nhạc cụ**, chọn một nhóm để mở các bộ lọc tương ứng:

| Nhóm | Thông số |
| --- | --- |
| Guitar | Acoustic/classic/điện/bass, số dây, dáng đàn |
| Piano / Keyboard | Loại đàn, số phím, cơ chế phím |
| Trống / Bộ gõ | Loại trống, số trống trong bộ, chất liệu thân trống |
| Violin | Loại violin, cỡ đàn, gỗ mặt đàn |
| Kèn / Sáo | Loại nhạc cụ hơi, tông nhạc cụ |

Các bộ lọc kết hợp với danh mục, tìm kiếm, khoảng giá, đánh giá, tồn kho và sắp xếp. API lọc trước khi phân trang. Đổi nhóm nhạc cụ sẽ xóa các thông số riêng của nhóm cũ; “Xóa bộ lọc” đặt lại toàn bộ lựa chọn.

Nhấn **So sánh** tại thẻ sản phẩm hoặc trang chi tiết. Thanh chọn giữ tối đa 3 sản phẩm qua các trang và khi tải lại trình duyệt. Mở `/compare` để xem giá, tồn kho, đánh giá và các thông số cạnh nhau; bật **Chỉ xem điểm khác nhau** để rút gọn bảng. Có thể bỏ từng sản phẩm, thêm vào giỏ hoặc xem chi tiết. Bảng hỗ trợ cuộn ngang trên điện thoại.

Giá và tồn kho được tải lại từ API khi mở bảng. Sản phẩm đã ngừng bán có thông báo và nút bỏ; lỗi kết nối có nút thử lại. “Chưa cập nhật” thể hiện dữ liệu còn thiếu, “Không áp dụng” thể hiện thông số của nhóm nhạc cụ khác.

## Nhập thông số

Chủ shop có thể nhập thông số khi **Tạo sản phẩm**, hoặc mở trang chi tiết → **Chỉnh sửa ảnh & nội dung** để cập nhật sản phẩm đang có. API chỉ cho chủ shop lưu; khách xem và lọc không cần đăng nhập. Các lựa chọn dùng chung tệp `src/catalog/instruments.json` cho frontend và backend.

Sản phẩm chưa được gán nhóm vẫn xuất hiện khi không lọc thông số. Muốn sản phẩm xuất hiện trong một bộ lọc cụ thể, cần nhập đúng nhóm và thông số đó. Không suy đoán thông số của sản phẩm thật từ ảnh hoặc tên.

## Cập nhật Aiven trước khi deploy backend

Migration mới: `db/migrations/add_instrument_specifications.sql`. Migration tạo bảng phụ và chỉ mục, không sửa giá, tồn kho hoặc lịch sử đơn hàng. Production tắt schema initializer, vì vậy cần chạy bước cập nhật trước khi deploy backend mới.

Từ thư mục dự án, sau khi đã chạy `backend\mvnw.cmd test` trong thư mục backend và có cấu hình Aiven trong `backend/application-local.properties`:

```powershell
# Kiểm tra rồi rollback toàn bộ thay đổi
.\scripts\upgrade-instruments.ps1

# Áp dụng migration và bổ sung thông số mẫu còn thiếu
.\scripts\upgrade-instruments.ps1 -Apply
```

Tệp `db/seeds/instrument-specifications.json` bổ sung thông số **minh họa cho 24 SKU nhạc cụ mẫu** `MOC-ACOU/ELEC/PIANO/DRUM/VIOL/TRUMP-001..004`. Đây là dữ liệu đồ án, không phải thông số đã xác minh của sản phẩm kinh doanh thực tế. Công cụ chỉ bổ sung sản phẩm có ghi chú “Dữ liệu mẫu”, bỏ qua sản phẩm đã có thông số và SKU không tồn tại. Chạy lại không ghi đè chỉnh sửa của chủ shop. Các sản phẩm thật cần nhập thông số đã xác nhận qua trang quản trị.

Sau đó deploy backend Railway và frontend Vercel. Đường dẫn `/compare` dùng SPA fallback đã có trong `vercel.json`.

Ví dụ API:

```text
GET /products?instrumentType=guitar&spec.guitarType=acoustic&spec.stringCount=6&page=0&size=12
GET /products?instrumentType=violin&spec.violinSize=1%2F2
```

Body tạo sản phẩm hoặc cập nhật nội dung có thể thêm:

```json
"specifications": { "instrumentType": "guitar", "guitarType": "acoustic", "stringCount": "6" }
```

Khi cập nhật nội dung, bỏ hẳn trường `specifications` sẽ giữ nguyên thông số; gửi `{}` sẽ xóa thông số. Giá trị và tổ hợp trường không đúng nhóm bị từ chối bằng HTTP 400.
