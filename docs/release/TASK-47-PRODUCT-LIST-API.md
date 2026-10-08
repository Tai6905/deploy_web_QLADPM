# Báo cáo hoàn thành Task 47 – API danh sách sản phẩm

## Thông tin chung

| Nội dung | Thông tin |
|---|---|
| Người thực hiện | HỒ ANH DŨNG |
| Nhánh code | `feature/ho-anh-dung` |
| Ngày kiểm tra | 28/09/2026 |
| Cơ sở dữ liệu | PostgreSQL 17.11 |

## Mục tiêu của task

Tạo các API xem danh sách cho khách chưa đăng nhập:

- Xem danh sách sản phẩm và danh mục.
- Cho phép chọn số trang và số dòng mỗi trang bằng `size`.
- Lọc theo khoảng giá và sắp xếp theo giá hoặc thời gian tạo.
- Lọc sản phẩm theo danh mục.
- Tìm sản phẩm theo tên.
- Dùng index để tốc độ phản hồi tốt hơn.
- Thời gian phản hồi mục tiêu dưới 500 ms trong điều kiện kiểm tra local.

## Các API đã làm

| Cách gọi | Đường dẫn | Kết quả |
|---|---|---|
| `GET` | `/products?page=0&size=20` | Xem danh sách sản phẩm |
| `GET` | `/products?page=0&size=20&categoryId=1` | Lọc sản phẩm theo danh mục |
| `GET` | `/products?page=0&size=20&q=Phone` | Tìm sản phẩm theo tên |
| `GET` | `/products?page=0&size=20&minPrice=100&maxPrice=1000&sort=priceAsc` | Lọc giá và sắp xếp |
| `GET` | `/categories?page=0&size=20` | Xem danh sách danh mục |

`page` bắt đầu từ `0`. `size` nhận giá trị từ `1` đến `100`. Kết quả trả về dùng `size`, cùng tổng số sản phẩm và số trang.

Có thể dùng `q`, `categoryId`, `minPrice`, `maxPrice` và `sort`. Giá trị `sort` hợp lệ là `priceAsc`, `priceDesc` và `newest`.

## Minh chứng gọi API

Các yêu cầu dưới đây được gọi tại `http://localhost:18081` và không dùng thông tin đăng nhập.

### 1. Xem danh sách sản phẩm có phân trang

Yêu cầu:

```http
GET /products?page=0&size=1
```

Kết quả: `HTTP 200 OK`

```json
{
  "items": [
    {
      "id": 2,
      "categoryId": 2,
      "categoryName": "Phụ kiện",
      "sku": "DEMO-CASE-001",
      "name": "Modern Phone Case",
      "slug": "modern-phone-case",
      "listPrice": 29
    }
  ],
  "page": 0,
  "size": 1,
  "totalItems": 2,
  "totalPages": 2
}
```

`size=1` được áp dụng đúng: mỗi trang chỉ có một sản phẩm và tổng cộng có hai trang. Giá tiền là số nguyên VND theo contract chung.

### 2. Tìm sản phẩm theo tên

Yêu cầu:

```http
GET /products?page=0&size=20&q=Phone
```

Kết quả: `HTTP 200 OK`

```json
{
  "items": [
    {"id": 2, "sku": "DEMO-CASE-001", "name": "Modern Phone Case", "listPrice": 29},
    {"id": 1, "sku": "DEMO-PHONE-001", "name": "Modern Phone X", "listPrice": 799}
  ],
  "page": 0,
  "size": 20,
  "totalItems": 2,
  "totalPages": 1
}
```

### 3. Khách chưa đăng nhập xem danh mục

Yêu cầu:

```http
GET /categories?page=0&size=20
```

Kết quả: `HTTP 200 OK`

```json
{
  "items": [
    {"id": 2, "name": "Phụ kiện", "slug": "phu-kien", "description": "Phụ kiện công nghệ"},
    {"id": 1, "name": "Điện thoại", "slug": "dien-thoai", "description": "Thiết bị di động"}
  ],
  "page": 0,
  "size": 20,
  "totalItems": 2,
  "totalPages": 1
}
```

### 4. Không tìm thấy sản phẩm

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
  "correlationId": "req-product-not-found-20260929"
}
```

## Tốc độ và database

- Sản phẩm và danh mục đã bị xóa mềm sẽ không xuất hiện trong danh sách.
- Có thể lọc theo `categoryId`, khoảng giá `minPrice`/`maxPrice` và tìm theo tên với `q`.
- Có thể sắp xếp bằng `sort=priceAsc`, `sort=priceDesc` hoặc `sort=newest`.
- Database có index cho danh mục sản phẩm, tìm kiếm tên và kiểm tra tồn kho.
- Lần đo local trong bộ dữ liệu kiểm tra mất khoảng `0.009744` giây, tương đương khoảng 10 ms, thấp hơn mục tiêu 500 ms. Đây chỉ là kết quả trên máy local; tốc độ thật còn phụ thuộc dữ liệu và máy chủ.

## Các file chính đã thực hiện

- `backend/src/main/java/com/modernshopflow/catalog/web/ProductController.java`
- `backend/src/main/java/com/modernshopflow/catalog/repository/CatalogRepository.java`
- `backend/src/main/java/com/modernshopflow/catalog/api/PageResponse.java`
- `db/migrations/create_catalog_tables.sql`
- `db/migrations/create_catalog_api_indexes.sql`

## Kết quả kiểm tra

```bash
./mvnw test
```

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Database có các index chính:

```text
idx_products_active_category_id
idx_products_active_name_trgm
idx_products_category_active
idx_products_search
```

## Kiểm tra trên Swagger/OpenAPI

- Swagger UI: `http://localhost:18081/docs`
- OpenAPI JSON: `http://localhost:18081/v3/api-docs`

OpenAPI hiển thị các đường dẫn `/products` và `/categories`, đồng thời đã được kiểm tra bằng `HTTP 200`.

## Cách chạy lại

```bash
docker compose up -d postgres
cd backend
./mvnw test
./mvnw spring-boot:run
curl 'http://localhost:18081/products?page=0&size=20&minPrice=100&maxPrice=1000&sort=priceAsc'
curl 'http://localhost:18081/categories?page=0&size=20'
```
