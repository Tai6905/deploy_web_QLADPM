import type {
  ApiReturnRequest,
  DeliveryOrder,
  InventoryItem,
  OrderSummary,
  ReceivingResponse,
} from "./shopflowApi";
import type { Order, Product, ReturnRequest } from "../data";

/** Keeps backend enum and identifier details out of the presentation templates. */
export function toOrderView(order: OrderSummary): Order {
  const deliveryStatus = order.deliveryStatus;
  const status: Order["status"] =
    order.status === "PAYMENT_FAILED"
      ? "Đã hủy"
      : order.status === "PENDING_PAYMENT" && order.paymentMethod !== 'COD'
        ? "Chờ xác nhận"
        : deliveryStatus === "DELIVERED"
          ? "Hoàn tất"
          : deliveryStatus === "SHIPPED"
            ? "Đang giao"
            : "Đang xử lý";
  return {
    id: `SF-${order.id}`,
    apiId: order.id,
    orderRef: order.orderRef,
    paymentMethod: order.paymentMethod,
    customer: order.customer,
    date: new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(order.createdAt)),
    total: order.totalAmount,
    status,
    payment: order.paymentStatus === "SUCCESS" ? "Đã thanh toán" : "Chờ thanh toán",
    product: order.product,
    quantity: order.quantity,
    deliveryStatus,
  };
}

export function toInventoryProduct(item: InventoryItem): Product {
  return {
    id: item.variantId,
    variantId: item.variantId,
    categoryId: item.productId,
    name: item.productName,
    sku: item.color || item.size ? [item.color, item.size].filter(Boolean).join("-") : `VAR-${item.variantId}`,
    category: "Tồn kho",
    price: 0,
    stock: item.availableStock,
    threshold: item.lowStockThreshold,
    color: "sage",
    image: "",
  };
}

export function toReceivingProduct(receipt: ReceivingResponse): Product {
  return {
    id: receipt.id,
    variantId: receipt.variantId,
    categoryId: receipt.productId,
    name: receipt.productName,
    sku: `VAR-${receipt.variantId}`,
    category: "Nhập hàng",
    price: receipt.unitCost ?? 0,
    unitCost: receipt.unitCost,
    totalCost: receipt.totalCost,
    receivedQuantity: receipt.quantity,
    supplierName: receipt.supplierName,
    receivedAt: receipt.createdAt,
    stock: receipt.availableStock,
    threshold: 0,
    color: "sage",
    image: "",
  };
}

export function toDeliveryOrder(delivery: DeliveryOrder): Order {
  const deliveryStatus = delivery.deliveryStatus;
  const status: Order["status"] =
    deliveryStatus === "DELIVERED"
      ? "Hoàn tất"
      : deliveryStatus === "SHIPPED"
        ? "Đang giao"
        : "Đang xử lý";
  return {
    id: `SF-${delivery.orderId}`,
    apiId: delivery.orderId,
    customer: delivery.receiverName,
    date: new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(delivery.createdAt)),
    total: delivery.totalAmount,
    status,
    payment: delivery.orderStatus === 'PENDING_PAYMENT' ? 'Chờ thanh toán' : 'Đã thanh toán',
    paymentMethod: delivery.paymentMethod,
    product: delivery.items.map((item) => item.productName).join(", "),
    quantity: delivery.items.reduce((total, item) => total + item.quantity, 0),
    deliveryStatus,
  };
}

export function toReturnView(item: ApiReturnRequest): ReturnRequest {
  const status: ReturnRequest["status"] =
    item.status === "REQUESTED"
      ? "Chờ xử lý"
      : item.status === "APPROVED"
        ? "Đã chấp nhận"
        : item.status === "RESTOCKED"
          ? "Đã nhập kho"
          : "Đã từ chối";
  return {
    id: `RT-${item.id}`,
    apiId: item.id,
    order: `SF-${item.orderId}`,
    orderId: item.orderId,
    customer: "Khách hàng",
    reason: item.reason || item.items.map((line) => line.productName).join(", "),
    status,
    restockable: item.restockable,
  };
}
