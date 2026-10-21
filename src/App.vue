<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from "vue";
import { useCart } from "./cart";
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
  retiredCatalogSkus,
  seedOrders,
  seedProducts,
  seedReturns,
  type Order,
  type Product,
  type ReturnRequest,
} from "./data";
import {
  paginate,
  shopflowApi,
  roleLabels,
  type ApiPage,
  type Category,
  type DashboardSummary,
  type DeliveryOrder,
  type InventoryItem,
  type OrderSummary,
  type ProductListItem,
  type ReturnableOrder,
  type UserRole,
} from "./api/shopflowApi";
import { canAccessPage, canPerform, landingPage } from "./auth/permissions";
import {
  currentRole,
  currentUser,
  dataSource,
  setDemoRole,
  signOut,
} from "./auth/session";
import PaginationBar from "./components/PaginationBar.vue";
import CategoryManager from "./components/CategoryManager.vue";
import ProductDetailDialog from "./components/ProductDetailDialog.vue";
import LoginPage from "./views/LoginPage.vue";
import OrdersDeliveryPage from "./views/OrdersDeliveryPage.vue";
import ProductsInventoryPage from "./views/ProductsInventoryPage.vue";
import ReturnsPage from "./views/ReturnsPage.vue";
import StaffPage from "./views/StaffPage.vue";
import AccountPage from "./views/AccountPage.vue";
import AnimatedNumber from "./components/AnimatedNumber.vue";
import ProductPage from "./views/ProductPage.vue";
import StoreInfoPage from "./views/StoreInfoPage.vue";
import ProductImage from "./components/ProductImage.vue";
import CheckoutPage from "./views/CheckoutPage.vue";
import {
  toDeliveryOrder,
  toInventoryProduct,
  toOrderView,
  toReceivingProduct,
  toReturnView,
} from "./api/viewModels";

type ModalKind = "order" | "product" | "stock" | "return" | null;

const route = useRoute();
const router = useRouter();
const page = computed(() =>
  route.path.startsWith("/product/") ? "product" :
  route.path === "/about" || route.path === "/guide" ? route.path.slice(1) :
  route.path === "/login" ||
  route.path === "/forbidden" ||
  route.path === "/profile" ||
  route.path === "/purchases" ||
  route.path === "/checkout" ||
  route.path === "/customer"
    ? route.path.slice(1)
    : String(route.params.page || "overview"),
);
const search = ref("");
const mobileMenuOpen = ref(false);
const modalKind = ref<ModalKind>(null);
const toast = ref("");
let toastTimer: ReturnType<typeof setTimeout> | undefined;
const formError = ref("");
const selectedProductId = ref<number | null>(null);
const apiProductPage = ref<ApiPage<ProductListItem> | null>(null);
const apiOrderPage = ref<ApiPage<OrderSummary> | null>(null);
const apiDashboard = ref<DashboardSummary | null>(null);
const apiInventory = ref<InventoryItem[]>([]);
const apiDeliveries = ref<DeliveryOrder[]>([]);
const apiOrderProducts = ref<Product[]>([]);
const apiReturnableOrders = ref<ReturnableOrder[]>([]);
const apiCategories = ref<Category[]>([]);
const apiCategoriesLoaded = ref(false);
const categoryFilter = ref("all");
const sortFilter = ref<"newest" | "priceAsc" | "priceDesc" | "rating">("newest");
const minPriceFilter = ref<number | null>(null), maxPriceFilter = ref<number | null>(null);
const ratingFilter = ref(0), stockFilter = ref(false);
const filterError = computed(() => {
  if ([minPriceFilter.value, maxPriceFilter.value].some(value => value != null && (!Number.isSafeInteger(value) || value < 0))) return 'Giá lọc phải là số nguyên VND không âm.';
  return minPriceFilter.value != null && maxPriceFilter.value != null && minPriceFilter.value > maxPriceFilter.value ? 'Giá thấp nhất phải nhỏ hơn hoặc bằng giá cao nhất.' : '';
});
function resetStoreFilters() { minPriceFilter.value = null; maxPriceFilter.value = null; ratingFilter.value = 0; stockFilter.value = false; categoryFilter.value = 'all'; sortFilter.value = 'newest'; search.value = ''; }
const apiProductLoading = ref(false);
const apiProductSaving = ref(false);
const apiOrderSaving = ref(false);
const apiReturnSaving = ref(false);
const apiProductError = ref("");
const apiWorkspaceLoading = ref(false);
const apiWorkspaceError = ref("");
const apiStockSaving = ref(false);
const categoryManagerOpen = ref(false);
const categorySaving = ref(false);
const categoryError = ref("");
const productDetailId = ref<number | null>(null);
const productDetail = computed(
  () => products.value.find((product) => product.id === productDetailId.value) ?? null,
);
const { cart, cartItems, cartTotal, add: addCartItem, adjust: adjustCart, refresh: refreshCart, clear: clearCart, removeSkus: removeCartSkus } =
  useCart(localStorage, dataSource === "demo" ? "moc.music.demo.cart.v1" : "shopflow.api.cart.v1");
removeCartSkus(retiredCatalogSkus);
const checkoutDestination = computed(() =>
  dataSource === 'api' && !currentUser.value
    ? { path: '/login', query: { redirect: '/checkout' } }
    : { path: '/checkout' },
);
const products = ref<Product[]>(
  dataSource === "demo" ? readStore("moc.music.products.v1", seedProducts) : [],
);
const orders = ref<Order[]>(
  dataSource === "demo" ? readStore("moc.music.orders.v1", seedOrders) : [],
);
const returns = ref<ReturnRequest[]>(
  dataSource === "demo" ? readStore("moc.music.returns.v1", seedReturns) : [],
);

function toCatalogProduct(item: ProductListItem): Product {
  return {
    id: item.id,
    categoryId: item.categoryId,
    name: item.name,
    sku: item.sku,
    category: item.categoryName,
    price: item.listPrice,
    stock: item.availableQuantity,
    threshold: 0,
    color: "sage",
    image: item.imageUrl || "",
    averageRating: item.averageRating,
    reviewCount: item.reviewCount,
  };
}

const form = reactive({
  name: "",
  sku: "",
  category: "Guitar acoustic",
  categoryId: 0,
  price: 0,
  unitCost: 0,
  description: "",
  usageInstructions: "",
  imageUrls: "",
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
      localStorage.setItem("moc.music.products.v1", JSON.stringify(value));
  },
  { deep: true },
);
watch(
  orders,
  (value) => {
    if (dataSource === "demo")
      localStorage.setItem("moc.music.orders.v1", JSON.stringify(value));
  },
  { deep: true },
);
watch(
  returns,
  (value) => {
    if (dataSource === "demo")
      localStorage.setItem("moc.music.returns.v1", JSON.stringify(value));
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
    label: "QUẢN TRỊ",
    items: [{ title: "Tài khoản nhân viên", page: "staff", icon: Settings2 }],
  },
  {
    label: "KHO VẬN",
    items: [
      { title: "Tồn kho", page: "inventory", icon: PackageCheck },
      { title: "Nhập hàng", page: "receiving", icon: PackagePlus },
    ],
  },
  {
    label: "TÀI KHOẢN",
    items: [
      { title: "Thông tin cá nhân", page: "profile", icon: Settings2 },
      { title: "Đơn mua của tôi", page: "purchases", icon: ShoppingBag },
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
  staff: "Nhân viên",
};
const title = computed(() => pageNames[page.value] || "Tổng quan");
const actionLabel = computed(() => {
  if (page.value === "products") return "Thêm sản phẩm";
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
      .includes(search.value.toLowerCase()) &&
      (categoryFilter.value === "all" ||
        (dataSource === "demo"
          ? product.category === categoryOptions.value.find((item) => item.id === Number(categoryFilter.value))?.name
          : product.categoryId === Number(categoryFilter.value))),
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
const currentPage = computed(() => Math.max(1, Math.floor(Number(route.query.page)) || 1));
const pageSize = computed(() =>
  Math.max(1, Math.min(100, Math.floor(Number(route.query.pageSize)) || (page.value === "customer" ? 12 : 5))),
);
const productPage = computed(() => {
  if (
    dataSource === "api" &&
    apiProductPage.value &&
    (page.value === "customer" || page.value === "products")
  ) {
    return {
      items: products.value,
      total: apiProductPage.value.totalItems,
      page: apiProductPage.value.page + 1,
      size: apiProductPage.value.size,
      totalPages: Math.max(1, apiProductPage.value.totalPages),
    };
  }
  return paginate(matchingProducts.value, {
    page: currentPage.value,
    size: pageSize.value,
    query: search.value,
  });
});
const orderPage = computed(() =>
  dataSource === "api" && apiOrderPage.value && page.value === "orders"
    ? {
        items: orders.value,
        total: apiOrderPage.value.totalItems,
        page: apiOrderPage.value.page + 1,
        size: apiOrderPage.value.size,
        totalPages: Math.max(apiOrderPage.value.totalPages, 1),
      }
    : paginate(matchingOrders.value, {
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
const pendingOrders = computed(
  () => orders.value.filter((order) => order.status === "Chờ xác nhận").length,
);
const salesTotal = computed(() =>
  apiDashboard.value?.revenue ?? orders.value
    .filter((order) => order.status !== "Đã hủy")
    .reduce((sum, order) => sum + order.total, 0),
);
const dashboardOrderCount = computed(() => apiDashboard.value?.orderCount ?? orders.value.length);
const averagePaidOrder = computed(() => {
  const count = apiDashboard.value?.paidOrderCount ?? orders.value.filter((item) => item.payment === "Đã thanh toán").length;
  return count ? Math.round(salesTotal.value / count) : 0;
});
const revenueChart = computed(() => {
  const days = apiDashboard.value?.dailyRevenue ?? [];
  const max = Math.max(1, ...days.map((day) => day.revenue));
  return {
    points: days.map((day, index) => `${index * 700 / Math.max(days.length - 1, 1)},${180 - day.revenue / max * 160}`).join(" "),
    labels: days.map((day) => day.date.slice(5).split("-").reverse().join("/")),
    max,
  };
});
const today = new Intl.DateTimeFormat("vi-VN", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
}).format(new Date());
let workspaceLoadId = 0;
let catalogLoadId = 0;
const debouncedSearch = ref("");
const workspaceSearch = computed(() => page.value === "orders" ? debouncedSearch.value : "");
const workspacePage = computed(() => page.value === "orders" ? currentPage.value : 1);
const workspaceSize = computed(() => page.value === "orders" ? pageSize.value : 5);
let searchTimer: ReturnType<typeof setTimeout> | undefined;
const categoryOptions = computed(() =>
  dataSource === "api"
    ? apiCategories.value
    : [...new Set(products.value.map((product) => product.category))].map(
        (name, index) => ({ id: index + 1, name }),
      ),
);

watch(search, (value) => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    debouncedSearch.value = value;
    if (currentPage.value !== 1) void updatePagination(1);
  }, 250);
});
watch([categoryFilter, sortFilter, minPriceFilter, maxPriceFilter, ratingFilter, stockFilter], () => {
  if (currentPage.value !== 1) void updatePagination(1);
});
watch(products, (items) => {
  if (dataSource === "demo" || page.value === "customer" || page.value === "products") refreshCart(items);
});
onUnmounted(() => { clearTimeout(searchTimer); clearTimeout(toastTimer); ++catalogLoadId; ++workspaceLoadId; });
watch(page, () => {
  clearTimeout(searchTimer);
  search.value = "";
  debouncedSearch.value = "";
  categoryFilter.value = "all";
});
watch(
  [page, currentPage, pageSize, debouncedSearch, categoryFilter, sortFilter, minPriceFilter, maxPriceFilter, ratingFilter, stockFilter],
  async ([activePage]) => {
    const loadId = ++catalogLoadId;
    if (
      dataSource !== "api" ||
      (activePage !== "customer" && activePage !== "products")
    )
      return;
    apiProductLoading.value = true;
    apiProductError.value = "";
    if (filterError.value) { apiProductError.value = filterError.value; apiProductLoading.value = false; return; }
    products.value = [];
    apiProductPage.value = null;
    try {
      const [catalog, categoryPage] = await Promise.all([
        shopflowApi.getProducts({
          page: currentPage.value - 1,
          size: pageSize.value,
          query: debouncedSearch.value || undefined,
          categoryId:
            categoryFilter.value === "all"
              ? undefined
              : Number(categoryFilter.value),
          sort: sortFilter.value,
          minPrice: minPriceFilter.value ?? undefined,
          maxPrice: maxPriceFilter.value ?? undefined,
          rating: ratingFilter.value || undefined,
          inStock: stockFilter.value || undefined,
        }),
        apiCategoriesLoaded.value
          ? Promise.resolve(null)
          : shopflowApi.getCategories({ page: 0, size: 100 }),
      ]);
      if (loadId !== catalogLoadId) return;
      if (catalog.totalPages > 0 && currentPage.value > catalog.totalPages) {
        void updatePagination(catalog.totalPages);
        return;
      }
      apiProductPage.value = catalog;
      if (categoryPage) {
        apiCategories.value = categoryPage.items;
        apiCategoriesLoaded.value = true;
      }
      products.value = catalog.items.map(toCatalogProduct);
    } catch (error) {
      if (loadId !== catalogLoadId) return;
      apiProductError.value =
        error instanceof Error ? error.message : "Không thể tải danh mục sản phẩm.";
    } finally {
      if (loadId === catalogLoadId) apiProductLoading.value = false;
    }
  },
  { immediate: true },
);
watch(
  [page, workspacePage, workspaceSize, workspaceSearch],
  async ([activePage]) => {
    const loadId = ++workspaceLoadId;
    if (dataSource !== "api" || ["customer", "products", "login", "forbidden", "staff", "profile", "purchases", "product", "about", "guide", "checkout"].includes(activePage)) {
      apiWorkspaceLoading.value = false;
      return;
    }
    apiWorkspaceLoading.value = true;
    apiWorkspaceError.value = "";
    try {
      if (activePage === "overview") {
        const [summary, orderResult, inventory] = await Promise.all([
          shopflowApi.getDashboard("7d"),
          shopflowApi.getOrders({ page: 0, size: 5 }),
          shopflowApi.getInventory(),
        ]);
        if (loadId !== workspaceLoadId) return;
        apiDashboard.value = summary;
        apiOrderPage.value = orderResult;
        orders.value = orderResult.items.map(toOrderView);
        apiInventory.value = inventory;
        products.value = inventory.map(toInventoryProduct);
      } else if (activePage === "orders") {
        const result = await shopflowApi.getOrders({ page: currentPage.value - 1, size: pageSize.value, query: debouncedSearch.value || undefined });
        if (loadId !== workspaceLoadId) return;
        apiOrderPage.value = result;
        orders.value = result.items.map(toOrderView);
      } else if (activePage === "inventory") {
        const inventory = await shopflowApi.getInventory();
        if (loadId !== workspaceLoadId) return;
        apiInventory.value = inventory;
        products.value = inventory.map(toInventoryProduct);
      } else if (activePage === "receiving") {
        const [receipts, inventory] = await Promise.all([
          shopflowApi.listReceivings(),
          shopflowApi.getInventory(),
        ]);
        if (loadId !== workspaceLoadId) return;
        apiInventory.value = inventory;
        products.value = receipts.map(toReceivingProduct);
      } else if (activePage === "delivery") {
        const deliveries = await shopflowApi.getDeliveries();
        if (loadId !== workspaceLoadId) return;
        apiDeliveries.value = deliveries;
        orders.value = deliveries.map(toDeliveryOrder);
      } else if (activePage === "returns") {
        const [apiReturns, returnableOrders] = await Promise.all([
          shopflowApi.getReturns(),
          currentRole.value === "SHOP_OWNER" ? shopflowApi.getReturnableOrders() : Promise.resolve([]),
        ]);
        if (loadId !== workspaceLoadId) return;
        returns.value = apiReturns.map(toReturnView);
        apiReturnableOrders.value = returnableOrders;
      }
    } catch (error) {
      if (loadId === workspaceLoadId) {
        apiWorkspaceError.value = error instanceof Error ? error.message : "Không tải được dữ liệu nghiệp vụ.";
      }
    } finally {
      if (loadId === workspaceLoadId) apiWorkspaceLoading.value = false;
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
  void router.push(landingPage(role));
}

async function logout() {
  try {
    await signOut();
    await router.replace("/login");
  } catch (error) {
    notify(error instanceof Error ? error.message : "Không đăng xuất được.");
  }
}

async function passwordChanged() {
  window.dispatchEvent(new Event("shopflow:session-expired"));
  await router.replace("/login");
  notify("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
}

function addToCart(product: Product, quantity = 1) {
  if (!addCartItem(product, quantity)) {
    notify("Số lượng trong giỏ đã đạt tồn kho hiện có");
    return;
  }
  notify(`Đã thêm ${product.name} vào giỏ`);
}

function setPageSize(size: number) {
  void updatePagination(1, size);
}

async function updateStockThreshold(productId: number, threshold: number) {
  if (!Number.isSafeInteger(threshold) || threshold < 0) {
    notify("Ngưỡng tồn kho phải là số nguyên không âm.");
    return;
  }
  apiStockSaving.value = true;
  try {
    await shopflowApi.updateThreshold(productId, threshold);
    products.value = products.value.map((item) => item.categoryId === productId ? { ...item, threshold } : item);
    notify("Đã cập nhật ngưỡng cảnh báo tồn kho.");
  } catch (error) {
    notify(error instanceof Error ? error.message : "Không cập nhật được ngưỡng tồn kho.");
  } finally { apiStockSaving.value = false; }
}

function notify(message: string) {
  toast.value = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.value = "";
  }, 2800);
}

function openModal(kind: Exclude<ModalKind, null>, productId?: number) {
  modalKind.value = kind;
  selectedProductId.value =
    productId ??
    (kind === "stock" && dataSource === "api"
      ? apiInventory.value[0]?.variantId
      : products.value[0]?.id) ??
    null;
  formError.value = "";
  Object.assign(form, {
    unitCost: 0, description: "", usageInstructions: "", imageUrls: "",
    name: "",
    sku: "",
    category: "Guitar acoustic",
    categoryId: apiCategories.value[0]?.id ?? 0,
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

async function openProductModal() {
  openModal("product");
  if (dataSource !== "api" || apiCategories.value.length) {
    form.categoryId = apiCategories.value[0]?.id ?? 0;
    return;
  }
  try {
    const result = await shopflowApi.getCategories({ page: 0, size: 100 });
    apiCategories.value = result.items;
    form.categoryId = result.items[0]?.id ?? 0;
    if (!result.items.length)
      formError.value = "Hãy tạo danh mục trước khi thêm sản phẩm.";
  } catch (error) {
    formError.value =
      error instanceof Error ? error.message : "Không tải được danh mục.";
  }
}

function closeModal() {
  modalKind.value = null;
  formError.value = "";
}

async function openCategoryManager() {
  categoryError.value = "";
  categoryManagerOpen.value = true;
  if (!apiCategories.value.length) {
    try {
      const result = await shopflowApi.getCategories({ page: 0, size: 100 });
      apiCategories.value = result.items;
    } catch (error) {
      categoryError.value =
        error instanceof Error ? error.message : "Không tải được danh mục.";
    }
  }
}

async function saveCategory(payload: {
  id?: number;
  input: { name: string; slug: string; description?: string | null };
}) {
  categorySaving.value = true;
  categoryError.value = "";
  try {
    const category = payload.id
      ? await shopflowApi.updateCategory(payload.id, payload.input)
      : await shopflowApi.createCategory(payload.input);
    const index = apiCategories.value.findIndex((item) => item.id === category.id);
    if (index < 0) apiCategories.value.unshift(category);
    else apiCategories.value.splice(index, 1, category);
    notify(payload.id ? "Đã cập nhật danh mục" : "Đã tạo danh mục");
  } catch (error) {
    categoryError.value =
      error instanceof Error ? error.message : "Không lưu được danh mục.";
  } finally {
    categorySaving.value = false;
  }
}

async function deleteCategory(id: number) {
  categoryError.value = "";
  if (!window.confirm("Bạn chắc chắn muốn xóa mềm danh mục này?")) return;
  categorySaving.value = true;
  try {
    await shopflowApi.deleteCategory(id);
    apiCategories.value = apiCategories.value.filter((item) => item.id !== id);
    if (categoryFilter.value === String(id)) categoryFilter.value = "all";
    notify("Đã xóa danh mục");
  } catch (error) {
    categoryError.value =
      error instanceof Error ? error.message : "Không xóa được danh mục.";
  } finally {
    categorySaving.value = false;
  }
}

function openProductDetail(id: number) {
  if (dataSource !== "api") return;
  productDetailId.value = id;
}

async function submitModal() {
  formError.value = "";
  if (modalKind.value === "order") {
    if (dataSource === "api") {
      if (
        !form.customer.trim() ||
        !form.phone.trim() ||
        !form.addressLine.trim() ||
        !form.city.trim()
      ) {
        formError.value = "Nhập tên, số điện thoại và địa chỉ giao hàng.";
        return;
      }
      await createApiOrder();
      return;
    }
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
    if (!Number.isSafeInteger(form.unitCost) || form.unitCost < 0) { formError.value = "Giá nhập phải là số nguyên VND không âm."; return; }
    if (!form.name.trim() || !form.sku.trim() || form.price <= 0) {
      formError.value = "Vui lòng điền tên, mã SKU và giá bán hợp lệ.";
      return;
    }
    if (dataSource === "api") {
      const categoryId = Number(form.categoryId);
      if (!Number.isInteger(categoryId) || categoryId <= 0) {
        formError.value = "Chọn danh mục hợp lệ cho sản phẩm.";
        return;
      }
      apiProductSaving.value = true;
      try {
        await shopflowApi.createProduct({
          categoryId,
          name: form.name.trim(),
          sku: form.sku.trim(),
          listPrice: form.price,
          costPrice: form.unitCost,
          description: form.description.trim() || null,
          usageInstructions: form.usageInstructions.trim() || null,
          imageUrls: form.imageUrls.split(/\r?\n/).map(value => value.trim()).filter(Boolean),
        });
        search.value = "";
        categoryFilter.value = "all";
        sortFilter.value = "newest";
        await updatePagination(1);
        const catalog = await shopflowApi.getProducts({
          page: 0,
          size: pageSize.value,
          sort: "newest",
        });
        apiProductPage.value = catalog;
        products.value = catalog.items.map(toCatalogProduct);
        closeModal();
        notify("Đã thêm sản phẩm vào danh mục");
      } catch (error) {
        formError.value =
          error instanceof Error ? error.message : "Không thể thêm sản phẩm.";
      } finally {
        apiProductSaving.value = false;
      }
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
      image: form.imageUrls.split(/\r?\n/).map(value => value.trim()).find(Boolean) || "",
    });
    closeModal();
    notify("Đã thêm sản phẩm vào danh mục");
    return;
  }
  if (modalKind.value === "return") {
    if (dataSource === "api") {
      const orderId = Number(form.orderId);
      const order = apiReturnableOrders.value.find(
        (item) => item.orderId === orderId,
      );
      const items =
        order?.items.filter((item) => item.returnableQuantity > 0) ?? [];
      if (!order || !items.length || !form.reason.trim()) {
        formError.value =
          "Chọn đơn hàng đủ điều kiện và nhập lý do đổi trả.";
        return;
      }
      apiReturnSaving.value = true;
      try {
        await shopflowApi.createReturn({
          orderId: order.orderId,
          reason: form.reason.trim(),
          items: items.map(({ orderItemId, returnableQuantity }) => ({
            orderItemId,
            quantity: returnableQuantity,
          })),
        });
        const [apiReturns, returnableOrders] = await Promise.all([
          shopflowApi.getReturns(),
          shopflowApi.getReturnableOrders(),
        ]);
        returns.value = apiReturns.map(toReturnView);
        apiReturnableOrders.value = returnableOrders;
        closeModal();
        notify("Đã gửi yêu cầu đổi trả lên máy chủ");
      } catch (error) {
        formError.value =
          error instanceof Error
            ? error.message
            : "Không gửi được yêu cầu đổi trả.";
      } finally {
        apiReturnSaving.value = false;
      }
      return;
    }
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
  if (dataSource === "api") {
    const product = apiInventory.value.find(
      (item) => item.variantId === selectedProductId.value,
    );
    if (!product || !Number.isInteger(form.quantity) || form.quantity < 1) {
      formError.value = "Chọn biến thể sản phẩm và số lượng nhập hợp lệ.";
      return;
    }
    if (!Number.isSafeInteger(form.unitCost) || form.unitCost < 0) { formError.value = "Nhập giá mua hợp lệ (VND nguyên)."; return; }
    apiStockSaving.value = true;
    try {
      await shopflowApi.receiveStock({
        variantId: product.variantId,
        quantity: form.quantity,
        unitCost: form.unitCost,
        supplierName: form.supplier.trim() || undefined,
        note: form.reason.trim() || undefined,
      });
      const inventory = await shopflowApi.getInventory();
      apiInventory.value = inventory;
      if (page.value === "receiving") {
        products.value = (await shopflowApi.listReceivings()).map(
          toReceivingProduct,
        );
      } else {
        products.value = inventory.map(toInventoryProduct);
      }
      closeModal();
      notify(`Đã ghi nhận nhập ${form.quantity} sản phẩm`);
    } catch (error) {
      formError.value =
        error instanceof Error ? error.message : "Không ghi nhận được phiếu nhập.";
    } finally {
      apiStockSaving.value = false;
    }
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

async function createApiOrder() {
  const product = apiOrderProducts.value.find(item => item.id === selectedProductId.value);
  const items = product ? [{ productId: product.id, quantity: form.quantity }] : [];
  if (!items.length || items.some((item) => item.quantity < 1)) {
    formError.value = "Chọn sản phẩm và nhập số lượng hợp lệ.";
    return;
  }
  if (
    !form.customer.trim() ||
    !form.phone.trim() ||
    !form.addressLine.trim() ||
    !form.city.trim()
  ) {
    formError.value = "Nhập tên, số điện thoại và địa chỉ giao hàng.";
    return;
  }

  apiOrderSaving.value = true;
  let orderRef: string | null = null;
  try {
    const created = await shopflowApi.createOrder({
      customer: {
        fullName: form.customer.trim(),
        email: form.email.trim() || null,
        phone: form.phone.trim(),
      },
      shippingAddress: {
        receiverName: form.customer.trim(),
        phone: form.phone.trim(),
        addressLine: form.addressLine.trim(),
        district: form.district.trim() || null,
        city: form.city.trim(),
      },
      paymentMethod: "CARD",
      items,
    });
    orderRef = created.orderRef;

    const payment = await shopflowApi.simulatePayment(orderRef, "SUCCESS");
    if (page.value === "orders" && canPerform("manageOrders", currentRole.value)) {
      const result = await shopflowApi.getOrders({
        page: currentPage.value - 1,
        size: pageSize.value,
      });
      apiOrderPage.value = result;
      orders.value = result.items.map(toOrderView);
    }
    if (page.value === "customer") {
      const catalog = await shopflowApi.getProducts({
        page: currentPage.value - 1,
        size: pageSize.value,
        query: search.value || undefined,
        categoryId:
          categoryFilter.value === "all"
            ? undefined
            : Number(categoryFilter.value),
        sort: sortFilter.value,
      });
      apiProductPage.value = catalog;
      products.value = catalog.items.map(toCatalogProduct);
    }
    closeModal();
    notify(
      payment.status === "SUCCESS"
        ? `Đã tạo và thanh toán đơn ${orderRef}`
        : `Đơn ${orderRef} đã được tạo nhưng thanh toán chưa thành công.`,
    );
  } catch (error) {
    if (orderRef) {
      closeModal();
      apiWorkspaceError.value =
        error instanceof Error
          ? error.message
          : "Không thể xác nhận trạng thái đơn hàng hoặc thanh toán.";
      notify(
        `Đơn ${orderRef} đã được tạo; hãy kiểm tra trạng thái trước khi thử lại.`,
      );
    } else {
      formError.value =
        error instanceof Error ? error.message : "Không tạo được đơn hàng.";
    }
  } finally {
    apiOrderSaving.value = false;
  }
}

async function advanceOrder(order: Order) {
  if (dataSource === "api") {
    if (!order.apiId || !order.deliveryStatus || (order.payment !== "Đã thanh toán" && order.paymentMethod !== 'COD'))
      return;
    const nextStatus =
      order.deliveryStatus === "NONE"
        ? "PREPARING"
        : order.deliveryStatus === "PREPARING"
        ? "SHIPPED"
        : order.deliveryStatus === "SHIPPED"
          ? "DELIVERED"
          : null;
    if (!nextStatus) return;
    try {
      const updated = await shopflowApi.updateDelivery(order.apiId, nextStatus);
      const updatedOrder = toDeliveryOrder(updated);
      orders.value = orders.value.map((item) =>
        item.apiId === order.apiId ? updatedOrder : item,
      );
      notify(`Đơn ${order.id}: đã cập nhật trạng thái giao hàng`);
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : "Không cập nhật được trạng thái giao hàng.",
      );
    }
    return;
  }
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

async function setReturnStatus(
  request: ReturnRequest,
  status: ReturnRequest["status"],
) {
  const action = status === "Đã nhập kho" ? "restockReturn" : "approveReturn";
  if (!canPerform(action, currentRole.value)) {
    notify("Vai trò hiện tại không có quyền thực hiện thao tác này");
    return;
  }
  if (dataSource === "api") {
    if (!request.apiId) {
      notify("Không tìm thấy mã yêu cầu đổi trả trên máy chủ.");
      return;
    }
    const statusMap: Record<
      ReturnRequest["status"],
      "APPROVED" | "REJECTED" | "RESTOCKED"
    > = {
      "Chờ xử lý": "APPROVED",
      "Đã chấp nhận": "APPROVED",
      "Đã nhập kho": "RESTOCKED",
      "Đã từ chối": "REJECTED",
    };
    const restockable =
      status === "Đã chấp nhận"
        ? window.confirm(
            "Hàng hoàn còn đủ điều kiện nhập lại kho không? Chọn OK nếu có, Hủy nếu không.",
          )
        : status === "Đã nhập kho"
          ? true
          : undefined;
    try {
      await shopflowApi.updateReturn(
        request.apiId,
        statusMap[status],
        restockable,
      );
      const [apiReturns, returnableOrders] = await Promise.all([
        shopflowApi.getReturns(),
        currentRole.value === "SHOP_OWNER" ? shopflowApi.getReturnableOrders() : Promise.resolve([]),
      ]);
      returns.value = apiReturns.map(toReturnView);
      apiReturnableOrders.value = returnableOrders;
      notify(`Đã cập nhật yêu cầu ${request.id}`);
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : "Không cập nhật được yêu cầu đổi trả.",
      );
    }
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

async function openReturnModal() {
  openModal("return");
  if (dataSource !== "api") return;
  try {
    apiReturnableOrders.value = await shopflowApi.getReturnableOrders();
    if (!apiReturnableOrders.value.length) {
      closeModal();
      notify("Chưa có đơn đã thanh toán và giao thành công đủ điều kiện đổi trả.");
      return;
    }
    form.orderId = String(apiReturnableOrders.value[0].orderId);
  } catch (error) {
    formError.value =
      error instanceof Error
        ? error.message
        : "Không tải được các đơn hàng đủ điều kiện đổi trả.";
  }
}

async function openOrderModal() {
  openModal("order");
  if (dataSource !== "api") return;
  try {
    const catalog = await shopflowApi.getProducts({ page: 0, size: 100 });
    apiOrderProducts.value = catalog.items.map(toCatalogProduct);
    if (!apiOrderProducts.value.length) {
      closeModal();
      notify("Chưa có sản phẩm để tạo đơn hàng.");
      return;
    }
    selectedProductId.value = apiOrderProducts.value[0].id;
  } catch (error) {
    formError.value =
      error instanceof Error ? error.message : "Không tải được sản phẩm.";
  }
}

async function openStockModal() {
  if (dataSource === "api") {
    try {
      apiInventory.value = await shopflowApi.getInventory();
      if (!apiInventory.value.length) {
        notify("Chưa có biến thể sản phẩm để nhập kho.");
        return;
      }
    } catch (error) {
      apiWorkspaceError.value =
        error instanceof Error ? error.message : "Không tải được tồn kho.";
      return;
    }
  }
  openModal("stock");
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
    void openReturnModal();
    return;
  }
  if (page.value === "products") {
    openProductModal();
    return;
  }
  if (page.value === "orders") {
    void openOrderModal();
    return;
  }
  if (page.value === "inventory" || page.value === "receiving") {
    void openStockModal();
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
  <LoginPage v-if="page === 'login'" />
  <CheckoutPage v-else-if="page === 'checkout'" :cart-items="cartItems" :cart-total="cartTotal" @clear-cart="clearCart" @refresh-products="refreshCart" />
  <ProductPage v-else-if="page === 'product'" :product-id="Number(route.params.id)" :cart-quantity="cart[Number(route.params.id)] || 0" @add="addToCart" />
  <StoreInfoPage v-else-if="page === 'about' || page === 'guide'" :section="page" />
  <AccountPage v-else-if="page === 'profile' || page === 'purchases'" :tab="page" @logout="logout" @password-changed="passwordChanged" />
  <main v-else-if="page === 'forbidden'" class="auth-page">
    <section class="auth-panel forbidden-panel">
      <span class="auth-kicker">403 · KHÔNG ĐỦ QUYỀN</span>
      <h1>Trang này không dành cho vai trò hiện tại</h1>
      <p>
        Tài khoản hiện tại không có quyền truy cập trang này.
      </p>
      <button
        class="button button-primary"
        @click="
          router.replace(landingPage(currentRole))
        "
      >
        Về trang phù hợp <ArrowRight :size="16" />
      </button>
    </section>
  </main>
  <main v-else-if="page === 'customer'" class="storefront-page">
    <header class="storefront-header">
      <RouterLink to="/customer" class="store-brand"
        ><span class="brand-mark"><span></span><span></span><span></span></span
        ><strong>ShopFlow</strong></RouterLink
      ><label class="store-search"
        ><Search :size="17" /><input
          v-model="search"
          placeholder="Tìm sản phẩm"
          aria-label="Tìm sản phẩm"
      /></label>
      <select
        v-model="categoryFilter"
        class="store-category-select"
        aria-label="Lọc danh mục"
      >
        <option value="all">Tất cả danh mục</option>
        <option v-for="category in categoryOptions" :key="category.id" :value="String(category.id)">
          {{ category.name }}
        </option>
      </select>
      <div class="store-header-actions">
        <RouterLink to="/about" class="text-link">Về chúng tôi</RouterLink><RouterLink to="/guide" class="text-link">Cẩm nang</RouterLink>
        <RouterLink
          v-if="currentUser && currentRole !== 'CUSTOMER'"
          :to="landingPage(currentRole)"
          class="button button-outline workspace-link"
          ><LayoutDashboard :size="16" />Vận hành</RouterLink
        ><RouterLink
          v-else-if="dataSource === 'demo'"
          to="/login"
          class="text-link"
          >{{ currentUser ? "Đổi vai trò" : "Đăng nhập" }}</RouterLink
        ><RouterLink v-else-if="!currentUser" to="/login" class="text-link"
          >Đăng nhập</RouterLink
        >
        <details v-if="currentUser" class="account-menu">
          <summary>{{ currentUser.displayName }}</summary>
          <nav><RouterLink to="/profile">Thông tin cá nhân</RouterLink><RouterLink to="/purchases">Lịch sử mua / Theo dõi đơn</RouterLink></nav>
        </details>
        <button v-if="currentUser" class="button button-outline" @click="logout"><LogOut :size="15" />Đăng xuất</button>
        <RouterLink
          v-if="cartItems.length"
          :to="checkoutDestination"
          class="button button-primary cart-button"
        >
          <ShoppingCart :size="17" /><span>Giỏ hàng</span
          ><b :key="cartItems.reduce((sum, item) => sum + item.quantity, 0)" class="cart-bump">{{ cartItems.reduce((sum, item) => sum + item.quantity, 0) }}</b>
        </RouterLink>
        <button v-else type="button" class="button button-primary cart-button" @click="notify('Giỏ hàng đang trống')"><ShoppingCart :size="17" /><span>Giỏ hàng</span><b>0</b></button>
      </div>
    </header>
    <section class="storefront-hero">
      <div class="hero-copy">
        <span class="auth-kicker">SHOPFLOW · NHẠC CỤ & ĐAM MÊ</span>
        <h1>Chạm từng âm sắc,<br />bắt đầu đam mê.</h1>
        <p>Khám phá guitar, piano, violin, trống và kèn cho hành trình âm nhạc của bạn.</p>
        <a class="hero-explore" href="#collection">Khám phá bộ sưu tập <ArrowRight :size="17" /></a>
      </div>
      <div class="hero-orbit" aria-hidden="true"><span></span><span></span><span></span></div>
      <div class="hero-stamp">
        <span>PLAY<br />YOUR STORY</span><Sparkles :size="25" />
      </div>
    </section>
    <div class="storefront-content">
      <section id="collection" class="store-catalog">
        <div v-reveal class="store-section-heading">
          <div>
            <span class="auth-kicker">BỘ SƯU TẬP</span>
            <h2>Sản phẩm nổi bật</h2>
          </div>
          <span>{{ productPage.total }} sản phẩm</span>
        </div>
        <form class="catalog-filters" @submit.prevent>
          <label>Giá từ<input :value="minPriceFilter ?? ''" type="number" min="0" step="10000" placeholder="0 ₫" @change="minPriceFilter = ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value)" /></label>
          <label>Đến<input :value="maxPriceFilter ?? ''" type="number" min="0" step="10000" placeholder="Không giới hạn" @change="maxPriceFilter = ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value)" /></label>
          <label>Sắp xếp<select v-model="sortFilter"><option value="newest">Mới nhất</option><option value="priceAsc">Giá tăng dần</option><option value="priceDesc">Giá giảm dần</option><option value="rating">Đánh giá cao</option></select></label>
          <label>Đánh giá<select v-model.number="ratingFilter"><option :value="0">Tất cả</option><option v-for="star in [5,4,3,2,1]" :key="star" :value="star">Từ {{ star }} sao</option></select></label>
          <label class="stock-filter"><input v-model="stockFilter" type="checkbox" />Chỉ còn hàng</label><button type="button" class="text-link" @click="resetStoreFilters">Xóa bộ lọc</button>
        </form>
        <div v-if="apiProductLoading && !filteredProducts.length" class="catalog-skeleton" role="status" aria-label="Đang tải sản phẩm">
          <div v-for="item in 6" :key="item" class="skeleton-card" aria-hidden="true"><div class="skeleton-image"></div><i></i><i></i><i></i></div>
          <span class="sr-only">Đang tải sản phẩm…</span>
        </div>
        <p v-else-if="apiProductLoading" class="empty-state motion-loading" role="status">Đang tải sản phẩm…</p>
        <p v-else-if="apiProductError" class="api-error-banner" role="alert">{{ apiProductError }}</p>
        <div class="store-product-grid">
          <article
            v-for="(product, index) in filteredProducts"
            :key="product.id"
            v-reveal="index % 6"
            class="store-product"
          >
            <RouterLink :to="`/product/${product.id}`" class="store-product-image" :aria-label="`Xem ${product.name}`">
              <ProductImage :src="product.image" :alt="product.name" /><span
                v-if="product.stock <= product.threshold && product.stock > 0"
                class="stock-ribbon"
                >Sắp hết</span
              ><span v-if="product.stock === 0" class="stock-ribbon sold-out"
                >Hết hàng</span
              >
            </RouterLink>
            <div class="store-product-meta">
              <span>{{ product.category }}</span
              ><span>SKU {{ product.sku }}</span>
            </div>
            <h3>
              <RouterLink v-if="dataSource === 'api'" class="store-product-detail-link" :to="`/product/${product.id}`">{{ product.name }}</RouterLink>
              <span v-else>{{ product.name }}</span>
            </h3>
            <p class="store-card-rating">★ {{ product.reviewCount ? `${product.averageRating?.toFixed(1)} · ${product.reviewCount} đánh giá` : 'Chưa có đánh giá' }}</p>
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
        <p v-if="!apiProductLoading && !apiProductError && !filteredProducts.length" class="empty-state">
          Chưa có sản phẩm đang bán.
        </p>
        <PaginationBar
          :page="productPage.page"
          :page-size="productPage.size"
          :total="productPage.total"
          :total-pages="productPage.totalPages"
          @change-page="updatePagination($event)"
          @change-size="setPageSize"
        />
      </section>
      <aside id="cart" v-reveal="2" class="store-cart-panel">
        <div class="store-cart-heading">
          <div>
            <span class="auth-kicker">GIỎ HÀNG</span>
            <h2>Đơn của bạn</h2>
          </div>
          <span :key="cartItems.reduce((sum, item) => sum + item.quantity, 0)" class="cart-count cart-bump">{{
            cartItems.reduce((sum, item) => sum + item.quantity, 0)
          }}</span>
        </div>
        <TransitionGroup name="cart-item" tag="div" class="store-cart-items">
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
        </TransitionGroup>
        <div v-if="!cartItems.length" class="cart-empty">
          <ShoppingBag :size="24" />
          <p>Chưa có sản phẩm nào.<br />Chọn món bạn yêu thích nhé.</p>
        </div>
        <div class="cart-total">
          <span>Tạm tính</span><strong>{{ money(cartTotal) }}</strong>
        </div>
        <RouterLink
          v-if="cartItems.length"
          :to="checkoutDestination"
          class="button button-primary checkout-button"
        >
          Tiến hành đặt hàng <ArrowRight :size="16" />
        </RouterLink>
        <button v-else type="button" class="button button-primary checkout-button" disabled>Tiến hành đặt hàng <ArrowRight :size="16" /></button>
        <p class="store-checkout-note">
          Chọn địa chỉ, voucher và phương thức ở trang thanh toán.
        </p>
      </aside>
    </div>
    <footer class="storefront-footer">
      <span>© 2026 ShopFlow</span
      ><nav class="store-footer-links"><RouterLink to="/about">Về chúng tôi</RouterLink><RouterLink to="/guide">Cẩm nang mua sắm</RouterLink></nav><span>Âm nhạc bắt đầu từ cảm hứng.</span>
    </footer>
    <Transition name="dialog">
    <ProductDetailDialog
      v-if="productDetailId !== null && productDetail"
      :product="productDetail"
      @close="productDetailId = null"
    />
    </Transition>
    <Transition name="toast"><div v-if="toast" :key="toast" class="toast-message" role="status"><span><Check :size="16" /></span>{{ toast }}</div></Transition>
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
        <div><strong>ShopFlow</strong><small>QUẢN LÝ CỬA HÀNG</small></div>
        <button
          class="icon-button sidebar-close"
          aria-label="Đóng menu"
          @click="mobileMenuOpen = false"
        >
          <X :size="18" />
        </button>
      </div>

      <button class="store-switcher">
        <span class="store-avatar">S</span>
        <span class="store-copy"
          ><strong>ShopFlow</strong><small>Cửa hàng chính</small></span
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
          <strong>ShopFlow</strong>
          <p>Quản lý cửa hàng dễ dàng hơn mỗi ngày.</p>
          <button @click="notify('Bạn đang sử dụng gói demo ShopFlow')">
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
            ><small>{{ currentRole ? roleLabels[currentRole] : "Khách" }}</small></span
          ><RouterLink to="/profile" aria-label="Thông tin cá nhân"><Settings2 :size="17" /></RouterLink>
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
          <span>ShopFlow</span><span class="crumb-divider">/</span
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
            v-if="canAccessPage('inventory', currentRole)"
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
            <option value="SALES_STAFF">Bán hàng</option>
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
          <button class="top-avatar" aria-label="Tài khoản" @click="router.push('/profile')">
            {{ currentUser?.displayName.slice(0, 1) || "G" }}
          </button>
        </div>
      </header>

      <main :key="page" class="page-wrap">
        <div class="page-heading">
          <div>
            <div class="eyebrow">
              <span class="live-dot"></span> CỬA HÀNG ĐANG HOẠT ĐỘNG
            </div>
            <h1>{{ title }}</h1>
            <p v-if="page === 'overview'">
              Chào {{ currentUser?.displayName || "bạn" }}. Đây là tình hình cửa hàng hôm nay.
            </p>
            <p v-else-if="page === 'staff'">
              Tạo và cấp quyền truy cập cho nhân viên cửa hàng.
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
              v-if="page !== 'staff' && (page !== 'delivery' || canPerform('manageOrders', currentRole))"
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

        <div v-if="apiWorkspaceLoading" class="api-loading" role="status">Đang tải dữ liệu từ backend…</div>
        <div v-else-if="apiWorkspaceError" class="api-error-banner" role="alert">{{ apiWorkspaceError }}</div>

        <StaffPage v-if="page === 'staff'" />

        <template v-else-if="page === 'overview'">
          <section class="stats-grid">
            <article v-reveal="0" class="stat-card">
              <div class="stat-top">
                <span>Doanh thu ghi nhận</span
                ><span class="stat-icon mint"><Activity :size="18" /></span>
              </div>
              <strong><AnimatedNumber :value="salesTotal" :format="money" /></strong>
              <div v-if="dataSource === 'demo'" class="stat-foot">
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
            <article v-reveal="1" class="stat-card">
              <div class="stat-top">
                <span>Đơn hàng mới</span
                ><span class="stat-icon peach"><ShoppingBag :size="18" /></span>
              </div>
              <strong><AnimatedNumber :value="dashboardOrderCount" /> <small>đơn</small></strong>
              <div v-if="dataSource === 'demo'" class="stat-foot">
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
            <article v-reveal="2" class="stat-card">
              <div class="stat-top">
                <span>Giá trị đơn trung bình</span
                ><span class="stat-icon lavender"
                  ><ClipboardList :size="18"
                /></span>
              </div>
              <strong><AnimatedNumber :value="averagePaidOrder" :format="money" /></strong>
              <div v-if="dataSource === 'demo'" class="stat-foot">
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
            <article v-reveal="3" class="stat-card">
              <div class="stat-top">
                <span>Sản phẩm cần nhập</span
                ><span class="stat-icon yellow"><Boxes :size="18" /></span>
              </div>
              <strong
                ><AnimatedNumber :value="apiDashboard?.lowStockCount ?? lowStockProducts.length" /> <small>mặt hàng</small></strong
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
          <section v-if="dataSource === 'api'" class="cost-overview-grid">
            <article class="stat-card"><div class="stat-top"><span>Tiền nhập hàng · 7 ngày</span><PackagePlus :size="18" /></div><strong><AnimatedNumber :value="apiDashboard?.purchasingTotal || 0" :format="money" /></strong><p class="cost-caption">Theo giá đã ghi trên từng phiếu nhập.</p></article>
            <article class="stat-card"><div class="stat-top"><span>Giá trị tồn kho ước tính</span><Boxes :size="18" /></div><strong><AnimatedNumber :value="apiDashboard?.inventoryCostEstimate || 0" :format="money" /></strong><p class="cost-caption">Số lượng tồn × giá nhập gần nhất; không phải lợi nhuận.</p></article>
          </section>
          <p v-if="apiDashboard?.unpricedReceiptCount" class="cost-warning">Có {{ apiDashboard.unpricedReceiptCount }} phiếu nhập cũ chưa có giá trong kỳ; tổng tiền nhập chỉ tính các phiếu đã có giá.</p>

          <section class="overview-grid">
            <article v-reveal class="panel revenue-panel">
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
                ><span v-if="dataSource === 'demo'" class="trend-up"><ArrowUpRight :size="14" />12,8%</span
                ><small>{{ dataSource === 'api' ? 'Doanh thu đã thanh toán · 7 ngày gần nhất' : 'so với kỳ trước' }}</small>
              </div>
              <div v-if="dataSource === 'demo'" class="chart-area">
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
              <div v-else class="live-revenue-chart">
                <div class="muted-cell">{{ money(revenueChart.max) }}</div>
                <svg viewBox="0 0 700 200" role="img" aria-label="Doanh thu thực tế theo ngày">
                  <line x1="0" y1="180" x2="700" y2="180" stroke="#d9e0d8" />
                  <polyline :points="revenueChart.points" fill="none" stroke="#1d6957" stroke-width="3" />
                </svg>
                <div class="chart-x-axis"><span v-for="label in revenueChart.labels" :key="label">{{ label }}</span></div>
              </div>
            </article>
            <article v-if="dataSource === 'demo'" class="panel channel-panel">
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
            <article v-else class="panel channel-panel">
              <div class="panel-heading"><div><h2>Tình hình đơn hàng</h2><p>7 ngày gần nhất</p></div></div>
              <div class="channel-legend live-order-summary">
                <div><span>Tổng đơn hàng</span><strong>{{ dashboardOrderCount }}</strong></div>
                <div><span>Đã thanh toán</span><strong>{{ apiDashboard?.paidOrderCount ?? 0 }}</strong></div>
                <div><span>Giá trị đơn đã thanh toán trung bình</span><strong>{{ money(averagePaidOrder) }}</strong></div>
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

        <ProductsInventoryPage
          v-else-if="
            page === 'products' || page === 'inventory' || page === 'receiving'
          "
          :page="page"
          :data-source="dataSource"
          :products="products"
          :filtered-products="filteredProducts"
          :product-page="productPage"
          :low-stock-count="lowStockProducts.length"
          :category-options="categoryOptions"
          :category-filter="categoryFilter"
          :sort-filter="sortFilter"
          :search="search"
          :api-product-loading="apiProductLoading"
          :api-product-error="apiProductError"
          :stock-saving="apiStockSaving"
          :money="money"
          @update:search="search = $event"
          @update:category-filter="categoryFilter = $event"
          @update:sort-filter="sortFilter = $event"
          @navigate-products="router.push('/products')"
          @navigate-inventory="router.push('/inventory')"
          @open-categories="openCategoryManager"
          @export-products="exportProducts"
          @open-detail="openProductDetail"
          @open-stock="openModal('stock', $event)"
          @update-threshold="updateStockThreshold"
          @change-page="updatePagination"
          @change-size="setPageSize"
        />

        <OrdersDeliveryPage
          v-else-if="page === 'orders' || page === 'delivery'"
          :page="page"
          :orders="orders"
          :filtered-orders="filteredOrders"
          :order-page="orderPage"
          :pending-orders="pendingOrders"
          :can-manage-orders="canPerform('manageOrders', currentRole)"
          :search="search"
          :money="money"
          :status-class="statusClass"
          @update:search="search = $event"
          @notify="notify"
          @navigate-orders="router.push('/orders')"
          @export-orders="exportOrders"
          @advance-order="advanceOrder"
          @change-page="updatePagination"
          @change-size="setPageSize"
        />

        <ReturnsPage
          v-else
          :returns="returns"
          :filtered-returns="filteredReturns"
          :return-page="returnPage"
          :search="search"
          :can-approve="canPerform('approveReturn', currentRole)"
          :can-restock="canPerform('restockReturn', currentRole)"
          :status-class="statusClass"
          @update:search="search = $event"
          @notify="notify"
          @set-status="setReturnStatus"
          @change-page="updatePagination"
          @change-size="setPageSize"
        />

        <footer class="app-footer">
          <span>© 2026 ShopFlow</span
          ><span>Vận hành cửa hàng, nhẹ nhàng hơn.</span
          ><span class="footer-status"
            ><i></i> Hệ thống hoạt động bình thường</span
          >
        </footer>
      </main>
    </div>

    <Transition name="dialog">
    <div
      v-if="modalKind"
      class="modal-backdrop"
      @click.self="closeModal"
      @keydown.esc="closeModal"
    >
      <form class="modal" @submit.prevent="submitModal">
        <div class="modal-heading">
          <div>
            <span class="modal-kicker">SHOPFLOW · KHO VẬN</span>
            <h2>
              {{
                modalKind === "order"
                  ? "Tạo đơn hàng mới"
                  : modalKind === "product"
                    ? "Thêm sản phẩm"
                    : modalKind === "return"
                      ? "Tạo yêu cầu đổi trả"
                      : "Nhập hàng vào kho"
              }}
            </h2>
            <p>
              {{
                modalKind === "order"
                  ? "Tạo đơn và cập nhật tồn kho tự động."
                  : modalKind === "product"
                    ? "Thêm mặt hàng mới vào danh mục cửa hàng."
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
        <template v-if="modalKind === 'order'"
          ><label class="form-label"
            >Tên khách hàng<input
              v-model="form.customer"
              autofocus
              placeholder="Ví dụ: Nguyễn Minh Anh" /></label
          ><template v-if="dataSource === 'api'">
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
          <label class="form-label"
            >Sản phẩm<select v-model.number="selectedProductId">
              <option
                v-for="product in dataSource === 'api' ? apiOrderProducts : products"
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
              >Danh mục<select
                v-if="dataSource === 'api'"
                v-model.number="form.categoryId"
                required
              >
                <option :value="0" disabled>Chọn danh mục</option>
                <option
                  v-for="category in apiCategories"
                  :key="category.id"
                  :value="category.id"
                >
                  {{ category.name }}
                </option>
              </select>
              <select v-else v-model="form.category">
                <option>Guitar acoustic</option>
                <option>Guitar điện</option>
                <option>Đàn piano</option>
                <option>Trống acoustic</option><option>Đàn violin</option><option>Kèn trumpet</option>
              </select></label
            >
          </div>
          <label class="form-label"
            >Giá bán (VND)<input
              v-model.number="form.price"
              type="number"
              min="1"
              placeholder="0" /></label
        >
          <label class="form-label">Giá nhập ban đầu (VND)<input v-model.number="form.unitCost" type="number" min="0" max="9999999999" step="1" /></label>
          <label class="form-label">Giới thiệu sản phẩm<textarea v-model="form.description" rows="4" maxlength="5000" placeholder="Chất liệu, công dụng, kích thước…" /></label>
          <label class="form-label">Hướng dẫn sử dụng<textarea v-model="form.usageInstructions" rows="4" maxlength="5000" placeholder="Cách sử dụng và bảo quản riêng của sản phẩm" /></label>
          <label class="form-label">Link ảnh (mỗi dòng một URL, tối đa 8)<textarea v-model="form.imageUrls" rows="3" placeholder="https://…/san-pham.jpg" /></label>
          <div class="image-url-previews"><img v-for="url in form.imageUrls.split(/\r?\n/).map(value => value.trim()).filter(value => /^https?:\/\//i.test(value)).slice(0,8)" :key="url" :src="url" alt="Xem trước ảnh sản phẩm" loading="lazy" /></div>
          <p class="muted-cell">Ảnh đầu tiên là ảnh đại diện. Dùng link ảnh công khai HTTP/HTTPS.</p>
        </template>
        <template v-else-if="modalKind === 'return'"
          ><label class="form-label"
            >Đơn hàng<select v-model="form.orderId">
              <option
                v-if="dataSource !== 'api'"
                v-for="order in orders"
                :key="order.id"
                :value="order.id"
              >
                {{ order.id }} · {{ order.customer }}
              </option>
              <option
                v-for="order in dataSource === 'api' ? apiReturnableOrders : []"
                :key="order.orderId"
                :value="String(order.orderId)"
              >
                SF-{{ order.orderId }} · {{ order.receiverName }} ·
                {{ order.items.reduce((total, item) => total + item.returnableQuantity, 0) }} sp có thể đổi trả
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
            >Sản phẩm
            <select v-if="dataSource === 'api'" v-model.number="selectedProductId">
              <option
                v-for="product in apiInventory"
                :key="product.variantId"
                :value="product.variantId"
              >
                {{ product.productName }} · hiện có {{ product.availableStock }}
              </option>
            </select>
            <select v-else v-model.number="selectedProductId">
              <option v-for="product in products" :key="product.id" :value="product.id">
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
          ><label class="form-label">Giá nhập / sản phẩm (VND)<input v-model.number="form.unitCost" type="number" min="0" max="9999999999" step="1" required /></label
          ><div class="receiving-total"><span>Tổng tiền phiếu nhập</span><strong>{{ money((Number(form.quantity) || 0) * (Number(form.unitCost) || 0)) }}</strong></div
          ><label class="form-label">Ghi chú<input v-model="form.reason" maxlength="500" placeholder="Mã lô, ghi chú nhập hàng…" /></label
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
          ><button
            type="submit"
            class="button button-primary"
            :disabled="
              apiProductSaving ||
              apiOrderSaving ||
              apiReturnSaving ||
              apiStockSaving
            "
          >
            <Check :size="16" />{{
              apiProductSaving ||
              apiOrderSaving ||
              apiReturnSaving ||
              apiStockSaving
                ? "Đang lưu..."
                : modalKind === "order"
                ? "Tạo đơn hàng"
                : modalKind === "product"
                  ? "Lưu sản phẩm"
                  : modalKind === "return"
                    ? "Gửi yêu cầu"
                    : "Xác nhận nhập"
            }}
          </button>
        </div>
      </form>
    </div>
    </Transition>
    <Transition name="toast"
      ><div v-if="toast" :key="toast" class="toast-message" role="status">
        <span><Check :size="16" /></span>{{ toast }}
      </div></Transition
    >
    <Transition name="dialog">
    <CategoryManager
      v-if="categoryManagerOpen"
      :categories="apiCategories"
      :busy="categorySaving"
      :error="categoryError"
      @close="categoryManagerOpen = false"
      @save="saveCategory"
      @remove="deleteCategory"
    />
    </Transition>
    <Transition name="dialog">
    <ProductDetailDialog
      v-if="productDetailId !== null && productDetail"
      :product="productDetail"
      @close="productDetailId = null"
    />
    </Transition>
  </div>
</template>
