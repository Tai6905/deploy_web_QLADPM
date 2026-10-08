import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import { canAccessPage } from "./auth/permissions";
import { currentRole, dataSource, restoreSession } from "./auth/session";
import "./style.css";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/overview" },
    { path: "/login", component: App },
    { path: "/forbidden", component: App },
    { path: "/customer", component: App },
    {
      path: "/:page(overview|products|orders|inventory|delivery|receiving|returns)",
      component: App,
    },
    { path: "/:pathMatch(.*)*", redirect: "/overview" },
  ],
});

router.beforeEach(async (to) => {
  if (dataSource === "api") await restoreSession();
  const page = to.path.slice(1);
  if (page === "login") {
    if (currentRole.value === "WAREHOUSE") return "/inventory";
    if (currentRole.value === "CUSTOMER") return "/customer";
    if (currentRole.value === "SHOP_OWNER") return "/overview";
    return true;
  }
  if (page === "forbidden" || page === "customer") return true;
  if (!currentRole.value)
    return { path: "/login", query: { redirect: to.fullPath } };
  return canAccessPage(page, currentRole.value) ? true : "/forbidden";
});

createApp(App).use(router).mount("#app");
