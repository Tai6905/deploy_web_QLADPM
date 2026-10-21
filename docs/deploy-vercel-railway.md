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

Bản nâng cấp ShopFlow cần migration thứ 10: `enhance_storefront_and_receiving.sql` cho giá nhập, hướng dẫn sử dụng và đánh giá. Áp dụng bằng công cụ schema initializer trước khi deploy backend mới; xem [cách sử dụng và cập nhật storefront](storefront-upgrade.md). Không xóa hoặc tạo lại database.

Trang thanh toán riêng cần thêm migration thứ 11: `add_checkout_addresses_and_vouchers.sql`. Xem [hướng dẫn checkout](checkout.md) cho địa chỉ lưu theo tài khoản, voucher, COD và phương thức thanh toán mô phỏng. Deploy cả backend và frontend của bản checkout sau khi schema đã sẵn sàng.

Chức năng hủy đơn cần migration `add_order_cancellations.sql`. Chạy `scripts/check-order-cancellations.ps1` sau khi build/test backend để cập nhật Aiven và kiểm tra bằng dữ liệu rollback trước khi deploy backend mới. Xem [trang hủy đơn và quy tắc xử lý](order-cancellations.md). Sau đó deploy cả Railway và Vercel.

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

### 4.1. Kiểm tra backend và đẩy cấu hình lên GitHub

Mở `https://deploywebqladpm-production.up.railway.app/api/auth/session`. Backend sẵn sàng khi trang trả JSON với `authenticated: false`. Nếu Railway vẫn đang crash hoặc URL trả 502, cần xem Deployment Logs để sửa backend; frontend vẫn có thể deploy nhưng đăng nhập/tải sản phẩm sẽ chưa hoạt động.

File `vercel.json` hiện chuyển tiếp API tới:

```json
"destination": "https://deploywebqladpm-production.up.railway.app/:path*"
```

Phải có `https://` ở đầu và giữ `/:path*` ở cuối. Lưu file, commit rồi push lên nhánh GitHub sẽ deploy. Vercel dùng mã trên GitHub, không tự lấy các thay đổi chưa push trong VS Code.

### 4.2. Tạo project frontend

1. Đăng nhập Vercel, vào Dashboard → **Add New → Project**.
2. Trong **Import Git Repository**, tìm repo chứa dự án và bấm **Import**. Nếu chưa thấy repo, kiểm tra GitHub đã kết nối và Vercel có quyền đọc repo đó.
3. Ở màn hình cấu hình, kiểm tra các giá trị:

| Mục | Giá trị |
| --- | --- |
| Project Name | Tên tùy chọn, ví dụ `shopflow-doan` |
| Framework Preset | `Vite` |
| Root Directory | Thư mục gốc repo, nơi có `package.json` và `vercel.json`; không chọn `backend` hoặc `dist` |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | `dist` |

Các lệnh đã được đặt trong `vercel.json`; không cần ghi đè nếu Vercel đã đọc đúng.

### 4.3. Thêm biến môi trường và deploy

Mở phần **Environment Variables**, thêm riêng hai dòng (Name và Value là hai ô khác nhau, không nhập dấu ngoặc kép):

| Name | Value |
| --- | --- |
| `VITE_DATA_SOURCE` | `api` |
| `VITE_API_BASE_URL` | `/backend` |

Áp dụng cho **Production** và **Preview** nếu màn hình có lựa chọn môi trường. `/backend` là đường dẫn proxy trong `vercel.json`, không thay nó bằng URL Railway. Mật khẩu Aiven vẫn chỉ nhập trong Variables của Railway.

Bấm **Deploy**, đợi build hoàn tất. Khi trạng thái **Ready**, mở domain production trong mục **Domains**, ví dụ `https://shopflow-doan.vercel.app`. Đây chỉ là ví dụ; dùng domain Vercel thực sự cấp cho project của bạn.

Nếu project đã tạo trước đó: vào **Settings → Environment Variables** để thêm/sửa hai biến trên, sau đó vào **Deployments → chọn deployment mới nhất → menu dấu ba chấm → Redeploy**. Biến `VITE_*` được đưa vào frontend lúc build, nên lưu biến xong cần build/deploy lại.

### 4.4. Khai báo domain frontend trong backend

Domain frontend hiện tại đã được đặt trong `backend/src/main/resources/application-production.properties`:

```properties
app.cors.allowed-origins=https://deploy-web-qladpm.vercel.app
```

Bạn không cần thêm biến CORS trên Railway khi dùng cấu hình này. Nếu đổi domain, sửa dòng trên, lưu, commit và push GitHub, rồi kiểm tra Railway đã deploy đúng commit mới. Không thêm `/login`, không thêm dấu `/` ở cuối và không dùng `*`. Nếu có nhiều domain cần dùng, liệt kê các origin cụ thể, ngăn bằng dấu phẩy.

Lệnh chạy trong cả `railway.json` và `railpack.json` đã bật `--spring.profiles.active=production`. Trong Deployment Logs phải thấy profile `production` được kích hoạt. Domain frontend cũng có trong danh sách mặc định của `application.yml`; khi đổi domain cần cập nhật cả hai file để các cách chạy nhất quán. Nếu service có lệnh chạy tùy chỉnh hoặc cấu hình environment ghi đè (nhất là `APP_CORS_ALLOWED_ORIGINS`/`SPRING_APPLICATION_JSON`), kiểm tra chúng có đang giữ giá trị cũ không.

Khi dùng proxy, request trình duyệt vẫn cùng origin; cấu hình này cũng cho phép các preflight hợp lệ từ domain đã chọn. Nếu sau này mở frontend bằng domain Preview khác, origin đó cũng cần được cho phép.

## 5. Kiểm tra trên URL Vercel

Trong các URL bên dưới, thay `TEN-WEB.vercel.app` bằng domain frontend thật.

### 5.1. Kiểm tra chuyển tiếp API trước

Mở `https://TEN-WEB.vercel.app/backend/api/auth/session`. Khi chưa đăng nhập, phải thấy JSON có `authenticated: false` (có thể kèm các trường khác). Không cần sao chép hay chia sẻ `csrfToken`.

| Kết quả | Kiểm tra tiếp |
| --- | --- |
| JSON có `authenticated: false` | Proxy và endpoint session đã hoạt động; tiếp tục đăng nhập |
| 502 hoặc Application Failed to Respond | Thử URL session trực tiếp trên Railway, kiểm tra backend và target port |
| HTML hoặc giao diện web thay cho JSON | Kiểm tra `destination` HTTPS, thứ tự rewrites (proxy trước fallback) và commit đã deploy |
| 404 | Kiểm tra đúng domain/path, `vercel.json` nằm ở Root Directory đã chọn |

### 5.2. Kiểm tra đăng nhập và phân quyền

Mở `https://TEN-WEB.vercel.app/login`. Đăng nhập từng tài khoản trong bảng, dùng mật khẩu mặc định đã cấu hình theo yêu cầu của bạn. Đăng xuất trước khi thử tài khoản tiếp theo.

| Tài khoản | Kiểm tra |
| --- | --- |
| `shop@gmail.com` | Vào trang tổng quan, sản phẩm, đơn hàng và `/staff` |
| `nhanvien@shopflow.local` | Vào đơn hàng/giao hàng; không có quyền quản lý nhân viên hoặc chỉnh tồn kho |
| `kho@shopflow.local` | Vào tồn kho, nhập kho, giao hàng; không có quyền quản lý nhân viên |
| `khachhang@shopflow.local` | Vào cửa hàng, `/profile`, `/purchases`; không có quyền vào trang quản trị |

Sau khi đăng nhập thành công, tải lại `/backend/api/auth/session` trên cùng domain Vercel: trường `authenticated` phải là `true`. Nếu giao diện báo đăng nhập thành công nhưng tải lại bị mất đăng nhập, kiểm tra request session/cookie trong bước 5.4.

### 5.3. Kiểm tra lịch sử, tải lại trang và đăng xuất

1. Với tài khoản khách, vào `/customer`, thêm sản phẩm còn hàng vào giỏ, điền thông tin nhận hàng và đặt một đơn thử.
2. Vào `/purchases`, đơn vừa đặt phải xuất hiện; mở chi tiết để xem sản phẩm, số tiền và tiến trình giao hàng. Đơn thử trên web sẽ được lưu thật vào Aiven; thanh toán vẫn là mô phỏng.
3. Mở trực tiếp `/profile`, `/purchases` rồi nhấn F5: không được lỗi 404 và phiên đăng nhập phải còn.
4. Đăng xuất, mở lại `/profile`: phải chuyển tới trang đăng nhập.

### 5.4. Nếu gặp lỗi, lấy thông tin ở đâu

Nhấn **F12 → Network**, thử lại thao tác lỗi, chọn request màu đỏ hoặc request `login`/`session`. Ghi lại **Request URL**, **Status Code** và thông báo trong tab **Response**. Không chia sẻ Request Payload của login, Cookie, Set-Cookie hay các header/token xác thực.

- Build frontend thất bại: Vercel → Deployments → deployment lỗi → Build Logs, lấy lỗi đầu tiên.
- Backend vẫn crash: Railway → deployment backend → Deployment Logs, lấy phần `ERROR`/`Caused by` và mã thoát nếu có.
- Login trả 401: kiểm tra tài khoản/mật khẩu hoặc tài khoản bị khóa.
- Request ghi dữ liệu trả 403: kiểm tra quyền của vai trò và CSRF; không tắt CSRF để chữa lỗi.
- Response là `Invalid CORS request`: backend từ chối `Origin` của frontend trước khi xử lý đăng ký/đăng nhập. Kiểm tra domain chính xác, commit đang chạy và profile production. OPTIONS từ `https://deploy-web-qladpm.vercel.app` tới `/api/auth/register` phải trả 200 và header `Access-Control-Allow-Origin` đúng domain đó; không mở quyền cho toàn bộ `*.vercel.app` để chữa lỗi.

Có thể chạy kiểm tra tự động qua URL đã deploy từ PowerShell ở thư mục gốc repo (đọc bốn tài khoản trong file local, không in mật khẩu):

```powershell
$env:SHOPFLOW_URL = 'https://TEN-WEB.vercel.app'
$env:SHOPFLOW_API_PREFIX = '/backend'
node backend/tools/VerifyRoleAccess.mjs
```

Chỉ xác nhận triển khai thành công sau khi các bước kiểm tra trên domain thật đều đạt. Các cấu hình trong repo đã được chuẩn bị; chúng chưa tự tạo project hay deploy lên tài khoản cloud của bạn.

Tài liệu chính thức: [Railway Spring Boot](https://docs.railway.com/guides/spring-boot), [Railpack Java](https://railpack.com/languages/java/), [Vercel Vite](https://vercel.com/docs/frameworks/frontend/vite), [Vercel rewrites](https://vercel.com/docs/routing/rewrites).
