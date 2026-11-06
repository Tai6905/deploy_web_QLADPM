<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ArrowRight, Eye, EyeOff, LockKeyhole, Mail, Music2, ShieldCheck, UserRound } from "lucide-vue-next";
import MusicScene from "../components/MusicScene.vue";
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
const showPassword = ref(false);
const confirmPassword = ref("");

function changeMode(mode: "login" | "register") {
  if (submitting.value || mode === loginMode.value) return;
  loginMode.value = mode;
  loginError.value = "";
  confirmPassword.value = "";
  showPassword.value = false;
}

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
      if (loginForm.password !== confirmPassword.value) {
        loginError.value = "Mật khẩu nhập lại chưa khớp. Bạn kiểm tra lại nhé.";
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
  <main class="auth-scene">
    <section class="auth-story" aria-label="ShopFlow, không gian âm nhạc của bạn">
      <RouterLink to="/customer" class="store-brand auth-brand"><span class="brand-mark"><span></span><span></span><span></span></span><strong>ShopFlow<span>INSTRUMENTS & INSPIRATION</span></strong></RouterLink>
      <div class="auth-story-copy"><span class="studio-eyebrow"><span class="live-dot"></span> KHÔNG GIAN CỦA NHỮNG ĐAM MÊ</span><h2>Mỗi giai điệu.<br />Một <em>câu chuyện.</em></h2><p>Tìm nhạc cụ dành cho bạn.<br />Viết tiếp hành trình âm nhạc của riêng mình.</p></div>
      <MusicScene />
      <div class="auth-story-footer"><span><Music2 :size="16" /> Find your sound. Feel your flow.</span><span>EST. SHOPFLOW</span></div>
    </section>
    <section class="auth-entry">
      <RouterLink to="/customer" class="auth-back"><ArrowLeft :size="16" /> Về cửa hàng</RouterLink>
      <div class="auth-entry-inner">
        <div class="auth-welcome-icon"><Music2 :size="25" /><span></span></div>
        <div class="auth-mode-switch" role="group" aria-label="Chọn đăng nhập hoặc đăng ký" :class="{ 'is-register': loginMode === 'register' }">
          <span class="auth-mode-indicator" aria-hidden="true"></span>
          <button type="button" :aria-pressed="loginMode === 'login'" :disabled="submitting" @click="changeMode('login')">Đăng nhập</button>
          <button type="button" :aria-pressed="loginMode === 'register'" :disabled="submitting" @click="changeMode('register')">Đăng ký</button>
        </div>
        <div :key="loginMode" class="auth-heading"><span class="studio-eyebrow">YOUR SOUND STARTS HERE</span><h1>{{ loginMode === 'register' ? 'Bắt đầu cùng ShopFlow.' : 'Chào mừng bạn trở lại.' }}</h1><p>{{ loginMode === 'register' ? 'Một tài khoản, cả thế giới âm nhạc chờ bạn khám phá.' : 'Nhạc cụ yêu thích và hành trình âm nhạc đang chờ bạn.' }}</p></div>
        <form v-if="dataSource === 'api'" class="studio-auth-form" @submit.prevent="submitLogin">
          <fieldset :disabled="submitting">
            <Transition name="auth-field">
              <label v-if="loginMode === 'register'" class="studio-field"><span>Tên của bạn</span><span class="studio-input"><UserRound :size="18" /><input v-model="loginForm.displayName" autocomplete="name" maxlength="100" required placeholder="Bạn muốn được gọi là gì?" /></span></label>
            </Transition>
            <label class="studio-field"><span>Địa chỉ email</span><span class="studio-input"><Mail :size="18" /><input v-model="loginForm.email" autocomplete="email" type="email" required placeholder="ban@email.com" /></span></label>
            <label class="studio-field"><span>Mật khẩu</span><span class="studio-input"><LockKeyhole :size="18" /><input v-model="loginForm.password" :autocomplete="loginMode === 'register' ? 'new-password' : 'current-password'" required maxlength="72" minlength="8" :type="showPassword ? 'text' : 'password'" placeholder="Ít nhất 8 ký tự" /><button type="button" class="password-visibility" :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></span></label>
            <Transition name="auth-field">
              <label v-if="loginMode === 'register'" class="studio-field"><span>Nhập lại mật khẩu</span><span class="studio-input"><ShieldCheck :size="18" /><input v-model="confirmPassword" autocomplete="new-password" required maxlength="72" minlength="8" :type="showPassword ? 'text' : 'password'" placeholder="Nhập lại mật khẩu của bạn" /></span></label>
            </Transition>
            <p v-if="loginError" class="form-error studio-auth-error" role="alert">{{ loginError }}</p>
            <button class="button button-primary studio-submit" type="submit" :aria-busy="submitting"><span>{{ submitting ? 'Đang xử lý…' : loginMode === 'register' ? 'Tạo tài khoản của bạn' : 'Đăng nhập' }}</span><ArrowRight :size="19" /></button>
          </fieldset>
          <p class="auth-switch-prompt">{{ loginMode === 'register' ? 'Đã có tài khoản?' : 'Mới đến với ShopFlow?' }} <button type="button" :disabled="submitting" @click="changeMode(loginMode === 'register' ? 'login' : 'register')">{{ loginMode === 'register' ? 'Đăng nhập ngay' : 'Tham gia ngay' }} <ArrowRight :size="13" /></button></p>
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
        <button @click="selectDemoRole('SALES_STAFF')"><span class="role-badge customer-role">BH</span><span><strong>Nhân viên bán hàng</strong><small>Quản lý đơn và giao hàng</small></span><ArrowRight :size="16" /></button>
        <button @click="selectDemoRole('CUSTOMER')">
          <span class="role-badge customer-role">KH</span>
          <span><strong>Khách hàng</strong><small>Xem catalog và đặt hàng</small></span>
          <ArrowRight :size="16" />
        </button>
        <button class="guest-entry" @click="selectDemoRole(null)">
          Tiếp tục với tư cách khách <ArrowRight :size="15" />
        </button>
        </div>
        <div class="auth-divider"><span>hoặc cứ khám phá trước</span></div>
        <RouterLink to="/customer" class="auth-browse">Dạo một vòng cửa hàng <ArrowRight :size="16" /></RouterLink>
        <p class="auth-privacy"><ShieldCheck :size="14" /> Thông tin tài khoản được bảo vệ.</p>
      </div>
      <footer class="auth-entry-footer">© {{ new Date().getFullYear() }} ShopFlow <span>Made for music lovers.</span></footer>
    </section>
  </main>
</template>
