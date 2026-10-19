<script setup lang="ts">
import { ArrowRight, Download, Search, Truck } from "lucide-vue-next";
import type { Order } from "../data";
import PaginationBar from "../components/PaginationBar.vue";

type PageState = {
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

defineProps<{
  page: "orders" | "delivery";
  orders: Order[];
  filteredOrders: Order[];
  orderPage: PageState;
  pendingOrders: number;
  canManageOrders: boolean;
  search: string;
  money: (amount: number) => string;
  statusClass: (status: string) => string;
}>();

const emit = defineEmits<{
  "update:search": [value: string];
  notify: [message: string];
  "navigate-orders": [];
  "export-orders": [];
  "advance-order": [order: Order];
  "change-page": [page: number];
  "change-size": [size: number];
}>();
</script>

<template>
  <section class="panel data-panel">
    <div class="table-toolbar">
      <div class="toolbar-tabs">
        <button
          :class="{ selected: page === 'orders' }"
          :disabled="page !== 'orders' && !canManageOrders"
          @click="page !== 'orders' && emit('navigate-orders')"
        >
          {{ canManageOrders ? 'Tất cả đơn hàng' : 'Đơn giao hàng' }} <span>{{ orders.length }}</span></button
        ><button
          class="tab-button"
          @click="emit('notify', `${pendingOrders} đơn hàng đang chờ xác nhận`)"
        >
          Chờ xử lý <span class="alert-count">{{ pendingOrders }}</span>
        </button>
      </div>
      <div class="toolbar-actions">
        <label class="table-search"
          ><Search :size="16" /><input
            :value="search"
            type="search"
            placeholder="Tìm mã đơn, khách hàng"
            @input="emit('update:search', ($event.target as HTMLInputElement).value)"
        /></label>
        <button
          class="button button-outline"
          @click="emit('export-orders')"
        >
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
                :class="{ 'payment-label--paid': order.payment === 'Đã thanh toán' }"
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
                  order.status === 'Hoàn tất' || order.status === 'Đã hủy' || order.payment !== 'Đã thanh toán'
                "
                @click="emit('advance-order', order)"
              >
                {{ page === "delivery" ? "Cập nhật" : "Tiếp tục" }}
                <ArrowRight :size="13" />
              </button>
            </td>
          </tr>
          <tr v-if="!filteredOrders.length">
            <td colspan="8" class="empty-cell">Không tìm thấy đơn hàng.</td>
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
        @change-page="emit('change-page', $event)"
        @change-size="emit('change-size', $event)"
      />
    </div>
  </section>
  <div v-if="page === 'delivery'" class="delivery-hint">
    <Truck :size="18" /><span
      >Chọn <strong>Cập nhật</strong> để chuyển đơn sang bước giao hàng tiếp
      theo.</span
    >
  </div>
</template>
