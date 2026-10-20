import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import { canAccessPage, landingPage } from "./auth/permissions";
import { currentRole, dataSource, restoreSession } from "./auth/session";
import "./style.css";
import "./motion.css";
import "./commerce.css";
import "./checkout.css";
import { installMotion } from "./motion";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: () => landingPage(currentRole.value) },
    { path: "/login", component: App },
    { path: "/forbidden", component: App },
    { path: "/customer", component: App },
    { path: "/about", component: App },
    { path: "/guide", component: App },
    { path: "/product/:id(\\d+)", component: App },
    { path: "/profile", component: App },
    { path: "/purchases", component: App },
    { path: "/checkout", component: App },
    {
      path: "/:page(overview|products|orders|inventory|delivery|receiving|returns|staff)",
      component: App,
    },
    { path: "/:pathMatch(.*)*", redirect: "/overview" },
  ],
});

router.beforeEach(async (to) => {
  if (dataSource === "api") await restoreSession();
  const page = to.path.slice(1);
  if (!page) return landingPage(currentRole.value);
  if (page === "login") {
    if (currentRole.value) return landingPage(currentRole.value);
    return true;
  }
  if (page === "forbidden" || page === "customer" || page === "about" || page === "guide" || /^product\/\d+$/.test(page)) return true;
  if (!currentRole.value)
    return { path: "/login", query: { redirect: to.fullPath } };
  return canAccessPage(page, currentRole.value) ? true : "/forbidden";
});

window.addEventListener("shopflow:session-expired", () => {
  const route = router.currentRoute.value;
  if (!["/", "/login", "/customer", "/forbidden"].includes(route.path)) {
    void router.replace({ path: "/login", query: { redirect: route.fullPath } });
  }
});

const app = createApp(App);
const disposeMotion = installMotion(app);
app.use(router).mount("#app");
if (import.meta.hot) import.meta.hot.dispose(disposeMotion);
