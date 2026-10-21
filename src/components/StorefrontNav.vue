<script setup lang="ts">
import { computed } from "vue";
import { currentUser, signOut } from "../auth/session";
import { landingPage } from "../auth/permissions";
import { useRouter } from "vue-router";
const router = useRouter();
const error = defineModel<string>("error", { default: "" });
const operationLink = computed(() => landingPage(currentUser.value?.role || null));
async function logout() {
  try { await signOut(); await router.push('/customer'); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Không đăng xuất được.'; }
}
</script>
<template>
  <header class="commerce-header">
    <RouterLink to="/customer" class="commerce-brand"><span class="commerce-brand-mark">S</span><span>ShopFlow<small>Nhạc cụ cho từng nhịp đam mê.</small></span></RouterLink>
    <nav aria-label="Điều hướng cửa hàng">
      <RouterLink to="/customer">Cửa hàng</RouterLink><RouterLink to="/about">Về chúng tôi</RouterLink><RouterLink to="/guide">Cẩm nang</RouterLink>
      <RouterLink to="/compare">So sánh</RouterLink>
      <RouterLink v-if="currentUser" to="/purchases">Đơn mua</RouterLink><RouterLink v-if="currentUser" to="/profile">Tài khoản</RouterLink>
      <RouterLink v-if="currentUser && currentUser.role !== 'CUSTOMER'" :to="operationLink">Quản lý</RouterLink>
      <button v-if="currentUser" class="button button-outline" @click="logout">Đăng xuất</button><RouterLink v-else class="button button-primary" to="/login">Đăng nhập</RouterLink>
    </nav>
  </header>
</template>
