<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { shopflowApi, type ApiPage, type OrderResponse, type OrderSummary } from '../api/shopflowApi';
import { ApiError } from '../api/httpClient';
import { currentRole, dataSource } from '../auth/session';
import { canPerform } from '../auth/permissions';
import { canCancelOrder, cancellationBlockReason, cancellationReasons } from '../orders/cancellations';
import { money } from '../data';
import StorefrontNav from '../components/StorefrontNav.vue';
import PaginationBar from '../components/PaginationBar.vue';
const route = useRoute();
const staff = computed(() => canPerform('manageOrders', currentRole.value) && route.query.scope !== 'mine');
const scope = computed(() => staff.value ? 'all' : 'mine');
const selectedId = computed(() => {
  const value = route.query.order;
  return typeof value === 'string' && /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : null;
});
const orders = ref<ApiPage<OrderSummary> | null>(null), selected = ref<OrderResponse | null>(null);
const page = ref(1), size = ref(10), search = ref(''), query = ref(''), reason = ref('');
const loading = ref(false), detailLoading = ref(false), busy = ref(false);
const listError = ref(''), detailError = ref(''), actionError = ref(''), success = ref(''), navError = ref('');
let listId = 0, detailId = 0, mounted = true;
let timer: ReturnType<typeof setTimeout> | undefined;
const date = (value: string) => new Date(value).toLocaleString('vi-VN');
const canSubmit = computed(() => canCancelOrder(selected.value) && reason.value.trim().length > 0 && reason.value.trim().length <= 500 && !busy.value && !detailLoading.value);
async function loadList() {
  if (dataSource !== 'api') return;
  const id = ++listId; loading.value = true; listError.value = ''; orders.value = null;
  try {
    const input = { page: page.value - 1, size: size.value, query: query.value, status: 'CANCELLED' as const };
    const result = staff.value ? await shopflowApi.getOrders(input) : await shopflowApi.getMyOrders(input);
    if (id !== listId) return;
    if (result.totalPages > 0 && page.value > result.totalPages) { page.value = result.totalPages; return; }
    orders.value = result;
  } catch (cause) { if (id === listId) listError.value = cause instanceof Error ? cause.message : 'Không tải được danh sách đơn hủy.'; }
  finally { if (id === listId) loading.value = false; }
}
async function loadSelected() {
  const id = ++detailId; selected.value = null; detailError.value = ''; actionError.value = ''; success.value = ''; reason.value = ''; detailLoading.value = false;
  if (dataSource !== 'api' || !selectedId.value) {
    if (route.query.order != null && !selectedId.value) detailError.value = 'Mã đơn không hợp lệ.';
    return;
  }
  detailLoading.value = true;
  try {
    const result = staff.value ? await shopflowApi.getOrder(selectedId.value) : (await shopflowApi.getMyOrder(selectedId.value)).order;
    if (id === detailId) selected.value = result;
  } catch (cause) { if (id === detailId) detailError.value = cause instanceof Error ? cause.message : 'Không tải được đơn hàng.'; }
  finally { if (id === detailId) detailLoading.value = false; }
}
async function cancel() {
  if (!canSubmit.value || !selected.value) return;
  const id = selected.value.id, generation = detailId, asStaff = staff.value;
  const clean = reason.value.trim();
  busy.value = true; actionError.value = ''; success.value = '';
  try {
    const result = asStaff ? await shopflowApi.cancelOrder(id, clean) : await shopflowApi.cancelMyOrder(id, clean);
    if (!mounted || generation !== detailId) return;
    selected.value = result; reason.value = ''; success.value = `Đã hủy đơn SF-${id}. Tồn kho và lượt dùng voucher đã được cập nhật.`;
    if (page.value !== 1) page.value = 1; else await loadList();
  } catch (cause) {
    if (!mounted || generation !== detailId) return;
    const message = cause instanceof Error ? cause.message : 'Không hủy được đơn hàng. Bạn có thể thử lại.';
    if (cause instanceof ApiError && cause.status === 409) {
      const refreshed = detailId + 1;
      await loadSelected();
      if (!mounted || detailId !== refreshed) return;
    }
    actionError.value = message;
  } finally { if (mounted) busy.value = false; }
}
watch([staff, page, size, query], loadList, { immediate: true });
watch([() => route.query.order, staff], loadSelected, { immediate: true });
watch(staff, () => { page.value = 1; search.value = ''; query.value = ''; });
watch(search, value => { clearTimeout(timer); timer = setTimeout(() => { page.value = 1; query.value = value; }, 250); });
onUnmounted(() => { mounted = false; ++listId; ++detailId; clearTimeout(timer); });
</script>
<template>
  <main class="commerce-page"><StorefrontNav v-model:error="navError" />
    <div class="commerce-container cancellations-container">
      <div class="cancellations-heading"><div><span class="auth-kicker">SHOPFLOW · ĐƠN HÀNG</span><h1>{{ staff ? 'Quản lý hủy đơn' : 'Đơn đã hủy của tôi' }}</h1><p>{{ staff ? 'Theo dõi các đơn đã hủy và lý do hủy của cửa hàng.' : 'Xem lại lý do, thời gian hủy và trạng thái thanh toán của các đơn bạn đã đặt.' }}</p></div><RouterLink :to="staff ? '/orders' : '/purchases'" class="button button-outline">{{ staff ? 'Về quản lý đơn hàng' : 'Về đơn mua của tôi' }} →</RouterLink></div>
      <div v-if="canPerform('manageOrders', currentRole)" class="cancellation-scope"><RouterLink to="/cancellations" :class="{ selected: staff }">Đơn hủy của cửa hàng</RouterLink><RouterLink to="/cancellations?scope=mine" :class="{ selected: !staff }">Đơn hủy của tôi</RouterLink></div>
      <p v-if="navError" class="form-error" role="alert">{{ navError }}</p>
      <p v-if="dataSource === 'demo'" class="cancellation-notice">Hủy đơn và lịch sử hủy cần kết nối máy chủ. Không có đơn hàng nào bị thay đổi trong chế độ demo.</p>
      <template v-else>
        <p v-if="detailLoading" role="status">Đang kiểm tra trạng thái đơn hàng…</p>
        <div v-if="detailError" class="cancellation-notice" role="alert">{{ detailError }} <button type="button" class="text-link" @click="loadSelected">Thử lại</button></div>
        <section v-if="selected" class="cancellation-card">
          <div class="account-section-heading"><div><span class="auth-kicker">{{ selected.status === 'CANCELLED' ? 'THÔNG TIN ĐƠN HỦY' : 'KIỂM TRA TRƯỚC KHI HỦY' }}</span><h2>Đơn SF-{{ selected.id }}</h2></div><RouterLink :to="{ path: '/cancellations', query: { scope } }" class="text-link">Đóng chi tiết</RouterLink></div>
          <p class="muted-cell">Đặt lúc {{ date(selected.createdAt) }} · {{ selected.paymentMethod === 'COD' ? 'Thanh toán khi nhận hàng' : 'Thanh toán thử nghiệm' }}</p>
          <ul class="cancellation-products"><li v-for="item in selected.items" :key="item.variantId"><span>{{ item.productName }} × {{ item.quantity }}</span><strong>{{ money(item.lineTotal) }}</strong></li></ul><p class="cancellation-total">Tổng đơn <strong>{{ money(selected.totalAmount) }}</strong></p>
          <p v-if="success" class="form-success" role="status">{{ success }}</p><p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
          <div v-if="selected.cancellation" class="cancellation-receipt"><strong>Đã hủy lúc {{ date(selected.cancellation.cancelledAt) }}</strong><p>Lý do: {{ selected.cancellation.reason }}</p><p>Người hủy: {{ selected.cancellation.actorKind === 'STAFF' ? 'Cửa hàng' : 'Khách hàng' }}</p><p>{{ selected.cancellation.paymentOutcome === 'SIMULATED_REFUND' ? `Hoàn tiền mô phỏng: ${money(selected.cancellation.refundAmount)}. Chức năng này không chuyển tiền qua ngân hàng thực tế.` : 'Đơn chưa thu tiền; không phát sinh khoản hoàn tiền.' }}</p></div>
          <form v-else-if="canCancelOrder(selected)" class="cancellation-form" @submit.prevent="cancel">
            <p class="cancellation-notice">{{ selected.status === 'PAID' ? 'Đơn đã thanh toán qua chức năng thử nghiệm. Khi hủy, hệ thống ghi nhận hoàn tiền mô phỏng và trả hàng đang giữ về lượng có thể bán.' : 'Khi hủy, hàng đang giữ sẽ được mở bán lại và lượt sử dụng voucher của đơn được hoàn lại.' }} Đơn đã hủy không thể khôi phục.</p>
            <fieldset :disabled="busy"><legend>Lý do hủy đơn</legend><div class="cancellation-reasons"><button v-for="choice in cancellationReasons" :key="choice" type="button" :class="{ selected: reason === choice }" @click="reason = choice">{{ choice }}</button></div><label class="form-label">Lý do của bạn<textarea v-model="reason" required maxlength="500" rows="3" placeholder="Chọn một lý do ở trên hoặc nhập lý do khác…" /></label><small>{{ reason.length }}/500 ký tự</small></fieldset>
            <div class="cancellation-actions"><button type="submit" class="button button-cancel-order" :disabled="!canSubmit">{{ busy ? 'Đang hủy đơn…' : 'Xác nhận hủy đơn' }}</button><RouterLink :to="staff ? '/orders' : '/purchases'" class="button button-outline">Tiếp tục giữ đơn</RouterLink></div>
          </form><p v-else class="cancellation-notice">{{ cancellationBlockReason(selected) }}</p>
        </section>
        <section class="cancellation-card"><div class="account-section-heading"><h2>Lịch sử hủy đơn</h2><button type="button" class="button button-outline" :disabled="loading || busy" @click="loadList">Tải lại</button></div><label class="form-label">Tìm đơn đã hủy<input v-model="search" type="search" placeholder="Mã đơn, tên sản phẩm…" /></label>
          <p v-if="loading" role="status">Đang tải đơn đã hủy…</p><p v-if="listError" class="form-error" role="alert">{{ listError }}</p>
          <div v-if="orders" class="table-scroll"><table class="cancellations-table"><thead><tr><th>ĐƠN HÀNG</th><th>SẢN PHẨM</th><th>LÝ DO HỦY</th><th>THANH TOÁN</th><th></th></tr></thead><tbody><tr v-for="order in orders.items" :key="order.id"><td><strong>SF-{{ order.id }}</strong><small>{{ order.cancellation ? date(order.cancellation.cancelledAt) : 'Đã hủy' }}</small><small v-if="staff">{{ order.customer }}</small></td><td>{{ order.product }}<small>{{ order.quantity }} sản phẩm · {{ money(order.totalAmount) }}</small></td><td>{{ order.cancellation?.reason || 'Chưa có lý do' }}<small>{{ order.cancellation?.actorKind === 'STAFF' ? 'Cửa hàng hủy' : 'Khách hàng hủy' }}</small></td><td>{{ order.cancellation?.paymentOutcome === 'SIMULATED_REFUND' ? `Hoàn tiền mô phỏng ${money(order.cancellation.refundAmount)}` : 'Chưa thu tiền' }}</td><td><RouterLink :to="{ path: '/cancellations', query: { order: order.id, scope } }" class="text-link">Chi tiết →</RouterLink></td></tr><tr v-if="!orders.items.length"><td colspan="5" class="empty-cell">{{ query ? 'Không tìm thấy đơn hủy phù hợp.' : 'Chưa có đơn hàng nào bị hủy.' }}</td></tr></tbody></table></div>
          <PaginationBar :page="page" :page-size="size" :total="orders?.totalItems || 0" :total-pages="Math.max(1, orders?.totalPages || 0)" @change-page="page = $event" @change-size="size = $event; page = 1" />
        </section>
      </template>
    </div>
  </main>
</template>
