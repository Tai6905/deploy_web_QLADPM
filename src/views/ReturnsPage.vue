<script setup lang="ts">
import { ArrowRight, Check, PackageCheck, RotateCcw, Search, X } from "lucide-vue-next";
import type { ReturnRequest } from "../data";
import PaginationBar from "../components/PaginationBar.vue";

type PageState = {
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

defineProps<{
  returns: ReturnRequest[];
  filteredReturns: ReturnRequest[];
  returnPage: PageState;
  search: string;
  canApprove: boolean;
  canRestock: boolean;
  statusClass: (status: string) => string;
}>();

const emit = defineEmits<{
  "update:search": [value: string];
  notify: [message: string];
  "set-status": [request: ReturnRequest, status: ReturnRequest["status"]];
  "change-page": [page: number];
  "change-size": [size: number];
}>();
</script>

<template>
  <section class="panel data-panel returns-panel">
    <div class="table-toolbar">
      <div class="toolbar-tabs">
        <button class="selected">
          Yêu cầu đổi trả <span>{{ returns.length }}</span></button
        ><button
          class="tab-button"
          @click="
            emit(
              'notify',
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
          :value="search"
          type="search"
          placeholder="Tìm yêu cầu"
          @input="emit('update:search', ($event.target as HTMLInputElement).value)"
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
                v-if="request.status === 'Chờ xử lý' && canApprove"
                class="return-actions"
              >
                <button
                  class="button button-small button-primary"
                  @click="emit('set-status', request, 'Đã chấp nhận')"
                >
                  <Check :size="14" />Chấp nhận</button
                ><button
                  class="icon-button small-icon reject-button"
                  aria-label="Từ chối yêu cầu"
                  @click="emit('set-status', request, 'Đã từ chối')"
                >
                  <X :size="16" />
                </button>
              </div>
              <button
                v-else-if="request.status === 'Đã chấp nhận' && request.restockable !== false && canRestock"
                class="button button-small button-outline"
                @click="emit('set-status', request, 'Đã nhập kho')"
              >
                <PackageCheck :size="14" />Nhập lại kho
              </button>
              <span
                v-else-if="request.status === 'Chờ xử lý'"
                class="muted-cell"
                >Chờ chủ shop duyệt</span
              >
              <span v-else class="muted-cell">{{
                request.status === "Đã nhập kho" ? "Đã nhập kho" : request.restockable === false ? "Không nhập lại kho" : "Đã xử lý"
              }}</span>
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
        @change-page="emit('change-page', $event)"
        @change-size="emit('change-size', $event)"
      />
    </div>
  </section>
  <div class="return-policy">
    <RotateCcw :size="19" />
    <div>
      <strong>Quy trình đổi trả</strong>
      <p>
        Kiểm tra tình trạng sản phẩm trước khi chấp nhận hoàn hàng. Hàng hoàn
        kho sẽ cần được cập nhật tồn kho riêng.
      </p>
    </div>
    <button
      class="text-link"
      @click="emit('notify', 'Chính sách đổi trả đang được cập nhật')"
    >
      Xem chính sách <ArrowRight :size="15" />
    </button>
  </div>
</template>
