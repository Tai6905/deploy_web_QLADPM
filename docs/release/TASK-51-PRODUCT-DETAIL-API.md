# Báo cáo hoàn thành Task 51 – API chi tiết sản phẩm

## Thông tin chung

| Nội dung | Thông tin |
|---|---|
| Người thực hiện | HỒ ANH DŨNG |
| Nhánh code | `feature/ho-anh-dung` |
| Ngày kiểm tra | 28/09/2026 |
| Cơ sở dữ liệu | PostgreSQL 17.11 |

## Mục tiêu của task

API `/products/{id}` phải trả về đầy đủ thông tin sản phẩm, gồm:

- Thông tin sản phẩm và danh mục.
- Các phiên bản sản phẩm trong `productVariants`.
- Các hình ảnh trong `productImages`.
- Số lượng tồn kho còn dùng được ở thời điểm gọi API.

## Cách tính tồn kho

Với mỗi phiên bản sản phẩm, hệ thống tính:

```text
Số lượng còn dùng được = Số lượng có - Số lượng đã giữ - Số lượng đang tạm giữ
```

Chỉ những dòng trong `stock_holds` có trạng thái `HOLDING` và chưa hết hạn mới được tính là “đang tạm giữ”. Những dòng đã hết hạn, đã thanh toán hoặc đã hủy sẽ không được tính.

## Minh chứng gọi API

### 1. Xem chi tiết sản phẩm

Yêu cầu:

```http
GET /products/1
```

Kết quả: `HTTP 200 OK`

```json
{
  "id": 1,
  "categoryId": 1,
  "categoryName": "Điện thoại",
  "sku": "DEMO-PHONE-001",
  "name": "Modern Phone X",
  "slug": "modern-phone-x",
  "description": "Sản phẩm demo cho API chi tiết",
  "costPrice": 500,
  "listPrice": 799,
  "productVariants": [
    {
      "id": 1,
      "color": "Black",
      "size": "128GB",
      "quantity": 10,
      "reservedQuantity": 2,
      "holdingQuantity": 3,
      "availableQuantity": 5
    },
    {
      "id": 2,
      "color": "Blue",
      "size": "256GB",
      "quantity": 4,
      "reservedQuantity": 0,
      "holdingQuantity": 0,
      "availableQuantity": 4
    }
  ],
  "productImages": [
    {
      "id": 1,
      "imageUrl": "https://cdn.example.test/modern-phone-x/front.jpg",
      "displayOrder": 0,
      "primary": true
    }
  ]
}
```

Với phiên bản đầu tiên, phép tính là `10 - 2 - 3 = 5`. Số `3` được lấy từ dòng `HOLDING` còn hạn trong bảng `stock_holds`, không phải số viết cố định trong code.

### 2. Không tìm thấy sản phẩm

Yêu cầu:

```http
GET /products/999999
```

Kết quả: `HTTP 404 Not Found`

```json
{
  "timestamp": "2026-09-29T15:29:19.111+07:00",
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Product 999999 was not found",
  "path": "/products/999999",
  "correlationId": "req-product-detail-not-found-20260929"
}
```

## Các file chính đã thực hiện

- `backend/src/main/java/com/modernshopflow/catalog/web/ProductController.java`
- `backend/src/main/java/com/modernshopflow/catalog/repository/CatalogRepository.java`
- `backend/src/main/java/com/modernshopflow/catalog/api/ProductDetailResponse.java`
- `db/migrations/create_cart_and_inventory_tables.sql`
- `db/migrations/create_catalog_api_indexes.sql`
- `db/seed/catalog_demo.sql`

## Kết quả kiểm tra

```bash
./mvnw test
```

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Database đã chạy đủ năm file tạo cấu trúc theo đúng thứ tự, nạp dữ liệu demo và trả về `HTTP 200` cho `/products/1`.

## Kiểm tra trên Swagger/OpenAPI

- Swagger UI: `http://localhost:18081/docs`
- OpenAPI JSON: `http://localhost:18081/v3/api-docs`

OpenAPI có đường dẫn `/products/{id}` và đã được kiểm tra bằng `HTTP 200`.

## Cách chạy lại

```bash
docker compose up -d postgres
cd backend
./mvnw test
./mvnw spring-boot:run
cd ..
docker compose exec -T postgres psql -U shopflow -d shopflow < db/seed/catalog_demo.sql
curl 'http://localhost:18081/products/1'
```
