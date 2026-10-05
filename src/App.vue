<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Activity,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Bell,
  Boxes,
  CalendarDays,
  Check,
  ChevronDown,
  CircleHelp,
  ClipboardList,
  Download,
  LayoutDashboard,
  Menu,
  PackageCheck,
  PackagePlus,
  Plus,
  RotateCcw,
  Search,
  Settings2,
  ShoppingBag,
  Sparkles,
  Truck,
  X,
  LogOut,
  Store,
  Minus,
  ShoppingCart,
} from "lucide-vue-next";
import {
  money,
  seedOrders,
  seedProducts,
  seedReturns,
  type Order,
  type Product,
  type ReturnRequest,
} from "./data";
import { paginate, shopflowApi, type UserRole } from "./api/shopflowApi";
import { canAccessPage, canPerform } from "./auth/permissions";
import {
  currentRole,
  currentUser,
  dataSource,
  setDemoRole,
  signIn,
  signOut,
} from "./auth/session";
import PaginationBar from "./components/PaginationBar.vue";

type ModalKind = "order" | "product" | "stock" | "return" | "checkout" | null;

const route = useRoute();
const router = useRouter();
const page = computed(() =>
  route.path === "/login" ||
  route.path === "/forbidden" ||
  route.path === "/customer"
    ? route.path.slice(1)
    : String(route.params.page || "overview"),
);
const search = ref("");
const mobileMenuOpen = ref(false);
const modalKind = ref<ModalKind>(null);
const toast = ref("");
const formError = ref("");
const selectedProductId = ref<number | null>(null);
const loginForm = reactive({ username: "", password: "" });
const loginError = ref("");
const cart = ref<Record<number, number>>({});
const products = ref<Product[]>(readStore("shopflow.products", seedProducts));
const orders = ref<Order[]>(readStore("shopflow.orders", seedOrders));
const returns = ref<ReturnRequest[]>(
  readStore("shopflow.returns", seedReturns),
);

const form = reactive({
  name: "",
  sku: "",
  category: "Đồ dùng",
  price: 0,
  quantity: 1,
  customer: "",
  supplier: "",
  orderId: "",
  reason: "",
  phone: "",
  email: "",
  addressLine: "",
  district: "",
  city: "",
});

function readStore<T>(key: string, fallback: T): T {
  try {
    const saved = localStorage.getItem(key);
    return saved ? (JSON.parse(saved) as T) : fallback;
  } catch {
    return fallback;
  }
}

watch(
  products,
  (value) => {
    if (dataSource === "demo")
      localStorage.setItem("shopflow.products", JSON.stringify(value));
  },
  { deep: true },
);
watch(
  orders,
  (value) => {
    if (dataSource === "demo")
      localStorage.setItem("shopflow.orders", JSON.stringify(value));
  },
  { deep: true },
);
watch(
  returns,
  (value) => {
    if (dataSource === "demo")
      localStorage.setItem("shopflow.returns", JSON.stringify(value));
  },
  { deep: true },
);

const allNavGroups = [
  {
    label: "TỔNG QUAN",
    items: [{ title: "Tổng quan", page: "overview", icon: LayoutDashboard }],
  },
  {
    label: "BÁN HÀNG",
    items: [
      { title: "Đơn hàng", page: "orders", icon: ShoppingBag },
      { title: "Sản phẩm", page: "products", icon: Boxes },
      { title: "Giao hàng", page: "delivery", icon: Truck },
      { title: "Đổi trả", page: "returns", icon: RotateCcw },
    ],
  },
  {
    label: "KHO VẬN",
    items: [
      { title: "Tồn kho", page: "inventory", icon: PackageCheck },
      { title: "Nhập hàng", page: "receiving", icon: PackagePlus },
    ],
  },
];
const navGroups = computed(() =>
  allNavGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        canAccessPage(item.page, currentRole.value),
      ),
    }))
    .filter((group) => group.items.length),
);

const pageNames: Record<string, string> = {
  overview: "Tổng quan",
  products: "Sản phẩm",
  orders: "Đơn hàng",
  inventory: "Tồn kho",
  delivery: "Giao hàng",
  receiving: "Nhập hàng",
  returns: "Đổi trả",
};
const title = computed(() => pageNames[page.value] || "Tổng quan");
const actionLabel = computed(() => {
  if (page.value === "products")
    return dataSource === "api" ? "Catalog chỉ đọc" : "Thêm sản phẩm";
  if (page.value === "inventory" || page.value === "receiving")
    return "Nhập hàng";
  if (page.value === "delivery") return "Quản lý đơn";
  if (page.value === "returns")
    return canPerform("approveReturn", currentRole.value)
      ? "Tạo yêu cầu"
      : "Đổi trả";
  return "Tạo đơn hàng";
});
const lowStockProducts = computed(() =>
  products.value.filter((product) => product.stock <= product.threshold),
);
const matchingProducts = computed(() =>
  products.value.filter((product) =>
    `${product.name} ${product.sku} ${product.category}`
      .toLowerCase()
      .includes(search.value.toLowerCase()),
  ),
);
const matchingOrders = computed(() =>
  orders.value.filter((order) =>
    `${order.id} ${order.customer} ${order.product}`
      .toLowerCase()
      .includes(search.value.toLowerCase()),
  ),
);
const matchingReturns = computed(() =>
  returns.value.filter((request) =>
    `${request.id} ${request.order} ${request.customer}`
      .toLowerCase()
      .includes(search.value.toLowerCase()),
  ),
);
const currentPage = computed(() => Math.max(1, Number(route.query.page) || 1));
const pageSize = computed(() =>
  Math.max(1, Math.min(100, Number(route.query.pageSize) || 5)),
);
const productPage = computed(() =>
  paginate(matchingProducts.value, {
    page: currentPage.value,
    size: pageSize.value,
    query: search.value,
  }),
);
const orderPage = computed(() =>
  paginate(matchingOrders.value, {
    page: currentPage.value,
    size: pageSize.value,
    query: search.value,
  }),
);
const returnPage = computed(() =>
  paginate(matchingReturns.value, {
    page: currentPage.value,
    size: pageSize.value,
    query: search.value,
  }),
);
const filteredProducts = computed(() => productPage.value.items);
const filteredOrders = computed(() => orderPage.value.items);
const filteredReturns = computed(() => returnPage.value.items);
const activePageResult = computed(() =>
  page.value === "products" ||
  page.value === "inventory" ||
  page.value === "receiving"
    ? productPage.value
    : page.value === "returns"
      ? returnPage.value
      : orderPage.value,
);
const cartItems = computed(() =>
  products.value
    .filter((product) => (cart.value[product.id] || 0) > 0)
    .map((product) => ({ product, quantity: cart.value[product.id] || 0 })),
);
const cartTotal = computed(() =>
  cartItems.value.reduce(
    (sum, item) => sum + item.product.price * item.quantity,
    0,
  ),
);
const pendingOrders = computed(
  () => orders.value.filter((order) => order.status === "Chờ xác nhận").length,
);
const salesTotal = computed(() =>
  orders.value
    .filter((order) => order.status !== "Đã hủy")
    .reduce((sum, order) => sum + order.total, 0),
);
const today = new Intl.DateTimeFormat("vi-VN", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
}).format(new Date());

watch(search, () => {
  if (currentPage.value !== 1) void updatePagination(1);
});
watch(page, () => {
  search.value = "";
});
watch(
  page,
  async (activePage) => {
    if (activePage !== "customer" || dataSource !== "api") return;
    try {
      const catalog = await shopflowApi.getProducts();
      products.value = catalog.map((item) => ({
        id: item.id,
        name: item.name,
        sku: `SF-${item.id}`,
        category: "Sản phẩm",
        price: item.price,
        stock: item.stockStatus === "IN_STOCK" ? 999 : 0,
        threshold: 0,
        color: "sage",
        image:
          "https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=120&q=80",
      }));
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : "Không thể tải danh mục sản phẩm.",
      );
    }
  },
  { immediate: true },
);

function updatePagination(nextPage: number, nextSize = pageSize.value) {
  return router.replace({
    query: {
      ...route.query,
      page: String(nextPage),
      pageSize: String(nextSize),
    },
  });
}

function changeDemoRole(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  selectDemoRole(value === "GUEST" ? null : (value as UserRole));
}

function selectDemoRole(role: UserRole | null) {
  setDemoRole(role);
  void router.push(
    role === "WAREHOUSE"
      ? "/inventory"
      : role === "CUSTOMER" || role === null
        ? "/customer"
        : "/overview",
  );
}

async function submitLogin() {
  loginError.value = "";
  try {
    await signIn(loginForm.username, loginForm.password);
    const requestedRedirect =
      typeof route.query.redirect === "string" ? route.query.redirect : "";
    const fallback =
      currentRole.value === "WAREHOUSE"
        ? "/inventory"
        : currentRole.value === "CUSTOMER"
          ? "/customer"
          : "/overview";
    const redirect =
      requestedRedirect.startsWith("/") && !requestedRedirect.startsWith("//")
        ? requestedRedirect
        : fallback;
    await router.replace(redirect);
  } catch (error) {
    loginError.value =
      error instanceof Error ? error.message : "Không thể đăng nhập.";
  }
}

async function logout() {
  await signOut();
  await router.replace("/login");
}

function addToCart(product: Product) {
  const inCart = cart.value[product.id] || 0;
  if (inCart >= product.stock) {
    notify("Số lượng trong giỏ đã đạt tồn kho hiện có");
    return;
  }
  cart.value[product.id] = inCart + 1;
  notify(`Đã thêm ${product.name} vào giỏ`);
}

function adjustCart(productId: number, delta: number) {
  const quantity = (cart.value[productId] || 0) + delta;
  if (quantity <= 0) delete cart.value[productId];
  else cart.value[productId] = quantity;
}

function setPageSize(size: number) {
  void updatePagination(1, size);
}

function notify(message: string) {
  toast.value = message;
  window.setTimeout(() => {
    toast.value = "";
  }, 2800);
}

function openModal(kind: Exclude<ModalKind, null>, productId?: number) {
  modalKind.value = kind;
  selectedProductId.value = productId ?? products.value[0]?.id ?? null;
  formError.value = "";
  Object.assign(form, {
    name: "",
    sku: "",
    category: "Đồ dùng",
    price: 0,
    quantity: 1,
    customer: "",
    supplier: "",
    orderId: orders.value[0]?.id ?? "",
    reason: "",
    phone: "",
    email: "",
    addressLine: "",
    district: "",
    city: "",
  });
  mobileMenuOpen.value = false;
}

function closeModal() {
  modalKind.value = null;
  formError.value = "";
}

async function submitModal() {
  formError.value = "";
  if (modalKind.value === "checkout") {
    if (
      !form.customer.trim() ||
      !form.phone.trim() ||
      !form.addressLine.trim() ||
      !form.city.trim() ||
      !cartItems.value.length
    ) {
      formError.value =
        "Vui lòng nhập thông tin nhận hàng và thêm sản phẩm vào giỏ.";
      return;
    }
    if (dataSource === "api") {
      try {
        const created = await shopflowApi.createOrder({
          customer: {
            fullName: form.customer.trim(),
            phone: form.phone.trim(),
            email: form.email.trim() || null,
          },
          shippingAddress: {
            receiverName: form.customer.trim(),
            phone: form.phone.trim(),
            addressLine: form.addressLine.trim(),
            district: form.district.trim() || null,
            city: form.city.trim(),
          },
          paymentMethod: "CARD",
          items: cartItems.value.map(({ product, quantity }) => ({
            productId: product.id,
            quantity,
          })),
        });
        cart.value = {};
        closeModal();
        notify(`Đơn #${created.id} đã tạo. Mã thanh toán: ${created.orderRef}`);
      } catch (error) {
        formError.value =
          error instanceof Error ? error.message : "Không thể tạo đơn hàng.";
      }
      return;
    }
    const totalQuantity = cartItems.value.reduce(
      (sum, item) => sum + item.quantity,
      0,
    );
    const productNames = cartItems.value
      .map(({ product }) => product.name)
      .join(", ");
    for (const { product, quantity } of cartItems.value)
      product.stock -= quantity;
    orders.value.unshift({
      id: `SF-${String(Date.now()).slice(-4)}`,
      customer: form.customer.trim(),
      date: "Vừa xong",
      total: cartTotal.value,
      status: "Chờ xác nhận",
      payment: "Chờ thanh toán",
      product: productNames,
      quantity: totalQuantity,
    });
    cart.value = {};
    closeModal();
    notify("Đã tạo đơn hàng demo");
    return;
  }
  if (modalKind.value === "order") {
    const product = products.value.find(
      (item) => item.id === selectedProductId.value,
    );
    if (!form.customer.trim()) {
      formError.value = "Nhập tên khách hàng để tiếp tục.";
      return;
    }
    if (!product || form.quantity < 1 || form.quantity > product.stock) {
      formError.value = "Số lượng không hợp lệ hoặc vượt quá tồn kho.";
      return;
    }
    product.stock -= form.quantity;
    orders.value.unshift({
      id: `SF-${String(Date.now()).slice(-4)}`,
      customer: form.customer.trim(),
      date: "Vừa xong",
      total: product.price * form.quantity,
      status: "Chờ xác nhận",
      payment: "Chờ thanh toán",
      product: product.name,
      quantity: form.quantity,
    });
    closeModal();
    notify("Đã tạo đơn hàng mới");
    return;
  }
  if (modalKind.value === "product") {
    if (!form.name.trim() || !form.sku.trim() || form.price <= 0) {
      formError.value = "Vui lòng điền tên, mã SKU và giá bán hợp lệ.";
      return;
    }
    if (
      products.value.some(
        (item) => item.sku.toLowerCase() === form.sku.trim().toLowerCase(),
      )
    ) {
      formError.value = "Mã SKU này đã được sử dụng.";
      return;
    }
    products.value.unshift({
      id: Date.now(),
      name: form.name.trim(),
      sku: form.sku.trim().toUpperCase(),
      category: form.category,
      price: form.price,
      stock: 0,
      threshold: 8,
      color: "sage",
      image:
        "https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=120&q=80",
    });
    closeModal();
    notify("Đã thêm sản phẩm vào danh mục");
    return;
  }
  if (modalKind.value === "return") {
    const order = orders.value.find((item) => item.id === form.orderId);
    if (!order || !form.reason.trim()) {
      formError.value = "Chọn đơn hàng và nhập lý do đổi trả.";
      return;
    }
    returns.value.unshift({
      id: `RT-${String(Date.now()).slice(-3)}`,
      order: order.id,
      customer: order.customer,
      reason: form.reason.trim(),
      status: "Chờ xử lý",
    });
    closeModal();
    notify("Đã gửi yêu cầu đổi trả");
    return;
  }
  const product = products.value.find(
    (item) => item.id === selectedProductId.value,
  );
  if (!product || form.quantity < 1) {
    formError.value = "Chọn sản phẩm và nhập số lượng hợp lệ.";
    return;
  }
  product.stock += form.quantity;
  closeModal();
  notify(
    `Đã nhập ${form.quantity} sản phẩm${form.supplier.trim() ? ` từ ${form.supplier.trim()}` : ""}`,
  );
}

function advanceOrder(order: Order) {
  const next: Record<Order["status"], Order["status"]> = {
    "Chờ xác nhận": "Đang xử lý",
    "Đang xử lý": "Đang giao",
    "Đang giao": "Hoàn tất",
    "Hoàn tất": "Hoàn tất",
    "Đã hủy": "Đã hủy",
  };
  order.status = next[order.status];
  if (order.status === "Hoàn tất") order.payment = "Đã thanh toán";
  notify(`Đơn ${order.id}: ${order.status.toLowerCase()}`);
}

function setReturnStatus(
  request: ReturnRequest,
  status: ReturnRequest["status"],
) {
  const action = status === "Đã nhập kho" ? "restockReturn" : "approveReturn";
  if (!canPerform(action, currentRole.value)) {
    notify("Vai trò hiện tại không có quyền thực hiện thao tác này");
    return;
  }
  request.status = status;
  notify(`Đã cập nhật yêu cầu ${request.id}`);
}

function exportOrders() {
  const rows = [
    ["Mã đơn", "Khách hàng", "Sản phẩm", "Số lượng", "Tổng tiền", "Trạng thái"],
    ...orders.value.map((order) => [
      order.id,
      order.customer,
      order.product,
      String(order.quantity),
      String(order.total),
      order.status,
    ]),
  ];
  const csv =
    "\ufeff" +
    rows
      .map((row) =>
        row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(","),
      )
      .join("\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8" }),
  );
  link.download = "shopflow-don-hang.csv";
  link.click();
  URL.revokeObjectURL(link.href);
  notify("Đã xuất danh sách đơn hàng");
}

function exportProducts() {
  const rows = [
    ["Tên sản phẩm", "Mã SKU", "Danh mục", "Giá bán", "Tồn kho"],
    ...products.value.map((product) => [
      product.name,
      product.sku,
      product.category,
      String(product.price),
      String(product.stock),
    ]),
  ];
  const csv =
    "\ufeff" +
    rows
      .map((row) =>
        row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(","),
      )
      .join("\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8" }),
  );
  link.download = "shopflow-san-pham.csv";
  link.click();
  URL.revokeObjectURL(link.href);
  notify("Đã xuất danh mục sản phẩm");
}

function primaryAction() {
  if (page.value === "delivery") {
    void router.push("/orders");
    return;
  }
  if (page.value === "returns") {
    if (!canPerform("approveReturn", currentRole.value)) {
      notify("Chỉ chủ shop được tạo yêu cầu đổi trả");
      return;
    }
    openModal("return");
    return;
  }
  if (page.value === "products" && dataSource === "api") {
    notify("Backend hiện chỉ hỗ trợ xem catalog; CRUD sản phẩm chưa có endpoint");
    return;
  }
  openModal(
    page.value === "products"
      ? "product"
      : page.value === "inventory" || page.value === "receiving"
        ? "stock"
        : "order",
  );
}

function submitSearch() {
  if (page.value === "overview" && search.value.trim())
    void router.push("/orders");
}

function statusClass(status: string) {
  if (
    status.includes("Hoàn tất") ||
    status.includes("thanh toán") ||
    status.includes("chấp nhận") ||
    status.includes("nhập kho")
  )
    return "success";
  if (status.includes("Chờ")) return "pending";
  if (status.includes("hủy") || status.includes("từ chối")) return "danger";
  return "info";
}
</script>

<template>
  <main v-if="page === 'login'" class="auth-page">
    <section class="auth-panel">
      <RouterLink to="/customer" class="store-brand"
        ><span class="brand-mark"><span></span><span></span><span></span></span
        ><strong>shopflow</strong></RouterLink
      ><span class="auth-kicker">WORKSPACE ACCESS</span>
      <h1>Đăng nhập</h1>
      <p>Đăng nhập để tiếp tục quản lý cửa hàng.</p>
      <form
        v-if="dataSource === 'api'"
        class="auth-form"
        @submit.prevent="submitLogin"
      >
        <label class="form-label"
          >Tên đăng nhập<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            placeholder="Tên đăng nhập" /></label
        ><label class="form-label"
          >Mật khẩu<input
            v-model="loginForm.password"
            autocomplete="current-password"
            required
            type="password"
            placeholder="Mật khẩu"
        /></label>
        <p v-if="loginError" class="form-error">{{ loginError }}</p>
        <button class="button button-primary auth-submit" type="submit">
          Đăng nhập <ArrowRight :size="16" />
        </button>
      </form>
      <div v-else class="demo-role-list">
        <p>Chọn vai trò demo để kiểm tra quyền truy cập.</p>
        <button @click="selectDemoRole('SHOP_OWNER')">
          <span class="role-badge owner-role">SO</span
          ><span
            ><strong>Chủ shop</strong><small>Toàn quyền vận hành</small></span
          ><ArrowRight :size="16" /></button
        ><button @click="selectDemoRole('WAREHOUSE')">
          <span class="role-badge warehouse-role">WH</span
          ><span
            ><strong>Nhân viên kho</strong
            ><small>Tồn kho, giao hàng, nhập hàng</small></span
          ><ArrowRight :size="16" /></button
        ><button @click="selectDemoRole('CUSTOMER')">
          <span class="role-badge customer-role">KH</span
          ><span
            ><strong>Khách hàng</strong
            ><small>Xem catalog và đặt hàng</small></span
          ><ArrowRight :size="16" /></button
        ><button class="guest-entry" @click="selectDemoRole(null)">
          Tiếp tục với tư cách khách <ArrowRight :size="15" />
        </button>
      </div>
      <RouterLink to="/customer" class="back-store-link"
        ><Store :size="15" /> Xem cửa hàng</RouterLink
      >
    </section>
  </main>
  <main v-else-if="page === 'forbidden'" class="auth-page">
    <section class="auth-panel forbidden-panel">
      <span class="auth-kicker">403 · KHÔNG ĐỦ QUYỀN</span>
      <h1>Trang này không dành cho vai trò hiện tại</h1>
      <p>
        Quyền được kiểm tra ở giao diện và cần được xác thực lại bởi backend khi
        kết nối.
      </p>
      <button
        class="button button-primary"
        @click="
          router.replace(
            currentRole === 'WAREHOUSE'
              ? '/inventory'
              : currentRole === 'CUSTOMER' || !currentRole
                ? '/customer'
                : '/overview',
          )
        "
      >
        Về trang phù hợp <ArrowRight :size="16" />
      </button>
    </section>
  </main>
  <main v-else-if="page === 'customer'" class="storefront-page">
    <header class="storefront-header">
      <RouterLink to="/overview" class="store-brand"
        ><span class="brand-mark"><span></span><span></span><span></span></span
        ><strong>Mộc Studio</strong></RouterLink
      ><label class="store-search"
        ><Search :size="17" /><input
          v-model="search"
          placeholder="Tìm sản phẩm"
          aria-label="Tìm sản phẩm"
      /></label>
      <div class="store-header-actions">
        <RouterLink
          v-if="currentUser && currentRole !== 'CUSTOMER'"
          :to="currentRole === 'WAREHOUSE' ? '/inventory' : '/overview'"
          class="button button-outline workspace-link"
          ><LayoutDashboard :size="16" />Vận hành</RouterLink
        ><RouterLink
          v-else-if="dataSource === 'demo'"
          to="/login"
          class="text-link"
          >{{ currentUser ? "Đổi vai trò" : "Đăng nhập" }}</RouterLink
        ><RouterLink v-else-if="!currentUser" to="/login" class="text-link"
          >Đăng nhập</RouterLink
        ><button
          class="button button-primary cart-button"
          @click="
            cartItems.length
              ? openModal('checkout')
              : notify('Giỏ hàng đang trống')
          "
        >
          <ShoppingCart :size="17" /><span>Giỏ hàng</span
          ><b>{{ cartItems.reduce((sum, item) => sum + item.quantity, 0) }}</b>
        </button>
      </div>
    </header>
    <section class="storefront-hero">
      <div>
        <span class="auth-kicker">MỘC STUDIO · ĐỒ DÙNG SỐNG XANH</span>
        <h1>Những món đồ nhỏ,<br />cho ngày thường tốt hơn.</h1>
        <p>Sản phẩm được chọn kỹ cho góc sống có chủ đích.</p>
      </div>
      <div class="hero-stamp">
        <span>CURATED<br />FOR EVERYDAY</span><Sparkles :size="25" />
      </div>
    </section>
    <div class="storefront-content">
      <section class="store-catalog">
        <div class="store-section-heading">
          <div>
            <span class="auth-kicker">BỘ SƯU TẬP</span>
            <h2>Sản phẩm nổi bật</h2>
          </div>
          <span>{{ products.length }} sản phẩm</span>
        </div>
        <div class="store-product-grid">
          <article
            v-for="product in products.filter((item) =>
              `${item.name} ${item.category}`
                .toLowerCase()
                .includes(search.toLowerCase()),
            )"
            :key="product.id"
            class="store-product"
          >
            <div class="store-product-image">
              <img :src="product.image" :alt="product.name" /><span
                v-if="product.stock <= product.threshold && product.stock > 0"
                class="stock-ribbon"
                >Sắp hết</span
              ><span v-if="product.stock === 0" class="stock-ribbon sold-out"
                >Hết hàng</span
              >
            </div>
            <div class="store-product-meta">
              <span>{{ product.category }}</span
              ><span>SKU {{ product.sku }}</span>
            </div>
            <h3>{{ product.name }}</h3>
            <div class="store-product-bottom">
              <strong>{{ money(product.price) }}</strong
              ><button
                class="icon-button store-add"
                :disabled="product.stock <= (cart[product.id] || 0)"
                :aria-label="`Thêm ${product.name} vào giỏ`"
                @click="addToCart(product)"
              >
                <Plus :size="18" />
              </button>
            </div>
          </article>
        </div>
        <p v-if="!products.length" class="empty-state">
          Chưa có sản phẩm đang bán.
        </p>
      </section>
      <aside class="store-cart-panel">
        <div class="store-cart-heading">
          <div>
            <span class="auth-kicker">GIỎ HÀNG</span>
            <h2>Đơn của bạn</h2>
          </div>
          <span class="cart-count">{{
            cartItems.reduce((sum, item) => sum + item.quantity, 0)
          }}</span>
        </div>
        <div v-if="cartItems.length" class="store-cart-items">
          <div
            v-for="item in cartItems"
            :key="item.product.id"
            class="store-cart-item"
          >
            <img :src="item.product.image" :alt="item.product.name" /><span
              class="store-cart-copy"
              ><strong>{{ item.product.name }}</strong
              ><small>{{ money(item.product.price) }}</small
              ><span class="quantity-control"
                ><button
                  :aria-label="`Giảm ${item.product.name}`"
                  @click="adjustCart(item.product.id, -1)"
                >
                  <Minus :size="13" /></button
                ><b>{{ item.quantity }}</b
                ><button
                  :aria-label="`Tăng ${item.product.name}`"
                  :disabled="item.quantity >= item.product.stock"
                  @click="addToCart(item.product)"
                >
                  <Plus :size="13" /></button></span></span
            ><strong class="cart-line-total">{{
              money(item.product.price * item.quantity)
            }}</strong>
          </div>
        </div>
        <div v-else class="cart-empty">
          <ShoppingBag :size="24" />
          <p>Chưa có sản phẩm nào.<br />Chọn món bạn yêu thích nhé.</p>
        </div>
        <div class="cart-total">
          <span>Tạm tính</span><strong>{{ money(cartTotal) }}</strong>
        </div>
        <button
          class="button button-primary checkout-button"
          :disabled="!cartItems.length"
          @click="openModal('checkout')"
        >
          Tiến hành đặt hàng <ArrowRight :size="16" />
        </button>
        <p class="store-checkout-note">
          Thanh toán mô phỏng bằng thẻ · Giá đã gồm VAT
        </p>
      </aside>
    </div>
    <footer class="storefront-footer">
      <span>© 2026 Mộc Studio · ShopFlow</span
      ><span>Đơn giản hơn, có chủ đích hơn.</span>
    </footer>
    <div
      v-if="modalKind === 'checkout'"
      class="modal-backdrop"
      @click.self="closeModal"
      @keydown.esc="closeModal"
    >
      <form class="modal" @submit.prevent="submitModal">
        <div class="modal-heading">
          <div>
            <span class="modal-kicker">MỘC STUDIO · THANH TOÁN</span>
            <h2>Thông tin nhận hàng</h2>
            <p>Đơn hàng sẽ được tạo với thanh toán mô phỏng bằng thẻ.</p>
          </div>
          <button type="button" class="icon-button" aria-label="Đóng" @click="closeModal"><X :size="19" /></button>
        </div>
        <label class="form-label">Họ và tên<input v-model="form.customer" autofocus autocomplete="name" placeholder="Tên người nhận" /></label>
        <div class="form-row">
          <label class="form-label">Số điện thoại<input v-model="form.phone" autocomplete="tel" placeholder="09xxxxxxxx" /></label>
          <label class="form-label">Email <span class="optional-label">Tùy chọn</span><input v-model="form.email" autocomplete="email" type="email" placeholder="ban@email.com" /></label>
        </div>
        <label class="form-label">Địa chỉ giao hàng<input v-model="form.addressLine" autocomplete="street-address" placeholder="Số nhà, tên đường" /></label>
        <div class="form-row">
          <label class="form-label">Quận / huyện <span class="optional-label">Tùy chọn</span><input v-model="form.district" placeholder="Quận, huyện" /></label>
          <label class="form-label">Tỉnh / thành phố<input v-model="form.city" autocomplete="address-level1" placeholder="Thành phố" /></label>
        </div>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="modal-actions">
          <button type="button" class="button button-outline" @click="closeModal">Hủy</button>
          <button type="submit" class="button button-primary"><Check :size="16" />Đặt hàng · {{ money(cartTotal) }}</button>
        </div>
      </form>
    </div>
    <Transition name="toast"><div v-if="toast" class="toast-message"><span><Check :size="16" /></span>{{ toast }}</div></Transition>
  </main>
  <div v-else class="app-shell">
    <div
      v-if="mobileMenuOpen"
      class="sidebar-scrim"
      @click="mobileMenuOpen = false"
    ></div>
    <aside class="sidebar" :class="{ 'sidebar--open': mobileMenuOpen }">
      <div class="brand-lockup">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <div><strong>shopflow</strong><small>STORE OPERATIONS</small></div>
        <button
          class="icon-button sidebar-close"
          aria-label="Đóng menu"
          @click="mobileMenuOpen = false"
        >
          <X :size="18" />
        </button>
      </div>

      <button class="store-switcher">
        <span class="store-avatar">M</span>
        <span class="store-copy"
          ><strong>Mộc Studio</strong><small>Cửa hàng chính</small></span
        >
        <ChevronDown :size="15" />
      </button>

      <nav class="side-nav" aria-label="Điều hướng chính">
        <section
          v-for="group in navGroups"
          :key="group.label"
          class="nav-group"
        >
          <p class="nav-label">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.page"
            :to="`/${item.page}`"
            class="nav-link"
            :class="{ 'nav-link--active': page === item.page }"
            @click="mobileMenuOpen = false"
          >
            <component :is="item.icon" :size="18" :stroke-width="1.8" />
            <span>{{ item.title }}</span>
            <span
              v-if="item.page === 'orders' && pendingOrders"
              class="nav-count"
              >{{ pendingOrders }}</span
            >
            <span
              v-if="item.page === 'inventory' && lowStockProducts.length"
              class="nav-dot"
            ></span>
          </RouterLink>
        </section>
      </nav>

      <div class="sidebar-bottom">
        <div class="plan-card">
          <div class="plan-icon"><Sparkles :size="16" /></div>
          <strong>Shopflow Pro</strong>
          <p>Quản lý cửa hàng dễ dàng hơn mỗi ngày.</p>
          <button @click="notify('Bạn đang sử dụng gói demo Shopflow Pro')">
            Tìm hiểu thêm <ArrowRight :size="14" />
          </button>
        </div>
        <button
          class="nav-link muted-link"
          @click="notify('Trung tâm trợ giúp sắp ra mắt')"
        >
          <CircleHelp :size="18" />Trợ giúp
        </button>
        <RouterLink to="/customer" class="storefront-shortcut"
          ><Store :size="15" /> Mở cửa hàng online <ArrowRight :size="14"
        /></RouterLink>
        <div class="profile-row">
          <span class="profile-avatar">{{
            currentUser?.displayName.slice(0, 1) || "G"
          }}</span
          ><span class="profile-copy"
            ><strong>{{ currentUser?.displayName || "Khách vãng lai" }}</strong
            ><small>{{ currentRole || "GUEST" }}</small></span
          ><Settings2 :size="17" />
        </div>
      </div>
    </aside>

    <div class="main-column">
      <header class="topbar">
        <button
          class="icon-button mobile-menu"
          aria-label="Mở menu"
          @click="mobileMenuOpen = true"
        >
          <Menu :size="20" />
        </button>
        <div class="breadcrumbs">
          <span>Mộc Studio</span><span class="crumb-divider">/</span
          ><strong>{{ title }}</strong>
        </div>
        <div class="topbar-tools">
          <label class="search-box"
            ><Search :size="17" /><input
              v-model="search"
              type="search"
              placeholder="Tìm đơn hàng, sản phẩm..."
              aria-label="Tìm kiếm"
              @keydown.enter="submitSearch"
          /></label>
          <button
            class="icon-button notification-button"
            aria-label="Xem cảnh báo tồn kho"
            @click="router.push('/inventory')"
          >
            <Bell :size="18" /><i v-if="lowStockProducts.length"></i>
          </button>
          <span class="topbar-divider"></span>
          <button
            class="help-button"
            @click="notify('Trung tâm trợ giúp sắp ra mắt')"
          >
            Trợ giúp
          </button>
          <select
            v-if="dataSource === 'demo'"
            class="role-select"
            :value="currentRole || 'GUEST'"
            aria-label="Vai trò demo"
            @change="changeDemoRole"
          >
            <option value="SHOP_OWNER">Chủ shop</option>
            <option value="WAREHOUSE">Kho</option>
            <option value="CUSTOMER">Khách hàng</option>
            <option value="GUEST">Khách vãng lai</option>
          </select>
          <button
            v-else-if="currentUser"
            class="icon-button"
            aria-label="Đăng xuất"
            title="Đăng xuất"
            @click="logout"
          >
            <LogOut :size="17" />
          </button>
          <button class="top-avatar" aria-label="Tài khoản">
            {{ currentUser?.displayName.slice(0, 1) || "G" }}
          </button>
        </div>
      </header>

      <main class="page-wrap">
        <div class="page-heading">
          <div>
            <div class="eyebrow">
              <span class="live-dot"></span> CỬA HÀNG ĐANG HOẠT ĐỘNG
            </div>
            <h1>{{ title }}</h1>
            <p v-if="page === 'overview'">
              Chào buổi sáng, Anh Tài. Đây là những gì đang diễn ra hôm nay.
            </p>
            <p v-else>
              {{
                page === "products"
                  ? "Theo dõi và cập nhật danh mục sản phẩm của bạn."
                  : page === "orders"
                    ? "Theo dõi đơn hàng và trạng thái thanh toán."
                    : page === "inventory"
                      ? "Kiểm soát số lượng tồn và cảnh báo sắp hết hàng."
                      : page === "delivery"
                        ? "Cập nhật tiến trình giao hàng cho khách."
                        : page === "receiving"
                          ? "Ghi nhận hàng hóa mới nhập vào kho."
                          : "Tiếp nhận và xử lý yêu cầu đổi trả."
              }}
            </p>
          </div>
          <div class="heading-actions">
            <span class="date-chip"><CalendarDays :size="16" />{{ today }}</span>
            <button
              class="button button-primary"
              :disabled="page === 'returns' && !canPerform('approveReturn', currentRole)"
              @click="primaryAction"
            >
              <Plus v-if="page !== 'delivery'" :size="17" />
              <ArrowRight v-else :size="16" />{{ actionLabel }}
            </button>
            <PaginationBar
              v-if="['products', 'inventory', 'receiving', 'orders', 'delivery', 'returns'].includes(page)"
              :page="activePageResult.page"
              :page-size="activePageResult.size"
              :total="activePageResult.total"
              :total-pages="activePageResult.totalPages"
              @change-page="updatePagination"
              @change-size="setPageSize"
            />
          </div>
        </div>

        <template v-if="page === 'overview'">
          <section class="stats-grid">
            <article class="stat-card">
              <div class="stat-top">
                <span>Doanh thu ghi nhận</span
                ><span class="stat-icon mint"><Activity :size="18" /></span>
              </div>
              <strong>{{ money(salesTotal) }}</strong>
              <div class="stat-foot">
                <span class="trend-up"><ArrowUpRight :size="14" />12,8%</span
                ><span>so với tháng trước</span
                ><svg
                  viewBox="0 0 94 28"
                  class="sparkline sparkline-green"
                  aria-hidden="true"
                >
                  <polyline
                    points="0,23 12,18 24,20 36,10 48,15 60,7 72,12 84,3 94,6"
                  />
                </svg>
              </div>
            </article>
            <article class="stat-card">
              <div class="stat-top">
                <span>Đơn hàng mới</span
                ><span class="stat-icon peach"><ShoppingBag :size="18" /></span>
              </div>
              <strong>{{ orders.length }} <small>đơn</small></strong>
              <div class="stat-foot">
                <span class="trend-up"><ArrowUpRight :size="14" />8,2%</span
                ><span>so với tháng trước</span
                ><svg
                  viewBox="0 0 94 28"
                  class="sparkline sparkline-orange"
                  aria-hidden="true"
                >
                  <polyline
                    points="0,20 12,15 24,18 36,8 48,12 60,5 72,10 84,2 94,7"
                  />
                </svg>
              </div>
            </article>
            <article class="stat-card">
              <div class="stat-top">
                <span>Giá trị đơn trung bình</span
                ><span class="stat-icon lavender"
                  ><ClipboardList :size="18"
                /></span>
              </div>
              <strong>{{
                money(
                  orders.length ? Math.round(salesTotal / orders.length) : 0,
                )
              }}</strong>
              <div class="stat-foot">
                <span class="trend-down"><ArrowDownRight :size="14" />2,4%</span
                ><span>so với tháng trước</span
                ><svg
                  viewBox="0 0 94 28"
                  class="sparkline sparkline-purple"
                  aria-hidden="true"
                >
                  <polyline
                    points="0,8 12,12 24,7 36,15 48,11 60,21 72,14 84,19 94,22"
                  />
                </svg>
              </div>
            </article>
            <article class="stat-card">
              <div class="stat-top">
                <span>Sản phẩm cần nhập</span
                ><span class="stat-icon yellow"><Boxes :size="18" /></span>
              </div>
              <strong
                >{{ lowStockProducts.length }} <small>mặt hàng</small></strong
              >
              <div class="stat-foot">
                <span class="foot-alert">Cần chú ý</span
                ><span>đang dưới ngưỡng an toàn</span
                ><svg
                  viewBox="0 0 94 28"
                  class="sparkline sparkline-orange"
                  aria-hidden="true"
                >
                  <polyline
                    points="0,21 12,16 24,20 36,12 48,15 60,9 72,13 84,4 94,8"
                  />
                </svg>
              </div>
            </article>
          </section>

          <section class="overview-grid">
            <article class="panel revenue-panel">
              <div class="panel-heading">
                <div>
                  <h2>Doanh thu</h2>
                  <p>Hiệu quả bán hàng theo thời gian</p>
                </div>
                <button
                  class="select-button"
                  @click="notify('Đang xem dữ liệu 7 ngày gần nhất')"
                >
                  7 ngày gần nhất <ChevronDown :size="15" />
                </button>
              </div>
              <div class="chart-summary">
                <strong>{{ money(salesTotal) }}</strong
                ><span class="trend-up"><ArrowUpRight :size="14" />12,8%</span
                ><small>so với kỳ trước</small>
              </div>
              <div class="chart-area">
                <div class="chart-y-axis">
                  <span>2tr</span><span>1,5tr</span><span>1tr</span
                  ><span>500k</span><span>0</span>
                </div>
                <div class="chart-main">
                  <div class="chart-grid">
                    <i></i><i></i><i></i><i></i><i></i>
                  </div>
                  <svg
                    class="revenue-chart"
                    viewBox="0 0 700 190"
                    preserveAspectRatio="none"
                    role="img"
                    aria-label="Biểu đồ doanh thu 7 ngày"
                  >
                    <defs>
                      <linearGradient
                        id="area-fill"
                        x1="0"
                        x2="0"
                        y1="0"
                        y2="1"
                      >
                        <stop
                          offset="0%"
                          stop-color="#1d6957"
                          stop-opacity=".18"
                        />
                        <stop
                          offset="100%"
                          stop-color="#1d6957"
                          stop-opacity="0"
                        />
                      </linearGradient>
                    </defs>
                    <path
                      d="M0 152 C34 145 44 120 90 127 S147 151 190 105 S252 115 290 92 S354 108 390 75 S448 82 490 62 S551 91 590 48 S648 57 700 20 V190 H0Z"
                      fill="url(#area-fill)"
                    />
                    <path
                      d="M0 152 C34 145 44 120 90 127 S147 151 190 105 S252 115 290 92 S354 108 390 75 S448 82 490 62 S551 91 590 48 S648 57 700 20"
                      fill="none"
                      stroke="#1d6957"
                      stroke-width="3"
                      vector-effect="non-scaling-stroke"
                    />
                    <circle
                      cx="590"
                      cy="48"
                      r="5"
                      fill="#fff"
                      stroke="#1d6957"
                      stroke-width="3"
                      vector-effect="non-scaling-stroke"
                    />
                  </svg>
                  <div class="chart-x-axis">
                    <span>Thứ 2</span><span>Thứ 3</span><span>Thứ 4</span
                    ><span>Thứ 5</span><span>Thứ 6</span><span>Thứ 7</span
                    ><span>CN</span>
                  </div>
                </div>
              </div>
            </article>
            <article class="panel channel-panel">
              <div class="panel-heading">
                <div>
                  <h2>Kênh bán hàng</h2>
                  <p>Phân bổ đơn hàng</p>
                </div>
                <button
                  class="icon-button"
                  aria-label="Tùy chọn kênh bán hàng"
                  @click="notify('Dữ liệu demo theo kênh bán')"
                >
                  <Settings2 :size="17" />
                </button>
              </div>
              <div class="channel-content">
                <div class="donut-wrap">
                  <div class="donut">
                    <div class="donut-center">
                      <strong>128</strong><small>đơn hàng</small>
                    </div>
                  </div>
                </div>
                <div class="channel-legend">
                  <div>
                    <i class="legend-dot legend-green"></i><span>Website</span
                    ><strong>62%</strong>
                  </div>
                  <div>
                    <i class="legend-dot legend-orange"></i><span>Sàn TMĐT</span
                    ><strong>25%</strong>
                  </div>
                  <div>
                    <i class="legend-dot legend-blue"></i
                    ><span>Tại cửa hàng</span><strong>13%</strong>
                  </div>
                </div>
              </div>
              <div class="channel-note">
                <Sparkles :size="16" /><span
                  >Website đang là kênh mang lại nhiều đơn hàng nhất.</span
                >
              </div>
            </article>
          </section>

          <section class="overview-grid lower-grid">
            <article class="panel orders-panel">
              <div class="panel-heading">
                <div>
                  <h2>Đơn hàng gần đây</h2>
                  <p>Cập nhật mới nhất từ cửa hàng</p>
                </div>
                <RouterLink to="/orders" class="text-link"
                  >Xem tất cả <ArrowRight :size="15"
                /></RouterLink>
              </div>
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>MÃ ĐƠN</th>
                      <th>KHÁCH HÀNG</th>
                      <th>NGÀY ĐẶT</th>
                      <th>TỔNG TIỀN</th>
                      <th>TRẠNG THÁI</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="order in orders.slice(0, 4)" :key="order.id">
                      <td class="order-id">{{ order.id }}</td>
                      <td>
                        <div class="customer-cell">
                          <span class="customer-avatar">{{
                            order.customer.slice(0, 1)
                          }}</span
                          >{{ order.customer }}
                        </div>
                      </td>
                      <td class="muted-cell">{{ order.date }}</td>
                      <td class="amount-cell">{{ money(order.total) }}</td>
                      <td>
                        <span
                          class="status-badge"
                          :class="`status-${statusClass(order.status)}`"
                          ><i></i>{{ order.status }}</span
                        >
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </article>
            <article class="panel stock-panel">
              <div class="panel-heading">
                <div>
                  <h2>Cần nhập thêm</h2>
                  <p>{{ lowStockProducts.length }} sản phẩm dưới ngưỡng</p>
                </div>
                <RouterLink to="/inventory" class="text-link"
                  >Tồn kho <ArrowRight :size="15"
                /></RouterLink>
              </div>
              <div v-if="lowStockProducts.length" class="stock-alert-list">
                <div
                  v-for="product in lowStockProducts.slice(0, 3)"
                  :key="product.id"
                  class="stock-alert-row"
                >
                  <img :src="product.image" :alt="product.name" /><span
                    class="stock-alert-copy"
                    ><strong>{{ product.name }}</strong
                    ><small>SKU: {{ product.sku }}</small></span
                  ><span class="stock-qty"
                    >{{ product.stock }} <small>còn lại</small></span
                  ><button
                    class="icon-button small-icon"
                    :aria-label="`Nhập kho ${product.name}`"
                    @click="openModal('stock', product.id)"
                  >
                    <Plus :size="16" />
                  </button>
                </div>
              </div>
              <div v-else class="empty-state">
                Tất cả sản phẩm đang đủ hàng.
              </div>
              <button class="stock-action" @click="router.push('/receiving')">
                Tạo phiếu nhập hàng <ArrowRight :size="15" />
              </button>
            </article>
          </section>
        </template>

        <template
          v-else-if="
            page === 'products' || page === 'inventory' || page === 'receiving'
          "
        >
          <section class="panel data-panel">
            <div class="table-toolbar">
              <div class="toolbar-tabs">
                <button
                  :class="{ selected: page === 'products' }"
                  @click="page !== 'products' && router.push('/products')"
                >
                  Tất cả sản phẩm <span>{{ products.length }}</span></button
                ><button
                  v-if="page !== 'products'"
                  :class="{ selected: page === 'inventory' }"
                  @click="router.push('/inventory')"
                >
                  Cảnh báo tồn kho
                  <span class="alert-count">{{ lowStockProducts.length }}</span>
                </button>
              </div>
              <div class="toolbar-actions">
                <label class="table-search"
                  ><Search :size="16" /><input
                    v-model="search"
                    type="search"
                    placeholder="Tìm sản phẩm" /></label
                ><button
                  class="button button-outline"
                  @click="
                    notify('Bộ lọc danh mục đang hiển thị tất cả sản phẩm')
                  "
                >
                  <Settings2 :size="16" />Bộ lọc</button
                ><button
                  class="button button-outline export-button"
                  @click="exportProducts"
                >
                  <Download :size="16" />Xuất file
                </button>
              </div>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>SẢN PHẨM</th>
                    <th>MÃ SKU</th>
                    <th>DANH MỤC</th>
                    <th>GIÁ BÁN</th>
                    <th>
                      {{ page === "products" ? "TỒN KHO" : "SỐ LƯỢNG CÒN" }}
                    </th>
                    <th>TRẠNG THÁI</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="product in filteredProducts" :key="product.id">
                    <td>
                      <div class="product-cell">
                        <img
                          :src="product.image"
                          :alt="product.name"
                        /><strong>{{ product.name }}</strong>
                      </div>
                    </td>
                    <td class="muted-cell">{{ product.sku }}</td>
                    <td>
                      <span class="category-label">{{ product.category }}</span>
                    </td>
                    <td class="amount-cell">{{ money(product.price) }}</td>
                    <td>
                      <span
                        class="stock-number"
                        :class="{
                          'stock-number--low':
                            product.stock <= product.threshold,
                        }"
                        >{{ product.stock }} <small>sp</small></span
                      >
                    </td>
                    <td>
                      <span
                        class="status-badge"
                        :class="
                          product.stock <= product.threshold
                            ? 'status-pending'
                            : 'status-success'
                        "
                        ><i></i
                        >{{
                          product.stock <= product.threshold
                            ? "Sắp hết hàng"
                            : "Đang bán"
                        }}</span
                      >
                    </td>
                    <td>
                      <button
                        v-if="page !== 'products'"
                        class="button button-small button-outline"
                        @click="openModal('stock', product.id)"
                      >
                        <Plus :size="14" />Nhập kho</button
                      ><button
                        v-else
                        class="row-menu"
                        :aria-label="`Nhập hàng cho ${product.name}`"
                        @click="openModal('stock', product.id)"
                      >
                        <PackagePlus :size="17" />
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!filteredProducts.length">
                    <td colspan="7" class="empty-cell">
                      Không tìm thấy sản phẩm phù hợp.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="table-footer">
              <span
                >Đang hiển thị {{ filteredProducts.length }} trên
                {{ productPage.total }} sản phẩm</span
              >
              <PaginationBar
                :page="productPage.page"
                :page-size="productPage.size"
                :total="productPage.total"
                :total-pages="productPage.totalPages"
                @change-page="updatePagination"
                @change-size="setPageSize"
              />
            </div>
          </section>
          <div class="page-footnote">
            <span class="live-dot"></span>Dữ liệu được lưu trên thiết bị này
            <span>·</span> Đồng bộ gần nhất: vừa xong
          </div>
        </template>

        <template v-else-if="page === 'orders' || page === 'delivery'">
          <section class="panel data-panel">
            <div class="table-toolbar">
              <div class="toolbar-tabs">
                <button
                  :class="{ selected: page === 'orders' }"
                  @click="page !== 'orders' && router.push('/orders')"
                >
                  Tất cả đơn hàng <span>{{ orders.length }}</span></button
                ><button
                  class="tab-button"
                  @click="notify(`${pendingOrders} đơn hàng đang chờ xác nhận`)"
                >
                  Chờ xử lý <span class="alert-count">{{ pendingOrders }}</span>
                </button>
              </div>
              <div class="toolbar-actions">
                <label class="table-search"
                  ><Search :size="16" /><input
                    v-model="search"
                    type="search"
                    placeholder="Tìm mã đơn, khách hàng" /></label
                ><button class="button button-outline" @click="exportOrders">
                  <Download :size="16" />Xuất file
                </button>
              </div>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>MÃ ĐƠN</th>
                    <th>KHÁCH HÀNG</th>
                    <th>SẢN PHẨM</th>
                    <th>NGÀY ĐẶT</th>
                    <th>TỔNG TIỀN</th>
                    <th>THANH TOÁN</th>
                    <th>TRẠNG THÁI</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="order in filteredOrders" :key="order.id">
                    <td class="order-id">{{ order.id }}</td>
                    <td>
                      <div class="customer-cell">
                        <span class="customer-avatar">{{
                          order.customer.slice(0, 1)
                        }}</span
                        >{{ order.customer }}
                      </div>
                    </td>
                    <td>
                      <span class="product-order-name"
                        >{{ order.product
                        }}<small>{{ order.quantity }} sản phẩm</small></span
                      >
                    </td>
                    <td class="muted-cell">{{ order.date }}</td>
                    <td class="amount-cell">{{ money(order.total) }}</td>
                    <td>
                      <span
                        class="payment-label"
                        :class="{
                          'payment-label--paid':
                            order.payment === 'Đã thanh toán',
                        }"
                        >{{ order.payment }}</span
                      >
                    </td>
                    <td>
                      <span
                        class="status-badge"
                        :class="`status-${statusClass(order.status)}`"
                        ><i></i>{{ order.status }}</span
                      >
                    </td>
                    <td>
                      <button
                        class="button button-small button-outline"
                        :disabled="
                          order.status === 'Hoàn tất' ||
                          order.status === 'Đã hủy'
                        "
                        @click="advanceOrder(order)"
                      >
                        {{ page === "delivery" ? "Cập nhật" : "Tiếp tục" }}
                        <ArrowRight :size="13" />
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!filteredOrders.length">
                    <td colspan="8" class="empty-cell">
                      Không tìm thấy đơn hàng.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="table-footer">
              <span>{{ orderPage.total }} đơn hàng</span>
              <PaginationBar
                :page="orderPage.page"
                :page-size="orderPage.size"
                :total="orderPage.total"
                :total-pages="orderPage.totalPages"
                @change-page="updatePagination"
                @change-size="setPageSize"
              />
            </div>
          </section>
          <div v-if="page === 'delivery'" class="delivery-hint">
            <Truck :size="18" /><span
              >Chọn <strong>Cập nhật</strong> để chuyển đơn sang bước giao hàng
              tiếp theo.</span
            >
          </div>
        </template>

        <template v-else>
          <section class="panel data-panel returns-panel">
            <div class="table-toolbar">
              <div class="toolbar-tabs">
                <button class="selected">
                  Yêu cầu đổi trả <span>{{ returns.length }}</span></button
                ><button
                  class="tab-button"
                  @click="
                    notify(
                      `${returns.filter((item) => item.status === 'Chờ xử lý').length} yêu cầu cần xử lý`,
                    )
                  "
                >
                  Chờ xử lý
                  <span class="alert-count">{{
                    returns.filter((item) => item.status === "Chờ xử lý").length
                  }}</span>
                </button>
              </div>
              <label class="table-search"
                ><Search :size="16" /><input
                  v-model="search"
                  type="search"
                  placeholder="Tìm yêu cầu"
              /></label>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>MÃ YÊU CẦU</th>
                    <th>MÃ ĐƠN</th>
                    <th>KHÁCH HÀNG</th>
                    <th>LÝ DO</th>
                    <th>TRẠNG THÁI</th>
                    <th>THAO TÁC</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="request in filteredReturns" :key="request.id">
                    <td class="order-id">{{ request.id }}</td>
                    <td class="muted-cell">{{ request.order }}</td>
                    <td>
                      <div class="customer-cell">
                        <span class="customer-avatar">{{
                          request.customer.slice(0, 1)
                        }}</span
                        >{{ request.customer }}
                      </div>
                    </td>
                    <td>{{ request.reason }}</td>
                    <td>
                      <span
                        class="status-badge"
                        :class="`status-${statusClass(request.status)}`"
                        ><i></i>{{ request.status }}</span
                      >
                    </td>
                    <td>
                      <div
                        v-if="request.status === 'Chờ xử lý' && canPerform('approveReturn', currentRole)"
                        class="return-actions"
                      >
                        <button
                          class="button button-small button-primary"
                          @click="setReturnStatus(request, 'Đã chấp nhận')"
                        >
                          <Check :size="14" />Chấp nhận</button
                        ><button
                          class="icon-button small-icon reject-button"
                          aria-label="Từ chối yêu cầu"
                          @click="setReturnStatus(request, 'Đã từ chối')"
                        >
                          <X :size="16" />
                        </button>
                      </div>
                      <button
                        v-else-if="request.status === 'Đã chấp nhận' && canPerform('restockReturn', currentRole)"
                        class="button button-small button-outline"
                        @click="setReturnStatus(request, 'Đã nhập kho')"
                      >
                        <PackageCheck :size="14" />Nhập lại kho
                      </button>
                      <span v-else-if="request.status === 'Chờ xử lý'" class="muted-cell">Chờ chủ shop duyệt</span>
                      <span v-else class="muted-cell">{{ request.status === 'Đã nhập kho' ? 'Đã nhập kho' : 'Đã xử lý' }}</span>
                    </td>
                  </tr>
                  <tr v-if="!filteredReturns.length">
                    <td colspan="6" class="empty-cell">
                      Chưa có yêu cầu đổi trả phù hợp.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="table-footer">
              <span>{{ returnPage.total }} yêu cầu đổi trả</span>
              <PaginationBar
                :page="returnPage.page"
                :page-size="returnPage.size"
                :total="returnPage.total"
                :total-pages="returnPage.totalPages"
                @change-page="updatePagination"
                @change-size="setPageSize"
              />
            </div>
          </section>
          <div class="return-policy">
            <RotateCcw :size="19" />
            <div>
              <strong>Quy trình đổi trả</strong>
              <p>
                Kiểm tra tình trạng sản phẩm trước khi chấp nhận hoàn hàng. Hàng
                hoàn kho sẽ cần được cập nhật tồn kho riêng.
              </p>
            </div>
            <button
              class="text-link"
              @click="notify('Chính sách đổi trả đang được cập nhật')"
            >
              Xem chính sách <ArrowRight :size="15" />
            </button>
          </div>
        </template>

        <footer class="app-footer">
          <span>© 2026 ShopFlow</span
          ><span>Vận hành cửa hàng, nhẹ nhàng hơn.</span
          ><span class="footer-status"
            ><i></i> Hệ thống hoạt động bình thường</span
          >
        </footer>
      </main>
    </div>

    <div
      v-if="modalKind"
      class="modal-backdrop"
      @click.self="closeModal"
      @keydown.esc="closeModal"
    >
      <form class="modal" @submit.prevent="submitModal">
        <div class="modal-heading">
          <div>
            <span class="modal-kicker">MỘC STUDIO · KHO VẬN</span>
            <h2>
              {{
                modalKind === "order"
                  ? "Tạo đơn hàng mới"
                  : modalKind === "product"
                    ? "Thêm sản phẩm"
                    : modalKind === "return"
                      ? "Tạo yêu cầu đổi trả"
                      : modalKind === "checkout"
                        ? "Thông tin nhận hàng"
                        : "Nhập hàng vào kho"
              }}
            </h2>
            <p>
              {{
                modalKind === "order"
                  ? "Tạo đơn và cập nhật tồn kho tự động."
                  : modalKind === "product"
                    ? "Thêm mặt hàng mới vào danh mục cửa hàng."
                    : modalKind === "checkout"
                      ? "Đơn hàng sẽ được tạo với thanh toán mô phỏng bằng thẻ."
                      : modalKind === "return"
                        ? "Chọn đơn hàng và mô tả lý do cần xử lý."
                        : "Ghi nhận số lượng hàng mới nhận được."
              }}
            </p>
          </div>
          <button
            type="button"
            class="icon-button"
            aria-label="Đóng"
            @click="closeModal"
          >
            <X :size="19" />
          </button>
        </div>
        <template v-if="modalKind === 'checkout'">
          <label class="form-label">Họ và tên<input v-model="form.customer" autofocus autocomplete="name" placeholder="Tên người nhận" /></label>
          <div class="form-row">
            <label class="form-label">Số điện thoại<input v-model="form.phone" autocomplete="tel" placeholder="09xxxxxxxx" /></label>
            <label class="form-label">Email <span class="optional-label">Tùy chọn</span><input v-model="form.email" autocomplete="email" type="email" placeholder="ban@email.com" /></label>
          </div>
          <label class="form-label">Địa chỉ giao hàng<input v-model="form.addressLine" autocomplete="street-address" placeholder="Số nhà, tên đường" /></label>
          <div class="form-row">
            <label class="form-label">Quận / huyện <span class="optional-label">Tùy chọn</span><input v-model="form.district" placeholder="Quận, huyện" /></label>
            <label class="form-label">Tỉnh / thành phố<input v-model="form.city" autocomplete="address-level1" placeholder="Thành phố" /></label>
          </div>
        </template>
        <template v-else-if="modalKind === 'order'"
          ><label class="form-label"
            >Tên khách hàng<input
              v-model="form.customer"
              autofocus
              placeholder="Ví dụ: Nguyễn Minh Anh" /></label
          ><label class="form-label"
            >Sản phẩm<select v-model.number="selectedProductId">
              <option
                v-for="product in products"
                :key="product.id"
                :value="product.id"
              >
                {{ product.name }} · {{ money(product.price) }} · còn
                {{ product.stock }}
              </option>
            </select></label
          ><label class="form-label"
            >Số lượng<input
              v-model.number="form.quantity"
              type="number"
              min="1" /></label
        ></template>
        <template v-else-if="modalKind === 'product'"
          ><label class="form-label"
            >Tên sản phẩm<input
              v-model="form.name"
              autofocus
              placeholder="Tên hiển thị của sản phẩm"
          /></label>
          <div class="form-row">
            <label class="form-label"
              >Mã SKU<input
                v-model="form.sku"
                placeholder="VD: MOC-001" /></label
            ><label class="form-label"
              >Danh mục<select v-model="form.category">
                <option>Đồ dùng</option>
                <option>Phụ kiện</option>
                <option>Văn phòng phẩm</option>
                <option>Trang trí</option>
              </select></label
            >
          </div>
          <label class="form-label"
            >Giá bán (VND)<input
              v-model.number="form.price"
              type="number"
              min="1"
              placeholder="0" /></label
        ></template>
        <template v-else-if="modalKind === 'return'"
          ><label class="form-label"
            >Đơn hàng<select v-model="form.orderId">
              <option v-for="order in orders" :key="order.id" :value="order.id">
                {{ order.id }} · {{ order.customer }}
              </option>
            </select></label
          ><label class="form-label"
            >Lý do đổi trả<textarea
              v-model="form.reason"
              rows="3"
              placeholder="Mô tả vấn đề hoặc sản phẩm cần đổi"
            ></textarea></label
        ></template>
        <template v-else
          ><label class="form-label"
            >Sản phẩm<select v-model.number="selectedProductId">
              <option
                v-for="product in products"
                :key="product.id"
                :value="product.id"
              >
                {{ product.name }} · hiện có {{ product.stock }}
              </option>
            </select></label
          ><label class="form-label"
            >Số lượng nhập<input
              v-model.number="form.quantity"
              autofocus
              type="number"
              min="1"
              placeholder="Nhập số lượng" /></label
          ><label class="form-label"
            >Nhà cung cấp <span class="optional-label">Không bắt buộc</span
            ><input
              v-model="form.supplier"
              placeholder="Tên nhà cung cấp" /></label
        ></template>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="modal-actions">
          <button
            type="button"
            class="button button-outline"
            @click="closeModal"
          >
            Hủy</button
          ><button type="submit" class="button button-primary">
            <Check :size="16" />{{
              modalKind === "order"
                ? "Tạo đơn hàng"
                : modalKind === "product"
                  ? "Lưu sản phẩm"
                  : modalKind === "return"
                    ? "Gửi yêu cầu"
                    : modalKind === "checkout"
                      ? "Đặt hàng"
                    : "Xác nhận nhập"
            }}
          </button>
        </div>
      </form>
    </div>
    <Transition name="toast"
      ><div v-if="toast" class="toast-message">
        <span><Check :size="16" /></span>{{ toast }}
      </div></Transition
    >
  </div>
</template>
