<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowRight, Store } from "lucide-vue-next";
import { canAccessPage, landingPage } from "../auth/permissions";
import {
  currentRole,
  dataSource,
  setDemoRole,
  signIn,
  signUp,
} from "../auth/session";
import type { UserRole } from "../api/shopflowApi";

const route = useRoute();
const router = useRouter();
const loginForm = reactive({ email: "", password: "", displayName: "" });
const loginMode = ref<"login" | "register">("login");
const loginError = ref("");
const submitting = ref(false);

async function submitLogin() {
  if (submitting.value) return;
  submitting.value = true;
  loginError.value = "";
  try {
    if (loginMode.value === "register") {
      if (!loginForm.displayName.trim()) {
        loginError.value = "Vui lòng nhập tên hiển thị.";
        return;
      }
      await signUp({
        email: loginForm.email.trim(),
        password: loginForm.password,
        displayName: loginForm.displayName.trim(),
      });
    } else {
      await signIn(loginForm.email.trim(), loginForm.password);
    }

    const requestedRedirect =
      typeof route.query.redirect === "string" ? route.query.redirect : "";
    const requestedPage = requestedRedirect.split("?")[0].slice(1);
    const redirect =
      requestedRedirect.startsWith("/") &&
      !requestedRedirect.startsWith("//") &&
      requestedPage !== "login" &&
      requestedPage !== "forbidden" &&
      canAccessPage(requestedPage, currentRole.value)
        ? requestedRedirect
        : landingPage(currentRole.value);
    await router.replace(redirect);
  } catch (error) {
    loginError.value =
      error instanceof Error ? error.message : "Không thể đăng nhập.";
  } finally {
    submitting.value = false;
  }
}

function selectDemoRole(role: UserRole | null) {
  setDemoRole(role);
  void router.push(landingPage(role));
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <RouterLink to="/customer" class="store-brand">
        <span class="brand-mark"><span></span><span></span><span></span></span>
        <strong>ShopFlow</strong>
      </RouterLink>
      <span class="auth-kicker">SHOPFLOW · TÀI KHOẢN</span>
      <h1 :key="loginMode" class="auth-title">{{ loginMode === "register" ? "Tạo tài khoản" : "Đăng nhập" }}</h1>
      <p>
        {{
          loginMode === "register"
            ? "Tạo tài khoản khách hàng để bắt đầu."
            : "Đăng nhập để tiếp tục quản lý cửa hàng."
        }}
      </p>
      <form v-if="dataSource === 'api'" class="auth-form" @submit.prevent="submitLogin">
        <label v-if="loginMode === 'register'" class="form-label">
          Tên hiển thị
          <input
            v-model="loginForm.displayName"
            autocomplete="name"
            required
            placeholder="Tên của bạn"
          />
        </label>
        <label class="form-label">
          Email
          <input
            v-model="loginForm.email"
            autocomplete="email"
            type="email"
            required
            placeholder="ban@email.com"
          />
        </label>
        <label class="form-label">
          Mật khẩu
          <input
            v-model="loginForm.password"
            :autocomplete="loginMode === 'register' ? 'new-password' : 'current-password'"
            required
            maxlength="72"
            minlength="8"
            type="password"
            placeholder="Mật khẩu"
          />
        </label>
        <p v-if="loginError" class="form-error" role="alert">{{ loginError }}</p>
        <button class="button button-primary auth-submit" type="submit" :disabled="submitting" :aria-busy="submitting">
          {{ submitting ? "Đang xử lý…" : loginMode === "register" ? "Đăng ký" : "Đăng nhập" }}
          <ArrowRight :size="16" />
        </button>
        <button
          type="button"
          class="guest-entry"
          @click="loginMode = loginMode === 'register' ? 'login' : 'register'"
        >
          {{ loginMode === "register" ? "Đã có tài khoản? Đăng nhập" : "Tạo tài khoản mới" }}
        </button>
      </form>
      <div v-else class="demo-role-list">
        <p>Chọn vai trò demo để kiểm tra quyền truy cập.</p>
        <button @click="selectDemoRole('SHOP_OWNER')">
          <span class="role-badge owner-role">SO</span>
          <span><strong>Chủ shop</strong><small>Toàn quyền vận hành</small></span>
          <ArrowRight :size="16" />
        </button>
        <button @click="selectDemoRole('WAREHOUSE')">
          <span class="role-badge warehouse-role">WH</span>
          <span><strong>Nhân viên kho</strong><small>Tồn kho, giao hàng, nhập hàng</small></span>
          <ArrowRight :size="16" />
        </button>
        <button @click="selectDemoRole('CUSTOMER')">
          <span class="role-badge customer-role">KH</span>
          <span><strong>Khách hàng</strong><small>Xem catalog và đặt hàng</small></span>
          <ArrowRight :size="16" />
        </button>
        <button class="guest-entry" @click="selectDemoRole(null)">
          Tiếp tục với tư cách khách <ArrowRight :size="15" />
        </button>
      </div>
      <RouterLink to="/customer" class="back-store-link">
        <Store :size="15" /> Xem cửa hàng
      </RouterLink>
    </section>
  </main>
</template>
