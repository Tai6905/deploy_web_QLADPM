import { computed, ref } from "vue";
import {
  shopflowApi,
  type SessionUser,
  type UserRole,
} from "../api/shopflowApi";

export const dataSource =
  import.meta.env.VITE_DATA_SOURCE === "demo" ? "demo" : "api";
const savedRole = localStorage.getItem("shopflow.demo-role");
const initialRole =
  savedRole === "GUEST" ? null : (savedRole as UserRole | null) || "SHOP_OWNER";

export const currentUser = ref<SessionUser | null>(
  dataSource === "demo" && initialRole ? demoUser(initialRole) : null,
);
export const sessionResolved = ref(dataSource === "demo");
export const currentRole = computed(() => currentUser.value?.role ?? null);
let restorePromise: Promise<void> | null = null;

window.addEventListener("shopflow:session-expired", () => {
  if (dataSource === "api") currentUser.value = null;
});

function demoUser(role: UserRole): SessionUser {
  const names: Record<UserRole, string> = {
    CUSTOMER: "Khách hàng",
    WAREHOUSE: "Nhân viên kho",
    SHOP_OWNER: "Chủ cửa hàng",
    SALES_STAFF: "Nhân viên bán hàng",
  };
  return {
    userId: 1,
    username: role.toLowerCase(),
    displayName: names[role],
    role,
  };
}

export function restoreSession() {
  if (dataSource === "demo" || sessionResolved.value) return Promise.resolve();
  if (!restorePromise) {
    restorePromise = shopflowApi
      .getSession()
      .then((session) => {
        currentUser.value = session.authenticated ? session.user : null;
      })
      .catch(() => {
        currentUser.value = null;
      })
      .finally(() => {
        sessionResolved.value = true;
        restorePromise = null;
      });
  }
  return restorePromise;
}

export function setDemoRole(role: UserRole | null) {
  if (dataSource !== "demo") return;
  currentUser.value = role ? demoUser(role) : null;
  localStorage.setItem("shopflow.demo-role", role ?? "GUEST");
}

export async function signIn(email: string, password: string) {
  currentUser.value = await shopflowApi.login({
    email: email.trim().toLowerCase(),
    password,
  });
  await shopflowApi.getSession();
  sessionResolved.value = true;
}

export async function signUp(input: {
  email: string;
  password: string;
  displayName: string;
}) {
  await shopflowApi.register(input);
  await signIn(input.email, input.password);
}

export async function signOut() {
  if (dataSource === "api") await shopflowApi.logout();
  currentUser.value = null;
  sessionResolved.value = true;
  if (dataSource === "demo")
    localStorage.setItem("shopflow.demo-role", "GUEST");
}
