import { apiRequest } from "./httpClient";

export type UserRole = "CUSTOMER" | "WAREHOUSE" | "SHOP_OWNER";
export type PageQuery = { page: number; size: number; query?: string };
export type PageResult<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
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
};
export type ProductSummary = {
  id: number;
  name: string;
  price: number;
  stockStatus: "IN_STOCK" | "OUT_OF_STOCK";
};
export type ProductDetail = ProductSummary & { description: string | null };
export type InventoryItem = {
  productId: number;
  productName: string;
  onHandStock: number;
  reservedStock: number;
  availableStock: number;
  lowStockThreshold: number | null;
  lowStock: boolean;
};
export type DeliveryStatus = "NONE" | "PREPARING" | "SHIPPED" | "DELIVERED";
export type ReturnStatus = "REQUESTED" | "APPROVED" | "RESTOCKED" | "REJECTED";
export type DeliveryOrder = {
  orderId: number;
  orderStatus: "PAID";
  deliveryStatus: DeliveryStatus;
  receiverName: string;
  city: string;
  totalAmount: number;
  createdAt: string;
  items: Array<{ productId: number; productName: string; quantity: number }>;
  history: Array<{
    fromStatus: DeliveryStatus;
    toStatus: DeliveryStatus;
    changedAt: string;
    changedBy: string | null;
  }>;
};
export type ApiReturnRequest = {
  id: number;
  orderId: number;
  status: ReturnStatus;
  reason: string | null;
  restockable: boolean;
  createdAt: string;
  updatedAt: string;
  items: Array<{
    orderItemId: number;
    productId: number;
    productName: string;
    quantity: number;
  }>;
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
    productName: string;
    quantity: number;
    returnedQuantity: number;
    returnableQuantity: number;
  }>;
};
export type CreateReturnRequest = {
  orderId: number;
  reason: string | null;
  items: Array<{ orderItemId: number; quantity: number }>;
};
export type ReceivingResponse = {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  supplierName: string | null;
  note: string | null;
  createdAt: string;
  createdBy: string | null;
  onHandStock: number;
  reservedStock: number;
  availableStock: number;
};
export type PaymentResponse = {
  id: number;
  orderId: number;
  method: "CARD";
  status: "SUCCESS" | "FAILED" | "EXPIRED";
  amount: number;
  paidAt: string | null;
  failedReason: string | null;
  createdAt: string;
  orderStatus: string;
};

export interface ShopflowApi {
  getSession(): Promise<SessionResponse>;
  login(credentials: {
    username: string;
    password: string;
  }): Promise<SessionUser>;
  logout(): Promise<void>;
  getProducts(): Promise<ProductSummary[]>;
  getProduct(id: number): Promise<ProductDetail>;
  createOrder(input: CreateOrderRequest): Promise<OrderResponse>;
  simulatePayment(
    orderRef: string,
    result: "SUCCESS" | "FAILED" | "EXPIRED",
    failureReason?: string,
  ): Promise<PaymentResponse>;
  getInventory(): Promise<InventoryItem[]>;
  adjustInventory(
    productId: number,
    delta: number,
    reason: string,
  ): Promise<InventoryItem>;
  updateThreshold(
    productId: number,
    lowStockThreshold: number | null,
  ): Promise<InventoryItem>;
  receiveStock(input: {
    productId: number;
    quantity: number;
    supplierName?: string | null;
    note?: string | null;
  }): Promise<ReceivingResponse>;
  getDeliveries(): Promise<DeliveryOrder[]>;
  updateDelivery(
    orderId: number,
    toStatus: DeliveryStatus,
  ): Promise<DeliveryOrder>;
  getReturns(): Promise<ApiReturnRequest[]>;
  getReturnableOrders(): Promise<ReturnableOrder[]>;
  createReturn(input: CreateReturnRequest): Promise<ApiReturnRequest>;
  updateReturn(
    returnId: number,
    toStatus: ReturnStatus,
    restockable?: boolean,
  ): Promise<ApiReturnRequest>;
}

export const API_ENDPOINTS = {
  session: "/auth/session",
  login: "/auth/login",
  logout: "/auth/logout",
  products: "/products",
  orders: "/orders",
  payments: (orderRef: string) =>
    `/orders/${encodeURIComponent(orderRef)}/payments`,
  inventory: "/inventory",
  adjustment: (productId: number) => `/inventory/${productId}/adjustments`,
  threshold: (productId: number) => `/inventory/${productId}/threshold`,
  receiving: "/receivings",
  deliveries: "/deliveries",
  delivery: (orderId: number) => `/orders/${orderId}/delivery`,
  returns: "/returns",
  returnableOrders: "/returns/orders",
  return: (returnId: number) => `/returns/${returnId}`,
} as const;

export const shopflowApi: ShopflowApi = {
  getSession: () => apiRequest<SessionResponse>(API_ENDPOINTS.session),
  login: (credentials: { username: string; password: string }) =>
    apiRequest<SessionUser>(API_ENDPOINTS.login, {
      method: "POST",
      body: JSON.stringify(credentials),
    }),
  logout: () => apiRequest<void>(API_ENDPOINTS.logout, { method: "POST" }),
  getProducts: () => apiRequest<ProductSummary[]>(API_ENDPOINTS.products),
  getProduct: (id: number) =>
    apiRequest<ProductDetail>(`${API_ENDPOINTS.products}/${id}`),
  createOrder: (input: CreateOrderRequest) =>
    apiRequest<OrderResponse>(API_ENDPOINTS.orders, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  simulatePayment: (
    orderRef: string,
    result: "SUCCESS" | "FAILED" | "EXPIRED",
    failureReason?: string,
  ) =>
    apiRequest<PaymentResponse>(API_ENDPOINTS.payments(orderRef), {
      method: "POST",
      body: JSON.stringify({
        result,
        ...(failureReason ? { failureReason } : {}),
      }),
    }),
  getInventory: () => apiRequest<InventoryItem[]>(API_ENDPOINTS.inventory),
  adjustInventory: (productId: number, delta: number, reason: string) =>
    apiRequest<InventoryItem>(API_ENDPOINTS.adjustment(productId), {
      method: "POST",
      body: JSON.stringify({ delta, reason }),
    }),
  updateThreshold: (productId: number, lowStockThreshold: number | null) =>
    apiRequest<InventoryItem>(API_ENDPOINTS.threshold(productId), {
      method: "PUT",
      body: JSON.stringify({ lowStockThreshold }),
    }),
  receiveStock: (input: {
    productId: number;
    quantity: number;
    supplierName?: string | null;
    note?: string | null;
  }) =>
    apiRequest<ReceivingResponse>(API_ENDPOINTS.receiving, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  getDeliveries: () => apiRequest<DeliveryOrder[]>(API_ENDPOINTS.deliveries),
  updateDelivery: (orderId: number, toStatus: DeliveryStatus) =>
    apiRequest<DeliveryOrder>(API_ENDPOINTS.delivery(orderId), {
      method: "PATCH",
      body: JSON.stringify({ toStatus }),
    }),
  getReturns: () => apiRequest<ApiReturnRequest[]>(API_ENDPOINTS.returns),
  getReturnableOrders: () =>
    apiRequest<ReturnableOrder[]>(API_ENDPOINTS.returnableOrders),
  createReturn: (input: CreateReturnRequest) =>
    apiRequest<ApiReturnRequest>(API_ENDPOINTS.returns, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  updateReturn: (
    returnId: number,
    toStatus: ReturnStatus,
    restockable?: boolean,
  ) =>
    apiRequest<ApiReturnRequest>(API_ENDPOINTS.return(returnId), {
      method: "PATCH",
      body: JSON.stringify({
        toStatus,
        ...(restockable === undefined ? {} : { restockable }),
      }),
    }),
};

export type CreateOrderRequest = {
  customer: { fullName: string; email?: string | null; phone?: string | null };
  shippingAddress: {
    receiverName: string;
    phone: string;
    addressLine: string;
    district?: string | null;
    city: string;
  };
  paymentMethod: "CARD";
  items: Array<{ productId: number; quantity: number }>;
};

export type OrderResponse = {
  id: number;
  orderRef: string;
  status: "PENDING_PAYMENT";
  deliveryStatus: "NONE";
  paymentMethod: "CARD";
  totalAmount: number;
  createdAt: string;
};

export function paginate<T>(items: T[], query: PageQuery): PageResult<T> {
  const size = Math.max(1, Math.min(100, Math.floor(query.size) || 10));
  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const page = Math.max(1, Math.min(totalPages, Math.floor(query.page) || 1));
  const start = (page - 1) * size;
  return {
    items: items.slice(start, start + size),
    total,
    page,
    size,
    totalPages,
  };
}
