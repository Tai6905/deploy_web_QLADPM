<script setup lang="ts">
import { ref } from "vue";
import StorefrontNav from "../components/StorefrontNav.vue";
defineProps<{ section: "about" | "guide" }>();
const error = ref("");
</script>
<template>
  <main class="commerce-page"><StorefrontNav v-model:error="error" /><p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <section class="story-hero"><span class="auth-kicker">MỘC STUDIO · TỪ NHỮNG ĐIỀU NHỎ</span><h1>{{ section === 'about' ? 'Một góc sống giản dị.\nMột ngày nhiều cảm hứng.' : 'Chọn đúng. Dùng bền.\nYêu những điều thường ngày.' }}</h1><p>{{ section === 'about' ? 'Mộc Studio là cửa hàng đồ dùng và phụ kiện cho nhịp sống hằng ngày. Chúng tôi muốn việc chọn một món đồ trở nên rõ ràng và gần gũi hơn.' : 'Một vài hướng dẫn để bạn chọn sản phẩm, đặt hàng và chăm sóc món đồ mình yêu thích.' }}</p><RouterLink to="/customer" class="button button-primary">Khám phá cửa hàng →</RouterLink></section>
    <section v-if="section === 'about'" class="story-grid commerce-container">
      <article v-reveal class="story-card"><span>01 · CÂU CHUYỆN</span><h2>Bắt đầu từ sự vừa đủ</h2><p>Một chiếc bình, món đồ trên bàn hay phụ kiện quen thuộc đều có thể làm ngày thường dễ chịu hơn. Mộc Studio hướng tới những lựa chọn thiết thực, dễ phối hợp với góc sống riêng của bạn.</p></article>
      <article v-reveal="1" class="story-card"><span>02 · CÁCH CHÚNG TÔI LÀM</span><h2>Thông tin rõ ràng</h2><p>Xem ảnh, mô tả, hướng dẫn sử dụng và tồn kho ngay trên trang sản phẩm. Những đánh giá có nhãn đã mua được gửi từ tài khoản đã mua và nhận sản phẩm.</p></article>
      <article v-reveal="2" class="story-card"><span>03 · SAU KHI ĐẶT HÀNG</span><h2>Luôn biết đơn ở đâu</h2><p>Theo dõi các bước chuẩn bị, đang giao và đã nhận trong Đơn mua. Khi có vấn đề với sản phẩm đủ điều kiện, bạn có thể gửi yêu cầu đổi trả từ chính đơn hàng.</p></article>
    </section>
    <section v-else class="commerce-container guide-list">
      <details open><summary>Chọn sản phẩm như thế nào?</summary><p>Dùng bộ lọc danh mục, giá và tình trạng còn hàng. Bấm vào ảnh hoặc tên sản phẩm để xem bộ ảnh và thông tin chi tiết. Đọc hướng dẫn riêng của từng sản phẩm trước khi sử dụng.</p></details>
      <details><summary>Cách đặt hàng và theo dõi đơn</summary><p>Thêm món yêu thích vào giỏ, đăng nhập, nhập địa chỉ nhận hàng và xác nhận đơn. Vào Đơn mua để xem trạng thái thanh toán, tiến trình giao hàng và lịch sử cập nhật. Trong bản đồ án, thanh toán được mô phỏng.</p></details>
      <details><summary>Bảo quản và sử dụng sản phẩm</summary><p>Làm theo hướng dẫn nhà sản xuất và mục Hướng dẫn sử dụng trên từng trang sản phẩm. Vệ sinh đúng chất liệu, để khô trước khi cất giữ. Nếu chưa có hướng dẫn cụ thể, đừng tự giả định sản phẩm dùng được với nhiệt cao hoặc máy rửa.</p></details>
      <details><summary>Gửi đánh giá và yêu cầu đổi trả</summary><p>Sau khi đơn được thanh toán và giao thành công, mở trang sản phẩm để đánh giá từ 1 đến 5 sao. Bạn có thể cập nhật đánh giá đã gửi. Yêu cầu đổi trả được tạo trong chi tiết Đơn mua và sẽ được cửa hàng xem xét.</p></details>
    </section>
    <footer class="commerce-footer"><strong>Mộc Studio</strong><span>Đồ dùng cho ngày thường tốt hơn.</span><RouterLink to="/customer">Tiếp tục mua sắm →</RouterLink></footer>
  </main>
</template>
