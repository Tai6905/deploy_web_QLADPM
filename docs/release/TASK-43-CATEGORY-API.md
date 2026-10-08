# Báo cáo hoàn thành Task 43 – API danh mục

## Thông tin chung

| Nội dung | Thông tin |
|---|---|
| Người thực hiện | HỒ ANH DŨNG |
| Nhánh code | `feature/ho-anh-dung` |
| Ngày kiểm tra | 28/09/2026 |
| Cơ sở dữ liệu | PostgreSQL 17.11 |

## Mục tiêu của task

Viết API CRUD cho bảng `categories`:

- Xem danh sách và xem một danh mục.
- Thêm, sửa và xóa danh mục.
- Không cho xóa danh mục nếu danh mục đó vẫn có sản phẩm đang hoạt động.
- Trường hợp không được xóa phải trả về mã HTTP `409 Conflict`.

## Các API đã làm

| Cách gọi | Đường dẫn | Kết quả |
|---|---|---|
| `GET` | `/categories?page=0&size=20` | Xem danh sách danh mục đang hoạt động |
| `GET` | `/categories/{id}` | Xem một danh mục |
| `POST` | `/categories` | Thêm danh mục mới, trả về `201` |
| `PUT` | `/categories/{id}` | Sửa danh mục |
| `DELETE` | `/categories/{id}` | Xóa mềm danh mục rỗng, trả về `204` |

## Minh chứng gọi API

### 1. Xem danh sách danh mục

Yêu cầu:

```http
GET /categories?page=0&size=20
```

Kết quả: `HTTP 200 OK`

```json
{
  "items": [
    {
      "id": 2,
      "name": "Phụ kiện",
      "slug": "phu-kien",
      "description": "Phụ kiện công nghệ"
    },
    {
      "id": 1,
      "name": "Điện thoại",
      "slug": "dien-thoai",
      "description": "Thiết bị di động"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 2,
  "totalPages": 1
}
```

### 2. Xem một danh mục

Yêu cầu:

```http
GET /categories/1
```

Kết quả: `HTTP 200 OK`

```json
{
  "id": 1,
  "name": "Điện thoại",
  "slug": "dien-thoai",
  "description": "Thiết bị di động"
}
```

### 3. Thêm danh mục

Yêu cầu:

```http
POST /categories
Content-Type: application/json

{
  "name": "Report Proof",
  "slug": "report-proof-20260928",
  "description": "Category created for release evidence"
}
```

Kết quả: `HTTP 201 Created`

```json
{
  "id": 4,
  "name": "Report Proof",
  "slug": "report-proof-20260928",
  "description": "Category created for release evidence"
}
```

### 4. Sửa danh mục

Yêu cầu:

```http
PUT /categories/4
Content-Type: application/json

{
  "name": "Report Proof Updated",
  "slug": "report-proof-updated-20260928",
  "description": "Category updated for release evidence"
}
```

Kết quả: `HTTP 200 OK`

```json
{
  "id": 4,
  "name": "Report Proof Updated",
  "slug": "report-proof-updated-20260928",
  "description": "Category updated for release evidence"
}
```

### 5. Xóa danh mục không có sản phẩm

Yêu cầu:

```http
DELETE /categories/4
```

Kết quả: `HTTP 204 No Content`, không có dữ liệu trả về.

Đây là xóa mềm: hệ thống đổi `categories.is_deleted` thành `TRUE` thay vì xóa hẳn dòng dữ liệu.

### 6. Từ chối xóa danh mục đang có sản phẩm

Danh mục `1` đang có một sản phẩm hoạt động.

Yêu cầu:

```http
DELETE /categories/1
```

Kết quả: `HTTP 409 Conflict`

```json
{
  "timestamp": "2026-09-29T15:29:00+07:00",
  "status": 409,
  "code": "CONFLICT",
  "message": "Category 1 cannot be deleted while it contains 1 active product(s)",
  "path": "/categories/1",
  "correlationId": "req-category-conflict-20260929"
}
```

Kết quả này chứng minh hệ thống đã kiểm tra sản phẩm trước khi xóa danh mục.

## Các file chính đã thực hiện

- `backend/src/main/java/com/modernshopflow/catalog/web/CategoryController.java`
- `backend/src/main/java/com/modernshopflow/catalog/service/CatalogService.java`
- `backend/src/main/java/com/modernshopflow/catalog/repository/CatalogRepository.java`
- `backend/src/main/java/com/modernshopflow/catalog/web/ApiExceptionHandler.java`
- `db/migrations/create_catalog_tables.sql`
- `db/migrations/create_catalog_api_indexes.sql`

## Kết quả kiểm tra

Đã chạy:

```bash
./mvnw test
```

Kết quả:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Kiểm tra trên Swagger/OpenAPI

- Swagger UI: `http://localhost:18081/docs`
- OpenAPI JSON: `http://localhost:18081/v3/api-docs`

Hai đường dẫn đều trả về `HTTP 200`. Swagger UI hiển thị các API danh mục để có thể gọi thử trực tiếp.

## Cách chạy lại

```bash
docker compose up -d postgres
cd backend
./mvnw test
./mvnw spring-boot:run
cd ..
docker compose exec -T postgres psql -U shopflow -d shopflow < db/seed/catalog_demo.sql
```
