# Deploy đồ án: Vercel + Railway + Aiven

Frontend chạy trên Vercel, backend Spring Boot chạy trên Railway, PostgreSQL giữ nguyên trên Aiven. Bạn không cần cài Docker hoặc viết Dockerfile; Railway tự đóng gói ứng dụng bằng Railpack. Kiểm tra hạn mức/chi phí hiển thị trong tài khoản Railway trước khi tạo dịch vụ.

## 1. Đưa mã nguồn lên GitHub

Đưa repo hiện tại lên GitHub, bao gồm `backend`, `db/migrations`, `railpack.json`, `railway.json` và `vercel.json`. Không đưa `backend/application-local.properties` hoặc `backend/accounts.local.properties` lên GitHub. Hai file này chứa thông tin riêng và đã nằm trong `.gitignore`.

## 2. Deploy backend trên Railway trước

1. Tạo project trên Railway, chọn **Deploy from GitHub repo**, chọn repo của bạn.
2. Giữ **Root Directory** là `/` (thư mục gốc repo), builder **Railpack**. Không chọn riêng `backend` vì Maven còn cần `db/migrations` bên ngoài thư mục đó. Hai file cấu hình Railway/Railpack đã có lệnh build và chạy Java.
3. Trong **Variables**, nhập:

| Biến | Giá trị |
| --- | --- |
| `RAILPACK_JDK_VERSION` | `21` |
| `DB_URL` | `jdbc:postgresql://qldapm-261-hcmut-ltw.e.aivencloud.com:28028/defaultdb?sslmode=require&connectTimeout=10&socketTimeout=30&tcpKeepAlive=true` |
| `DB_USERNAME` | `avnadmin` |
| `DB_PASSWORD` | Mật khẩu Aiven bạn đang dùng, nhập trực tiếp tại Railway |

Không đặt mật khẩu database trong biến `VITE_*`. Không cần tạo database khác hoặc tạo lại bốn tài khoản: chúng đã có trên Aiven. Profile `production` được bật bằng lệnh chạy và dùng cổng `PORT` Railway cấp; nó không tự áp dụng migration lên database hiện có.

4. Deploy/redeploy sau khi lưu Variables. Khi trạng thái healthy, vào **Settings → Networking → Generate Domain**. Nếu được hỏi target port, nhập đúng cổng `PORT` của service/log Tomcat.
5. Mở `https://TEN-BACKEND.up.railway.app/api/auth/session`. Phải trả JSON có `authenticated: false`, không phải lỗi 502. Truy cập `/` trực tiếp có thể nhận 401 vì backend không phục vụ giao diện.

## 3. Điền URL backend cho Vercel

Trong `vercel.json`, thay **chỉ** `https://your-backend.up.railway.app` bằng domain HTTPS thật lấy ở bước 2, giữ đuôi `/:path*`. Commit và đẩy thay đổi lên GitHub.

Ví dụ:

```json
"destination": "https://shopflow-api-production.up.railway.app/:path*"
```

Vercel nhận `/backend/...` rồi chuyển tiếp tới backend. Trình duyệt gửi cookie đăng nhập qua cùng domain Vercel; frontend không gọi trực tiếp domain Railway. Các trang như `/orders` cũng không bị nhầm với API vì API có tiền tố `/backend`.

## 4. Deploy frontend trên Vercel

1. Chọn **Add New → Project**, import cùng repo GitHub.
2. **Root Directory**: thư mục gốc repo, **Framework Preset**: Vite. `vercel.json` đã đặt build `npm run build`, output `dist`, install `npm ci`.
3. Trong **Environment Variables**, đặt cho Production và Preview:

```env
VITE_DATA_SOURCE=api
VITE_API_BASE_URL=/backend
```

4. Chọn Deploy. Nếu sửa biến sau lần build đầu, chọn Redeploy để frontend nhận giá trị mới.
5. Trên Railway, thêm `CORS_ALLOWED_ORIGINS=https://TEN-WEB.vercel.app` theo domain frontend thật rồi redeploy backend. Khi dùng proxy, request trình duyệt vẫn cùng origin; cấu hình này cũng cho phép các preflight hợp lệ từ domain đã chọn. Không dùng `*` với cookie đăng nhập.

## 5. Kiểm tra trên URL Vercel

1. Mở `/backend/api/auth/session`: nhận JSON. Nếu nhận HTML, kiểm tra URL backend trong `vercel.json` và thứ tự rewrites.
2. Mở `/login`, đăng nhập `shop@gmail.com` bằng mật khẩu mặc định bạn yêu cầu, kiểm tra trang nhân viên.
3. Đăng xuất, thử lần lượt tài khoản bán hàng, kho và khách hàng để kiểm tra đúng quyền.
4. Với tài khoản khách, đặt một đơn thử và kiểm tra `/purchases`, mở chi tiết theo dõi đơn. Đơn thử trên web sẽ được lưu thật vào database; thanh toán vẫn là mô phỏng.
5. Tải lại trực tiếp `/profile` và `/purchases`: không được lỗi 404. Đăng xuất rồi mở lại trang cá nhân: phải yêu cầu đăng nhập.

Có thể chạy kiểm tra tự động qua URL đã deploy từ PowerShell ở thư mục gốc repo (đọc bốn tài khoản trong file local, không in mật khẩu):

```powershell
$env:SHOPFLOW_URL = 'https://TEN-WEB.vercel.app'
$env:SHOPFLOW_API_PREFIX = '/backend'
node backend/tools/VerifyRoleAccess.mjs
```

Chỉ xác nhận triển khai thành công sau khi các bước kiểm tra trên domain thật đều đạt. Các cấu hình trong repo đã được chuẩn bị; chúng chưa tự tạo project hay deploy lên tài khoản cloud của bạn.

Tài liệu chính thức: [Railway Spring Boot](https://docs.railway.com/guides/spring-boot), [Railpack Java](https://railpack.com/languages/java/), [Vercel Vite](https://vercel.com/docs/frameworks/frontend/vite), [Vercel rewrites](https://vercel.com/docs/routing/rewrites).
