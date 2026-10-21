<script setup lang="ts">
import { ref } from "vue";
import StorefrontNav from "../components/StorefrontNav.vue";
defineProps<{ section: "about" | "guide" }>();
const error = ref("");
</script>
<template>
  <main class="commerce-page"><StorefrontNav v-model:error="error" /><p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <section class="story-hero"><span class="auth-kicker">SHOPFLOW · NHẠC CỤ & ĐAM MÊ</span><h1>{{ section === 'about' ? 'Chạm từng âm sắc.\nViết câu chuyện của bạn.' : 'Chọn đúng nhạc cụ.\nBắt đầu hành trình âm nhạc.' }}</h1><p>{{ section === 'about' ? 'ShopFlow là cửa hàng nhạc cụ dành cho người mới bắt đầu, người luyện tập thường xuyên và những ai muốn mang âm nhạc lên sân khấu. Khám phá guitar, piano, violin, trống và kèn theo nhu cầu của bạn.' : 'Tìm hiểu cách chọn nhạc cụ, đặt hàng, theo dõi đơn và bảo quản đàn, trống, kèn trong quá trình sử dụng.' }}</p><RouterLink to="/customer" class="button button-primary">Khám phá nhạc cụ →</RouterLink></section>
    <section v-if="section === 'about'" class="story-grid commerce-container">
      <article v-reveal class="story-card"><span>01 · CÂU CHUYỆN</span><h2>Bắt đầu từ một giai điệu</h2><p>Một hợp âm guitar, một giai điệu piano hay nhịp trống đầu tiên đều có thể mở ra đam mê mới. ShopFlow giúp bạn khám phá nhạc cụ theo mục tiêu học tập, luyện tập và biểu diễn.</p></article>
      <article v-reveal="1" class="story-card"><span>02 · CÁCH CHÚNG TÔI LÀM</span><h2>Thông tin rõ ràng</h2><p>Xem ảnh, mô tả, hướng dẫn sử dụng và tồn kho ngay trên trang sản phẩm. Những đánh giá có nhãn đã mua được gửi từ tài khoản đã mua và nhận sản phẩm.</p></article>
      <article v-reveal="2" class="story-card"><span>03 · SAU KHI ĐẶT HÀNG</span><h2>Luôn biết đơn ở đâu</h2><p>Theo dõi các bước chuẩn bị, đang giao và đã nhận trong Đơn mua. Khi có vấn đề với sản phẩm đủ điều kiện, bạn có thể gửi yêu cầu đổi trả từ chính đơn hàng.</p></article>
    </section>
    <section v-else class="commerce-container guide-list">
      <details open><summary>Nên chọn nhạc cụ như thế nào?</summary><p>Chọn nhóm nhạc cụ theo mục tiêu học và phong cách chơi, sau đó lọc theo ngân sách và tình trạng còn hàng. Xem mô tả, cỡ đàn, hướng dẫn và ảnh trên trang chi tiết. Với violin, hãy nhờ giáo viên tư vấn cỡ đàn; với piano và trống, kiểm tra không gian đặt nhạc cụ trước khi mua.</p></details>
      <details><summary>Cách đặt hàng và theo dõi đơn</summary><p>Thêm nhạc cụ vào giỏ, đăng nhập và mở trang thanh toán để chọn địa chỉ, voucher và phương thức thanh toán. COD được thu tiền khi giao hàng; thẻ và chuyển khoản hiện là luồng mô phỏng của đồ án. Vào Đơn mua để xem trạng thái và lịch sử giao hàng.</p></details>
      <details><summary>Bảo quản nhạc cụ sau khi chơi</summary><p>Lau bằng khăn mềm phù hợp, cất nhạc cụ trong bao hoặc hộp khi di chuyển, tránh nước và thay đổi nhiệt độ đột ngột. Mỗi nhạc cụ có cách bảo quản riêng: đọc mục Hướng dẫn sử dụng của sản phẩm và hướng dẫn nhà sản xuất. Việc chỉnh cần, bộ máy hoặc sửa cơ cấu nên do người có chuyên môn thực hiện.</p></details>
      <details><summary>Gửi đánh giá và yêu cầu đổi trả</summary><p>Sau khi đơn được thanh toán và giao thành công, mở trang sản phẩm để đánh giá từ 1 đến 5 sao. Bạn có thể cập nhật đánh giá đã gửi. Yêu cầu đổi trả được tạo trong chi tiết Đơn mua và sẽ được cửa hàng xem xét.</p></details>
    </section>
    <footer class="commerce-footer"><strong>ShopFlow</strong><span>Nhạc cụ cho từng nhịp đam mê.</span><RouterLink to="/customer">Tiếp tục khám phá →</RouterLink></footer>
  </main>
</template>
