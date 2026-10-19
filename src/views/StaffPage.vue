<script setup lang="ts">
import { onUnmounted, reactive, ref, watch } from "vue";
import { shopflowApi, roleLabels, type AccountProfile, type ApiPage } from "../api/shopflowApi";
import { dataSource } from "../auth/session";
import PaginationBar from "../components/PaginationBar.vue";

const form = reactive({ displayName: "", email: "", password: "", phone: "", role: "SALES_STAFF" as "SALES_STAFF" | "WAREHOUSE", active: true });
const editingId = ref<number | null>(null);
const loading = ref(false), search = ref(""), query = ref(""), page = ref(1), size = ref(10);
const result = ref<ApiPage<AccountProfile> | null>(null);
let loadId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
const saving = ref(false);
const error = ref("");
const success = ref("");

async function load() {
  if (dataSource !== "api") return;
  const id = ++loadId;
  loading.value = true; error.value = "";
  try {
    const next = await shopflowApi.getEmployees({ page: page.value - 1, size: size.value, query: query.value });
    if (id === loadId) result.value = next;
  } catch (cause) { if (id === loadId) error.value = cause instanceof Error ? cause.message : "Không tải được nhân viên."; }
  finally { if (id === loadId) loading.value = false; }
}
watch([page, size, query], load, { immediate: true });
watch(search, value => { clearTimeout(timer); timer = setTimeout(() => { page.value = 1; query.value = value; }, 250); });
onUnmounted(() => { ++loadId; clearTimeout(timer); });
function reset() {
  editingId.value = null;
  Object.assign(form, { displayName: "", email: "", password: "", phone: "", role: "SALES_STAFF", active: true });
}
function edit(employee: AccountProfile) {
  editingId.value = employee.userId;
  Object.assign(form, { displayName: employee.displayName, email: employee.email, password: "", phone: employee.phone || "", role: employee.role, active: employee.active });
}
async function toggle(employee: AccountProfile) {
  if (saving.value || !["SALES_STAFF", "WAREHOUSE"].includes(employee.role)) return;
  saving.value = true; error.value = "";
  try {
    await shopflowApi.updateEmployee(employee.userId, { displayName: employee.displayName, phone: employee.phone, role: employee.role as "SALES_STAFF" | "WAREHOUSE", active: !employee.active });
    success.value = employee.active ? "Đã khóa tài khoản và thu hồi phiên đăng nhập." : "Đã mở lại tài khoản.";
    await load();
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "Không cập nhật được trạng thái."; }
  finally { saving.value = false; }
}

async function createEmployee() {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  success.value = "";
  try {
    if (editingId.value !== null) {
      await shopflowApi.updateEmployee(editingId.value, { displayName: form.displayName.trim(), phone: form.phone.trim() || null, role: form.role, active: form.active });
      success.value = "Đã cập nhật thông tin và quyền nhân viên.";
    } else {
    const employee = await shopflowApi.createEmployee({
      displayName: form.displayName.trim(),
      email: form.email.trim(),
      password: form.password,
      role: form.role,
    });
    success.value = `Đã tạo tài khoản nhân viên ${employee.username}.`;
    page.value = 1;
    }
    reset(); await load();
  } catch (cause) {
    error.value =
      cause instanceof Error ? cause.message : "Không thể tạo tài khoản nhân viên.";
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section v-if="dataSource === 'api'" class="panel data-panel" aria-labelledby="staff-heading">
    <div class="table-toolbar">
      <div>
        <span class="auth-kicker">QUẢN TRỊ CỬA HÀNG</span>
        <h2 id="staff-heading">Quản lý tài khoản nhân viên</h2>
      </div>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="success" class="form-success" role="status">{{ success }}</p>
    <div class="account-grid">
    <form class="auth-form staff-form" @submit.prevent="createEmployee">
      <h3>{{ editingId === null ? 'Tạo nhân viên' : 'Cập nhật nhân viên' }}</h3>
      <label class="form-label">
        Tên nhân viên
        <input v-model="form.displayName" autocomplete="name" required maxlength="255" />
      </label>
      <label class="form-label">
        Email đăng nhập
        <input v-model="form.email" type="email" autocomplete="email" required maxlength="255" :readonly="editingId !== null" />
      </label>
      <label v-if="editingId === null" class="form-label">
        Mật khẩu tạm thời
        <input
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          required
          minlength="8"
          maxlength="72"
        />
      </label>
      <label v-else class="form-label">Số điện thoại<input v-model="form.phone" type="tel" maxlength="20" /></label>
      <label class="form-label">Vai trò<select v-model="form.role"><option value="SALES_STAFF">Nhân viên bán hàng</option><option value="WAREHOUSE">Nhân viên kho</option></select></label>
      <label v-if="editingId !== null" class="account-checkbox"><input v-model="form.active" type="checkbox" /> Cho phép đăng nhập</label>
      <p class="muted-cell">Bán hàng: đơn hàng và giao hàng. Kho: tồn kho, nhập hàng, giao hàng và hoàn kho.</p>
      <button class="button button-primary" type="submit" :disabled="saving">
        {{ saving ? "Đang lưu..." : editingId === null ? "Tạo tài khoản nhân viên" : "Lưu thay đổi" }}
      </button>
      <button v-if="editingId !== null" type="button" class="button button-outline" @click="reset">Hủy sửa</button>
    </form>
    <div class="account-card staff-list">
      <div class="account-section-heading"><h3>Danh sách nhân viên</h3><button class="button button-outline" :disabled="loading" @click="load">Tải lại</button></div>
      <label class="form-label">Tìm nhân viên<input v-model="search" type="search" placeholder="Tên hoặc email" /></label>
      <p v-if="loading" role="status">Đang tải nhân viên…</p>
      <div class="table-scroll"><table><thead><tr><th>NHÂN VIÊN</th><th>VAI TRÒ</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
        <tr v-for="employee in result?.items || []" :key="employee.userId"><td><strong>{{ employee.displayName }}</strong><small class="account-subtext">{{ employee.email }}</small></td><td>{{ roleLabels[employee.role] }}</td><td>{{ employee.active ? 'Đang hoạt động' : 'Đã khóa' }}</td><td><div class="account-actions"><button class="button button-small button-outline" :disabled="saving" @click="edit(employee)">Sửa</button><button class="button button-small button-outline" :disabled="saving" @click="toggle(employee)">{{ employee.active ? 'Khóa' : 'Mở khóa' }}</button></div></td></tr>
        <tr v-if="!loading && !result?.items.length"><td colspan="4" class="empty-cell">Chưa có nhân viên phù hợp.</td></tr>
      </tbody></table></div>
      <PaginationBar :page="page" :page-size="size" :total="result?.totalItems || 0" :total-pages="Math.max(1, result?.totalPages || 0)" @change-page="page = $event" @change-size="size = $event; page = 1" />
    </div>
    </div>
  </section>
  <section v-else class="panel data-panel">
    <h2>Đổi sang chế độ API để tạo tài khoản nhân viên.</h2>
    <p class="muted-cell">Đặt VITE_DATA_SOURCE=api và khởi động lại frontend.</p>
  </section>
</template>
