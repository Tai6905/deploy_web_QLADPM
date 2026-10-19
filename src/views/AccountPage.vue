<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from "vue";
import { shopflowApi, roleLabels, type AccountProfile, type ApiPage, type MyOrderDetail, type OrderSummary } from "../api/shopflowApi";
import { currentUser, dataSource } from "../auth/session";
import { money } from "../data";
import PaginationBar from "../components/PaginationBar.vue";

const props = defineProps<{ tab: "profile" | "purchases" }>();
const emit = defineEmits<{ logout: []; "password-changed": [] }>();
const profile = ref<AccountProfile | null>(null);
const form = reactive({ displayName: "", phone: "" });
const password = reactive({ currentPassword: "", newPassword: "", confirmation: "" });
const loading = ref(false), saving = ref(false), error = ref(""), success = ref("");
const page = ref(1), size = ref(10), query = ref(""), search = ref("");
const orders = ref<ApiPage<OrderSummary> | null>(null);
const detail = ref<MyOrderDetail | null>(null), detailLoading = ref(false);
const returnReason = ref(""), returnQuantities = reactive<Record<number, number>>({});
const returnBusy = ref(false), paymentBusy = ref(false);
let loadId = 0, detailId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
const date = (value: string | null | undefined) => value ? new Date(value).toLocaleString("vi-VN") : "—";
const deliveryLabels: Record<string, string> = { NONE: "Chờ chuẩn bị", PREPARING: "Đang chuẩn bị", SHIPPED: "Đang giao hàng", DELIVERED: "Đã giao hàng" };
const orderLabels: Record<string, string> = { PENDING_PAYMENT: "Chờ thanh toán", PAID: "Đã thanh toán", PAYMENT_FAILED: "Thanh toán chưa thành công" };
const returnLabels: Record<string, string> = { REQUESTED: "Đang chờ cửa hàng duyệt", APPROVED: "Đã chấp nhận", RESTOCKED: "Đã hoàn hàng vào kho", REJECTED: "Đã từ chối" };
const steps = ["NONE", "PREPARING", "SHIPPED", "DELIVERED"];
const deliveryStep = computed(() => steps.indexOf(detail.value?.order.deliveryStatus || "NONE"));

async function load() {
  if (dataSource !== "api") return;
  const id = ++loadId;
  loading.value = true; error.value = "";
  try {
    if (props.tab === "profile") {
      const result = await shopflowApi.getProfile();
      if (id !== loadId) return;
      profile.value = result;
      form.displayName = result.displayName; form.phone = result.phone || "";
    } else {
      const result = await shopflowApi.getMyOrders({ page: page.value - 1, size: size.value, query: query.value });
      if (id === loadId) orders.value = result;
    }
  } catch (cause) { if (id === loadId) error.value = cause instanceof Error ? cause.message : "Không tải được tài khoản."; }
  finally { if (id === loadId) loading.value = false; }
}
watch([() => props.tab, page, size, query], load, { immediate: true });
watch(search, value => { clearTimeout(timer); timer = setTimeout(() => { page.value = 1; query.value = value; }, 250); });
watch(() => props.tab, () => { detail.value = null; ++detailId; error.value = ""; success.value = ""; });
onUnmounted(() => { ++loadId; ++detailId; clearTimeout(timer); });

async function saveProfile() {
  if (saving.value) return;
  saving.value = true; error.value = ""; success.value = "";
  try {
    profile.value = await shopflowApi.updateProfile({ displayName: form.displayName.trim(), phone: form.phone.trim() || null });
    if (currentUser.value) currentUser.value = { ...currentUser.value, displayName: profile.value.displayName };
    success.value = "Đã lưu thông tin cá nhân.";
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "Không lưu được thông tin."; }
  finally { saving.value = false; }
}
async function changePassword() {
  if (saving.value) return;
  error.value = "";
  if (password.newPassword !== password.confirmation) { error.value = "Mật khẩu xác nhận không khớp."; return; }
  if (new TextEncoder().encode(password.newPassword).length > 72) { error.value = "Mật khẩu tối đa 72 byte UTF-8."; return; }
  saving.value = true;
  try {
    await shopflowApi.changePassword({ currentPassword: password.currentPassword, newPassword: password.newPassword });
    Object.assign(password, { currentPassword: "", newPassword: "", confirmation: "" });
    emit("password-changed");
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "Không đổi được mật khẩu."; }
  finally { saving.value = false; }
}
async function openOrder(id: number) {
  const request = ++detailId;
  detailLoading.value = true; error.value = "";
  try {
    const result = await shopflowApi.getMyOrder(id);
    if (request !== detailId) return;
    detail.value = result; returnReason.value = "";
    Object.keys(returnQuantities).forEach(key => delete returnQuantities[Number(key)]);
    result.returnableItems.forEach(item => { returnQuantities[item.orderItemId] = 0; });
  } catch (cause) { if (request === detailId) error.value = cause instanceof Error ? cause.message : "Không tải được đơn."; }
  finally { if (request === detailId) detailLoading.value = false; }
}
async function requestReturn() {
  if (!detail.value || returnBusy.value) return;
  const id = detail.value.order.id;
  const items = detail.value.returnableItems.filter(item => (returnQuantities[item.orderItemId] || 0) > 0)
    .map(item => ({ orderItemId: item.orderItemId, quantity: returnQuantities[item.orderItemId] || 0 }));
  if (!items.length || !returnReason.value.trim()) { error.value = "Chọn số lượng cần trả và nhập lý do."; return; }
  returnBusy.value = true; error.value = "";
  try {
    await shopflowApi.createMyReturn(id, { reason: returnReason.value.trim(), items });
    success.value = "Đã gửi yêu cầu đổi trả. Cửa hàng sẽ kiểm tra và phản hồi.";
    await openOrder(id);
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "Không gửi được yêu cầu."; }
  finally { returnBusy.value = false; }
}
async function pay() {
  if (!detail.value || paymentBusy.value) return;
  const id = detail.value.order.id;
  paymentBusy.value = true; error.value = "";
  try { await shopflowApi.simulatePayment(detail.value.order.orderRef, "SUCCESS"); await openOrder(id); await load(); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : "Không thanh toán được đơn."; }
  finally { paymentBusy.value = false; }
}
</script>

<template>
  <main class="account-page">
    <header class="account-header">
      <RouterLink to="/customer" class="store-brand"><strong>Mộc Studio</strong></RouterLink>
      <nav><RouterLink to="/customer">Cửa hàng</RouterLink><RouterLink to="/profile">Thông tin cá nhân</RouterLink><RouterLink to="/purchases">Đơn mua của tôi</RouterLink><button class="button button-outline" @click="emit('logout')">Đăng xuất</button></nav>
    </header>
    <div :key="tab" class="account-content">
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="success" class="form-success" role="status">{{ success }}</p>
      <p v-if="dataSource === 'demo'" class="panel account-card">Thông tin cá nhân và lịch sử mua hàng cần kết nối máy chủ.</p>
      <template v-else-if="tab === 'profile'">
        <div class="account-section-heading"><div><span class="auth-kicker">TÀI KHOẢN CỦA BẠN</span><h1>Thông tin cá nhân</h1></div><RouterLink v-if="currentUser?.role !== 'CUSTOMER'" to="/" class="button button-outline">Về trang vận hành</RouterLink></div>
        <p v-if="loading" role="status">Đang tải hồ sơ…</p>
        <div v-else-if="profile" class="account-grid">
          <section v-reveal class="panel account-card">
            <h2>Hồ sơ</h2>
            <form class="auth-form" @submit.prevent="saveProfile">
              <label class="form-label">Họ và tên<input v-model="form.displayName" required maxlength="255" autocomplete="name" /></label>
              <label class="form-label">Email đăng nhập<input :value="profile.email" readonly type="email" /></label>
              <label class="form-label">Số điện thoại<input v-model="form.phone" maxlength="20" type="tel" autocomplete="tel" /></label>
              <p>Vai trò: <strong>{{ roleLabels[profile.role] }}</strong></p>
              <p class="muted-cell">Tham gia: {{ date(profile.createdAt) }}</p>
              <button class="button button-primary" :disabled="saving">Lưu thông tin</button>
            </form>
          </section>
          <section v-reveal="2" class="panel account-card">
            <h2>Đổi mật khẩu</h2>
            <form class="auth-form" @submit.prevent="changePassword">
              <label class="form-label">Mật khẩu hiện tại<input v-model="password.currentPassword" type="password" required maxlength="72" autocomplete="current-password" /></label>
              <label class="form-label">Mật khẩu mới<input v-model="password.newPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password" /></label>
              <label class="form-label">Nhập lại mật khẩu mới<input v-model="password.confirmation" type="password" required minlength="8" maxlength="72" autocomplete="new-password" /></label>
              <p class="muted-cell">Sau khi đổi mật khẩu, bạn sẽ đăng nhập lại trên các thiết bị.</p>
              <button class="button button-primary" :disabled="saving">Đổi mật khẩu</button>
            </form>
          </section>
        </div>
      </template>
      <template v-else>
        <div class="account-section-heading"><div><span class="auth-kicker">MUA SẮM CỦA BẠN</span><h1>Lịch sử mua hàng</h1></div><button class="button button-outline" :disabled="loading" @click="load">Tải lại</button></div>
        <section class="panel account-card">
          <label class="form-label">Tìm đơn<input v-model="search" type="search" placeholder="Mã đơn hoặc tên sản phẩm" /></label>
          <p v-if="loading" role="status">Đang tải đơn mua…</p>
          <div class="table-scroll"><table><thead><tr><th>ĐƠN HÀNG</th><th>SẢN PHẨM</th><th>TỔNG TIỀN</th><th>THANH TOÁN</th><th>GIAO HÀNG</th><th></th></tr></thead><tbody>
            <tr v-for="order in orders?.items || []" :key="order.id"><td><strong>SF-{{ order.id }}</strong><small class="account-subtext">{{ date(order.createdAt) }}</small></td><td>{{ order.product }}<small class="account-subtext">{{ order.quantity }} sản phẩm</small></td><td>{{ money(order.totalAmount) }}</td><td>{{ orderLabels[order.status] }}</td><td>{{ deliveryLabels[order.deliveryStatus] }}</td><td><button class="button button-small button-outline" :disabled="detailLoading" @click="openOrder(order.id)">Chi tiết / Theo dõi</button></td></tr>
            <tr v-if="!loading && !orders?.items.length"><td colspan="6" class="empty-cell">Chưa có đơn mua phù hợp. Đơn đặt khi đăng nhập sẽ xuất hiện tại đây.</td></tr>
          </tbody></table></div>
          <PaginationBar :page="page" :page-size="size" :total="orders?.totalItems || 0" :total-pages="Math.max(1, orders?.totalPages || 0)" @change-page="page = $event" @change-size="size = $event; page = 1" />
        </section>
        <p v-if="detailLoading" role="status">Đang tải chi tiết đơn…</p>
        <section v-if="detail" :key="detail.order.id" v-reveal class="panel account-card order-detail">
          <div class="account-section-heading"><h2>Đơn SF-{{ detail.order.id }}</h2><button class="button button-outline" :disabled="detailLoading" @click="openOrder(detail.order.id)">Cập nhật trạng thái</button></div>
          <p class="muted-cell">Mã tra cứu: {{ detail.order.orderRef }}</p>
          <p>{{ orderLabels[detail.order.status] }} · {{ date(detail.order.createdAt) }}</p>
          <ol class="delivery-steps"><li v-for="(step, index) in steps" :key="step" :class="{ complete: index <= deliveryStep }">{{ deliveryLabels[step] }}</li></ol>
          <div class="account-grid"><div><h3>Thông tin nhận hàng</h3><p>{{ detail.order.shippingAddress.receiverName }} · {{ detail.order.shippingAddress.phone }}</p><p>{{ [detail.order.shippingAddress.addressLine, detail.order.shippingAddress.district, detail.order.shippingAddress.city].filter(Boolean).join(', ') }}</p></div><div><h3>Sản phẩm</h3><p v-for="item in detail.order.items" :key="item.variantId">{{ item.productName }} × {{ item.quantity }} — {{ money(item.lineTotal) }}</p><strong>Tổng thanh toán: {{ money(detail.order.totalAmount) }}</strong></div></div>
          <h3>Lịch sử giao hàng</h3>
          <ul v-if="detail.delivery.history.length" class="tracking-history"><li v-for="(event, index) in detail.delivery.history" :key="index"><strong>{{ deliveryLabels[event.toStatus] }}</strong><span>{{ date(event.changedAt) }}</span></li></ul>
          <p v-else class="muted-cell">Chưa có cập nhật giao hàng.</p>
          <template v-if="detail.returns.length"><h3>Theo dõi đổi trả</h3><ul class="tracking-history"><li v-for="request in detail.returns" :key="request.id"><span><strong>RT-{{ request.id }} · {{ returnLabels[request.status] }}</strong><small class="account-subtext">{{ request.reason }}</small></span><span>{{ date(request.updatedAt) }}</span></li></ul></template>
          <button v-if="detail.order.status === 'PENDING_PAYMENT'" class="button button-primary" :disabled="paymentBusy" @click="pay">Thanh toán thử nghiệm</button>
          <form v-if="detail.order.status === 'PAID' && detail.order.deliveryStatus === 'DELIVERED' && detail.returnableItems.some(item => item.returnableQuantity > 0)" class="auth-form return-request-form" @submit.prevent="requestReturn">
            <h3>Yêu cầu đổi trả</h3>
            <label v-for="item in detail.returnableItems.filter(item => item.returnableQuantity > 0)" :key="item.orderItemId" class="form-label">{{ item.productName }} (có thể trả {{ item.returnableQuantity }})<input v-model.number="returnQuantities[item.orderItemId]" type="number" min="0" :max="item.returnableQuantity" step="1" /></label>
            <label class="form-label">Lý do<textarea v-model="returnReason" required maxlength="500" rows="3" /></label>
            <button class="button button-outline" :disabled="returnBusy">Gửi yêu cầu đổi trả</button>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
