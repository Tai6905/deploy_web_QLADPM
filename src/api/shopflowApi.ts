import { apiRequest } from "./httpClient";

export type UserRole = "CUSTOMER" | "WAREHOUSE" | "SALES_STAFF" | "SHOP_OWNER";
export const roleLabels: Record<UserRole, string> = {
  SHOP_OWNER: "Chủ shop", SALES_STAFF: "Nhân viên bán hàng", WAREHOUSE: "Nhân viên kho", CUSTOMER: "Khách hàng",
};
export type AccountProfile = {
  userId: number; email: string; displayName: string; phone: string | null;
  role: UserRole; active: boolean; createdAt: string | null;
};
export type MyOrderDetail = { order: OrderResponse; delivery: DeliveryOrder; returnableItems: ReturnableOrder["items"]; returns: ApiReturnRequest[] };
export type PaymentMethod = 'COD' | 'CARD' | 'BANK_TRANSFER';
export type ShippingAddress = { receiverName: string; phone: string; addressLine: string; district?: string | null; city: string };
export type SavedAddress = ShippingAddress & { id: number; isDefault: boolean };
export type CheckoutQuote = { subtotalAmount: number; discountAmount: number; totalAmount: number; voucherCode: string | null };
export type CheckoutVoucher = { code: string; title: string; minimum: number; type: string; value: number; maximum: number | null };
export type CheckoutRequest = { shippingAddress: ShippingAddress; contactEmail?: string; paymentMethod: PaymentMethod; items: { productId: number; quantity: number }[]; voucherCode?: string | null; checkoutKey: string; saveAddress: boolean; addressId?: number | null; makeDefault: boolean; expectedTotal: number };
export type PageQuery = {
  specifications?: Record<string, string>;
  status?: "PENDING_PAYMENT" | "PAID" | "PAYMENT_FAILED" | "CANCELLED";
  page: number;
  size: number;
  query?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  rating?: number;
  inStock?: boolean;
  sort?: "newest" | "priceAsc" | "priceDesc" | "rating";
};
export type ApiPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type SessionUser = {
  userId: number;
  username: string;
  displayName: string;
  role: UserRole;
};
export type SessionResponse = {
  authenticated: boolean;
  user: SessionUser | null;
  csrfToken: string;
};
export type CreateEmployeeRequest = {
  email: string;
  password: string;
  displayName: string;
  role?: "SALES_STAFF" | "WAREHOUSE";
};
export type ProductCreateRequest = {
  specifications?: Record<string, string>;
  categoryId: number;
  sku: string;
  name: string;
  description?: string | null;
  costPrice?: number;
  listPrice: number;
  usageInstructions?: string | null;
  imageUrls?: string[];
};
export type ProductListItem = {
  specifications?: Record<string, string>;
  id: number;
  categoryId: number;
  categoryName: string;
  sku: string;
  name: string;
  slug: string;
  description: string | null;
  listPrice: number;
  availableQuantity: number;
  imageUrl: string | null;
  averageRating: number;
  reviewCount: number;
};
export type ProductDetail = {
  specifications?: Record<string, string>;
  usageInstructions: string | null;
  averageRating: number;
  reviewCount: number;
  id: number;
  categoryId: number;
  categoryName: string;
  sku: string;
  name: string;
  slug: string;
  description: string | null;
  costPrice?: number;
  listPrice: number;
  createdAt: string;
  updatedAt: string;
  productVariants: Array<{
    id: number;
    color: string | null;
    size: string | null;
    quantity: number;
    reservedQuantity: number;
    holdingQuantity: number;
    availableQuantity: number;
  }>;
  productImages: Array<{
    id: number;
    imageUrl: string;
    displayOrder: number;
    primary: boolean;
  }>;
};
export type Category = {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};
export type CategoryInput = {
  name: string;
  slug: string;
  description?: string | null;
};
export type OrderSummary = {
  cancellation?: CancellationInfo | null;
  paymentMethod?: PaymentMethod;
  id: number;
  orderRef: string;
  customer: string;
  product: string;
  quantity: number;
  totalAmount: number;
  status: "PENDING_PAYMENT" | "PAID" | "PAYMENT_FAILED" | "CANCELLED";
  paymentStatus: "UNPAID" | "PENDING" | "SUCCESS" | "FAILED" | "EXPIRED" | "CANCELLED" | "REFUNDED";
  deliveryStatus: "NONE" | "PREPARING" | "SHIPPED" | "DELIVERED";
  createdAt: string;
};
export type PaymentResult = "SUCCESS" | "FAILED" | "EXPIRED";
export type InventoryItem = {
  productId: number;
  variantId: number;
  productName: string;
  color: string | null;
  size: string | null;
  onHandStock: number;
  reservedStock: number;
  holdingStock: number;
  availableStock: number;
  lowStockThreshold: number;
  lowStock: boolean;
};
export type ReceivingResponse = {
  id: number;
  productId: number;
  variantId: number;
  productName: string;
  quantity: number;
  unitCost: number | null;
  totalCost: number | null;
  supplierName: string | null;
  note: string | null;
  createdAt: string;
  onHandStock: number;
  reservedStock: number;
  availableStock: number;
};
export type DeliveryOrder = {
  paymentMethod?: PaymentMethod;
  orderId: number;
  orderStatus: string;
  deliveryStatus: "NONE" | "PREPARING" | "SHIPPED" | "DELIVERED";
  receiverName: string;
  city: string;
  totalAmount: number;
  createdAt: string;
  items: Array<{ productId: number; productName: string; variantId: number; quantity: number }>;
  history: Array<{ fromStatus: string; toStatus: string; changedAt: string; changedBy: number | null }>;
};
export type ApiReturnRequest = {
  id: number;
  orderId: number;
  status: "REQUESTED" | "APPROVED" | "RESTOCKED" | "REJECTED";
  reason: string | null;
  restockable: boolean | null;
  createdAt: string;
  updatedAt: string;
  items: Array<{ orderItemId: number; productId: number; variantId: number; productName: string; quantity: number }>;
};
export type ReturnableOrder = {
  orderId: number;
  receiverName: string;
  city: string;
  totalAmount: number;
  createdAt: string;
  items: Array<{
    orderItemId: number;
    productId: number;
    variantId: number;
    productName: string;
    purchasedQuantity: number;
    returnedQuantity: number;
    returnableQuantity: number;
  }>;
};
export type DashboardSummary = {
  purchasingTotal: number;
  inventoryCostEstimate: number;
  unpricedReceiptCount: number;
  range: "7d" | "30d" | "90d";
  revenue: number;
  orderCount: number;
  lowStockCount: number;
  from: string;
  to: string;
  paidOrderCount: number;
  dailyRevenue: Array<{ date: string; revenue: number }>;
};
export type CreateOrderRequest = {
  customer: { fullName: string; email?: string | null; phone?: string | null };
  shippingAddress: { receiverName: string; phone: string; addressLine: string; district?: string | null; city: string };
  paymentMethod: "CARD";
  items: Array<{ productId: number; quantity: number }>;
};
export type OrderResponse = {
  cancellation?: CancellationInfo | null;
  subtotalAmount: number; discountAmount: number; voucherCode: string | null;
  id: number;
  orderRef: string;
  status: "PENDING_PAYMENT" | "PAID" | "PAYMENT_FAILED" | "CANCELLED";
  deliveryStatus: string;
  paymentMethod: string;
  totalAmount: number;
  customer: { fullName: string; email: string | null; phone: string | null };
  shippingAddress: { receiverName: string; phone: string; addressLine: string; district: string | null; city: string };
  items: Array<{ productId: number; productName: string; variantId: number; unitPrice: number; quantity: number; lineTotal: number }>;
  createdAt: string;
};
export type PaymentResponse = { id: number; orderId: number; method: string; status: string; amount: number; paidAt: string | null; failedReason: string | null; createdAt: string; orderStatus: string };
export type CancellationInfo = { reason: string; cancelledAt: string; actorKind: 'CUSTOMER' | 'STAFF'; paymentOutcome: 'NOT_CHARGED' | 'SIMULATED_REFUND'; refundAmount: number };

export const API_ENDPOINTS = {
  session: "/api/auth/session",
  login: "/api/auth/login",
  register: "/api/auth/register",
  logout: "/api/auth/logout",
  employees: "/api/employees",
  products: "/products",
  product: (id: number) => `/products/${id}`,
  categories: "/categories",
  category: (id: number) => `/categories/${id}`,
  dashboard: "/dashboard/summary",
  orders: "/orders",
  order: (id: number) => `/orders/${id}`,
  payment: (orderRef: string) => `/orders/${encodeURIComponent(orderRef)}/payments`,
  inventory: "/inventory",
  adjustment: (variantId: number) => `/inventory/variants/${variantId}/adjustments`,
  threshold: (productId: number) => `/inventory/products/${productId}/threshold`,
  receivings: "/receivings",
  deliveries: "/deliveries",
  delivery: (orderId: number) => `/orders/${orderId}/delivery`,
  returns: "/returns",
  returnableOrders: "/returns/orders",
  return: (id: number) => `/returns/${id}`,
} as const;

function queryString(query: PageQuery) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.query?.trim()) params.set("q", query.query.trim());
  if (query.categoryId !== undefined)
    params.set("categoryId", String(query.categoryId));
  if (query.minPrice !== undefined)
    params.set("minPrice", String(query.minPrice));
  if (query.maxPrice !== undefined)
    params.set("maxPrice", String(query.maxPrice));
  if (query.sort) params.set("sort", query.sort);
  if (query.rating !== undefined) params.set("rating", String(query.rating));
  if (query.inStock !== undefined) params.set("inStock", String(query.inStock));
  Object.entries(query.specifications || {}).forEach(([key, value]) => {
    if (value) params.set(key === 'instrumentType' ? key : `spec.${key}`, value);
  });
  if (query.status) params.set('status', query.status);
  return params.toString();
}

export interface ShopflowApi {
  getAddresses(): Promise<SavedAddress[]>;
  saveAddress(id: number | null, input: { shippingAddress: ShippingAddress; makeDefault: boolean }): Promise<SavedAddress>;
  deleteAddress(id: number): Promise<void>;
  getCheckoutVouchers(): Promise<CheckoutVoucher[]>;
  quoteCheckout(items: CheckoutRequest['items'], voucherCode?: string | null): Promise<CheckoutQuote>;
  placeCheckout(input: CheckoutRequest): Promise<OrderResponse>;
  getReviews(productId: number, page?: number): Promise<ApiPage<ProductReview>>;
  getReviewEligibility(productId: number): Promise<ReviewEligibility>;
  saveReview(productId: number, input: { rating: number; comment: string }): Promise<void>;
  getProfile(): Promise<AccountProfile>;
  updateProfile(input: { displayName: string; phone: string | null }): Promise<AccountProfile>;
  changePassword(input: { currentPassword: string; newPassword: string }): Promise<void>;
  getEmployees(query: Pick<PageQuery, "page" | "size" | "query">): Promise<ApiPage<AccountProfile>>;
  updateEmployee(id: number, input: { displayName: string; phone: string | null; role: "SALES_STAFF" | "WAREHOUSE"; active: boolean }): Promise<AccountProfile>;
  getMyOrders(query: Pick<PageQuery, "page" | "size" | "query" | "status">): Promise<ApiPage<OrderSummary>>;
  cancelMyOrder(id: number, reason: string): Promise<OrderResponse>;
  cancelOrder(id: number, reason: string): Promise<OrderResponse>;
  getOrder(id: number): Promise<OrderResponse>;
  getMyOrder(id: number): Promise<MyOrderDetail>;
  createMyReturn(orderId: number, input: { reason: string; items: Array<{ orderItemId: number; quantity: number }> }): Promise<ApiReturnRequest>;
  getSession(): Promise<SessionResponse>;
  login(credentials: { email: string; password: string }): Promise<SessionUser>;
  register(input: {
    email: string;
    password: string;
    displayName: string;
  }): Promise<SessionUser>;
  logout(): Promise<void>;
  createEmployee(input: CreateEmployeeRequest): Promise<SessionUser>;
  getProducts(query: PageQuery): Promise<ApiPage<ProductListItem>>;
  createProduct(input: ProductCreateRequest): Promise<ProductDetail>;
  updateProductContent(id: number, input: { description: string | null; usageInstructions: string | null; imageUrls: string[]; specifications?: Record<string, string> }): Promise<ProductDetail>;
  getProduct(id: number): Promise<ProductDetail>;
  getCategories(query: Pick<PageQuery, "page" | "size">): Promise<ApiPage<Category>>;
  createCategory(input: CategoryInput): Promise<Category>;
  updateCategory(id: number, input: CategoryInput): Promise<Category>;
  deleteCategory(id: number): Promise<void>;
  getDashboard(range: DashboardSummary["range"]): Promise<DashboardSummary>;
  getOrders(query: Pick<PageQuery, "page" | "size" | "query" | "status">): Promise<ApiPage<OrderSummary>>;
  createOrder(input: CreateOrderRequest): Promise<OrderResponse>;
  simulatePayment(orderRef: string, result: "SUCCESS" | "FAILED" | "EXPIRED", failureReason?: string): Promise<PaymentResponse>;
  getInventory(): Promise<InventoryItem[]>;
  adjustInventory(variantId: number, delta: number, reason: string): Promise<InventoryItem>;
  updateThreshold(productId: number, lowStockThreshold: number): Promise<void>;
  listReceivings(limit?: number): Promise<ReceivingResponse[]>;
  receiveStock(input: { variantId: number; quantity: number; unitCost: number; supplierName?: string; note?: string }): Promise<ReceivingResponse>;
  getDeliveries(): Promise<DeliveryOrder[]>;
  updateDelivery(orderId: number, toStatus: DeliveryOrder["deliveryStatus"]): Promise<DeliveryOrder>;
  getReturns(limit?: number): Promise<ApiReturnRequest[]>;
  getReturnableOrders(): Promise<ReturnableOrder[]>;
  createReturn(input: { orderId: number; reason?: string | null; items: Array<{ orderItemId: number; quantity: number }> }): Promise<ApiReturnRequest>;
  updateReturn(id: number, toStatus: ApiReturnRequest["status"], restockable?: boolean): Promise<ApiReturnRequest>;
}

export const shopflowApi: ShopflowApi = {
  cancelMyOrder: (id, reason) => apiRequest<OrderResponse>(`/api/account/orders/${id}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }),
  cancelOrder: (id, reason) => apiRequest<OrderResponse>(`/orders/${id}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }),
  getOrder: id => apiRequest<OrderResponse>(`/orders/${id}`),
  getAddresses: () => apiRequest<SavedAddress[]>('/api/account/addresses'),
  saveAddress: (id, input) => apiRequest<SavedAddress>(`/api/account/addresses${id == null ? '' : '/' + id}`, { method: id == null ? 'POST' : 'PUT', body: JSON.stringify(input) }),
  deleteAddress: id => apiRequest<void>(`/api/account/addresses/${id}`, { method: 'DELETE' }),
  getCheckoutVouchers: () => apiRequest<CheckoutVoucher[]>('/api/checkout/vouchers'),
  quoteCheckout: (items, voucherCode) => apiRequest<CheckoutQuote>('/api/checkout/quote', { method: 'POST', body: JSON.stringify({ items, voucherCode }) }),
  placeCheckout: input => apiRequest<OrderResponse>('/api/checkout/orders', { method: 'POST', body: JSON.stringify(input) }),
  updateProductContent: (id, input) => apiRequest<ProductDetail>(`/products/${id}/content`, { method: "PUT", body: JSON.stringify(input) }),
  getReviews: (id, page = 0) => apiRequest<ApiPage<ProductReview>>(`/products/${id}/reviews?page=${page}&size=6`),
  getReviewEligibility: (id) => apiRequest<ReviewEligibility>(`/products/${id}/review-eligibility`),
  saveReview: (id, input) => apiRequest<void>(`/products/${id}/reviews`, { method: "PUT", body: JSON.stringify(input) }),
  getProfile: () => apiRequest<AccountProfile>("/api/account"),
  updateProfile: (input) => apiRequest<AccountProfile>("/api/account", { method: "PUT", body: JSON.stringify(input) }),
  changePassword: (input) => apiRequest<void>("/api/account/password", { method: "PUT", body: JSON.stringify(input) }),
  getEmployees: (query) => apiRequest<ApiPage<AccountProfile>>(`/api/employees?${queryString(query)}`),
  updateEmployee: (id, input) => apiRequest<AccountProfile>(`/api/employees/${id}`, { method: "PATCH", body: JSON.stringify(input) }),
  getMyOrders: (query) => apiRequest<ApiPage<OrderSummary>>(`/api/account/orders?${queryString(query)}`),
  getMyOrder: (id) => apiRequest<MyOrderDetail>(`/api/account/orders/${id}`),
  createMyReturn: (id, input) => apiRequest<ApiReturnRequest>(`/api/account/orders/${id}/returns`, { method: "POST", body: JSON.stringify(input) }),
  getSession: () => apiRequest<SessionResponse>(API_ENDPOINTS.session),
  login: (credentials) =>
    apiRequest<SessionUser>(API_ENDPOINTS.login, {
      method: "POST",
      body: JSON.stringify(credentials),
    }),
  register: (input) =>
    apiRequest<SessionUser>(API_ENDPOINTS.register, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  logout: () => apiRequest<void>(API_ENDPOINTS.logout, { method: "POST" }),
  createEmployee: (input) =>
    apiRequest<SessionUser>(API_ENDPOINTS.employees, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  getProducts: (query) =>
    apiRequest<ApiPage<ProductListItem>>(
      `${API_ENDPOINTS.products}?${queryString(query)}`,
    ),
  createProduct: (input) =>
    apiRequest<ProductDetail>(API_ENDPOINTS.products, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  getProduct: (id) =>
    apiRequest<ProductDetail>(API_ENDPOINTS.product(id)),
  getCategories: (query) =>
    apiRequest<ApiPage<Category>>(
      `${API_ENDPOINTS.categories}?${queryString(query)}`,
    ),
  createCategory: (input) =>
    apiRequest<Category>(API_ENDPOINTS.categories, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateCategory: (id, input) =>
    apiRequest<Category>(API_ENDPOINTS.category(id), {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  deleteCategory: (id) =>
    apiRequest<void>(API_ENDPOINTS.category(id), { method: "DELETE" }),
  getDashboard: (range) => apiRequest<DashboardSummary>(`${API_ENDPOINTS.dashboard}?range=${range}`),
  getOrders: (query) => apiRequest<ApiPage<OrderSummary>>(`${API_ENDPOINTS.orders}?${queryString(query)}`),
  createOrder: (input) => apiRequest<OrderResponse>(API_ENDPOINTS.orders, { method: "POST", body: JSON.stringify(input) }),
  simulatePayment: (orderRef, result, failureReason) => apiRequest<PaymentResponse>(API_ENDPOINTS.payment(orderRef), {
    method: "POST", body: JSON.stringify({ result, ...(failureReason ? { failureReason } : {}) }),
  }),
  getInventory: () => apiRequest<InventoryItem[]>(API_ENDPOINTS.inventory),
  adjustInventory: (variantId, delta, reason) => apiRequest<InventoryItem>(API_ENDPOINTS.adjustment(variantId), {
    method: "POST", body: JSON.stringify({ delta, reason }),
  }),
  updateThreshold: (productId, lowStockThreshold) => apiRequest<void>(API_ENDPOINTS.threshold(productId), {
    method: "PUT", body: JSON.stringify({ lowStockThreshold }),
  }),
  listReceivings: (limit = 50) => apiRequest<ReceivingResponse[]>(`${API_ENDPOINTS.receivings}?limit=${limit}`),
  receiveStock: (input) => apiRequest<ReceivingResponse>(API_ENDPOINTS.receivings, { method: "POST", body: JSON.stringify(input) }),
  getDeliveries: () => apiRequest<DeliveryOrder[]>(API_ENDPOINTS.deliveries),
  updateDelivery: (orderId, toStatus) => apiRequest<DeliveryOrder>(API_ENDPOINTS.delivery(orderId), {
    method: "PATCH", body: JSON.stringify({ toStatus }),
  }),
  getReturns: (limit = 50) => apiRequest<ApiReturnRequest[]>(`${API_ENDPOINTS.returns}?limit=${limit}`),
  getReturnableOrders: () => apiRequest<ReturnableOrder[]>(API_ENDPOINTS.returnableOrders),
  createReturn: (input) => apiRequest<ApiReturnRequest>(API_ENDPOINTS.returns, { method: "POST", body: JSON.stringify(input) }),
  updateReturn: (id, toStatus, restockable) => apiRequest<ApiReturnRequest>(API_ENDPOINTS.return(id), {
    method: "PATCH", body: JSON.stringify({ toStatus, ...(restockable === undefined ? {} : { restockable }) }),
  }),
};

export function paginate<T>(items: T[], query: PageQuery) {
  const size = Math.max(1, Math.min(100, Math.floor(query.size) || 10));
  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const page = Math.max(1, Math.min(totalPages, Math.floor(query.page) || 1));
  const start = (page - 1) * size;
  return { items: items.slice(start, start + size), total, page, size, totalPages };
}

export type ProductReview = { id: number; displayName: string; rating: number; comment: string; updatedAt: string; verifiedPurchase: boolean };
export type ReviewEligibility = { canReview: boolean; myReview: { rating: number; comment: string } | null; reason: string | null };
