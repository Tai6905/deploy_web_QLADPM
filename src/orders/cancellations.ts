export type CancellableOrder = { status: string; deliveryStatus: string; paymentMethod?: string };
export function canCancelOrder(order: CancellableOrder | null | undefined) {
  return !!order && ['PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED'].includes(order.status)
    && ['NONE', 'PREPARING'].includes(order.deliveryStatus)
    && !(order.status === 'PAID' && order.paymentMethod === 'COD');
}
export function cancellationBlockReason(order: CancellableOrder) {
  if (order.status === 'CANCELLED') return 'Đơn hàng này đã được hủy.';
  if (order.deliveryStatus === 'DELIVERED') return 'Đơn đã giao thành công. Bạn có thể yêu cầu đổi trả trong trang Đơn mua.';
  if (order.deliveryStatus === 'SHIPPED') return 'Đơn đã bàn giao vận chuyển nên không thể hủy trực tuyến. Vui lòng liên hệ cửa hàng.';
  return 'Trạng thái đơn hiện tại chưa cho phép hủy. Vui lòng liên hệ cửa hàng.';
}
export const cancellationReasons = ['Muốn đổi sang sản phẩm khác', 'Nhập sai thông tin nhận hàng', 'Đặt trùng đơn', 'Không còn nhu cầu mua'];
