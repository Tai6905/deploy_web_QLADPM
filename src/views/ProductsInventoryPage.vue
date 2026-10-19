<script setup lang="ts">
import { Download, PackagePlus, Plus, Search } from "lucide-vue-next";
import type { Product } from "../data";
import PaginationBar from "../components/PaginationBar.vue";

type CategoryOption = { id: number; name: string };

type PageState = {
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

defineProps<{
  page: "products" | "inventory" | "receiving";
  dataSource: "api" | "demo";
  products: Product[];
  filteredProducts: Product[];
  productPage: PageState;
  lowStockCount: number;
  categoryOptions: CategoryOption[];
  categoryFilter: string;
  sortFilter: "newest" | "priceAsc" | "priceDesc";
  search: string;
  apiProductLoading: boolean;
  apiProductError: string;
  stockSaving: boolean;
  money: (amount: number) => string;
}>();

const emit = defineEmits<{
  "update:search": [value: string];
  "update:category-filter": [value: string];
  "update:sort-filter": [value: "newest" | "priceAsc" | "priceDesc"];
  "navigate-products": [];
  "navigate-inventory": [];
  "open-categories": [];
  "export-products": [];
  "open-detail": [productId: number];
  "open-stock": [productId: number];
  "change-page": [page: number];
  "change-size": [size: number];
  "update-threshold": [productId: number, threshold: number];
}>();
</script>

<template>
  <section class="panel data-panel">
    <div class="table-toolbar">
      <div class="toolbar-tabs">
        <button
          :class="{ selected: page === 'products' }"
          @click="page !== 'products' && emit('navigate-products')"
        >
          Tất cả sản phẩm <span>{{ productPage.total }}</span></button
        ><button
          v-if="page !== 'products'"
          :class="{ selected: page === 'inventory' }"
          @click="emit('navigate-inventory')"
        >
          Cảnh báo tồn kho
          <span class="alert-count">{{ lowStockCount }}</span>
        </button>
      </div>
      <div class="toolbar-actions">
        <label class="table-search"
          ><Search :size="16" /><input
            :value="search"
            type="search"
            placeholder="Tìm sản phẩm"
            @input="emit('update:search', ($event.target as HTMLInputElement).value)"
        /></label>
        <select
          v-if="page === 'products' || dataSource === 'demo'"
          :value="categoryFilter"
          class="filter-select"
          aria-label="Lọc theo danh mục"
          @change="emit('update:category-filter', ($event.target as HTMLSelectElement).value)"
        >
          <option value="all">Tất cả danh mục</option>
          <option
            v-for="category in categoryOptions"
            :key="category.id"
            :value="String(category.id)"
          >
            {{ category.name }}
          </option>
        </select>
        <select
          v-if="dataSource === 'api' && page === 'products'"
          :value="sortFilter"
          class="filter-select"
          aria-label="Sắp xếp sản phẩm"
          @change="emit('update:sort-filter', ($event.target as HTMLSelectElement).value as 'newest' | 'priceAsc' | 'priceDesc')"
        >
          <option value="newest">Mới nhất</option>
          <option value="priceAsc">Giá tăng dần</option>
          <option value="priceDesc">Giá giảm dần</option>
        </select>
        <button
          v-if="dataSource === 'api' && page === 'products'"
          class="button button-outline"
          @click="emit('open-categories')"
        >
          Quản lý danh mục
        </button>
        <button
          class="button button-outline export-button"
          @click="emit('export-products')"
        >
          <Download :size="16" />Xuất file
        </button>
      </div>
    </div>
    <div v-if="apiProductLoading" class="empty-cell">
      Đang tải catalog từ backend…
    </div>
    <div v-else-if="apiProductError" class="api-error-banner" role="alert">
      {{ apiProductError }}
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>SẢN PHẨM</th>
            <th>MÃ SKU</th>
            <th>DANH MỤC</th>
            <th v-if="page === 'products' || dataSource === 'demo'">GIÁ BÁN</th>
            <th>{{ page === "products" ? "TỒN KHO" : "SỐ LƯỢNG CÒN" }}</th>
            <th>TRẠNG THÁI</th>
            <th v-if="page === 'inventory' && dataSource === 'api'">NGƯỠNG CẢNH BÁO</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="product in filteredProducts" :key="product.id">
            <td>
              <div class="product-cell">
                <img v-if="product.image" :src="product.image" :alt="product.name" loading="lazy" />
                <button
                  v-if="dataSource === 'api' && page === 'products'"
                  class="product-detail-link"
                  @click="emit('open-detail', product.id)"
                >
                  {{ product.name }}
                </button>
                <strong v-else>{{ product.name }}</strong>
              </div>
            </td>
            <td class="muted-cell">{{ product.sku }}</td>
            <td><span class="category-label">{{ product.category }}</span></td>
            <td v-if="page === 'products' || dataSource === 'demo'" class="amount-cell">{{ money(product.price) }}</td>
            <td>
              <span
                class="stock-number"
                :class="{ 'stock-number--low': product.stock <= product.threshold }"
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
                  product.stock <= product.threshold ? "Sắp hết hàng" : "Đang bán"
                }}</span
              >
            </td>
            <td v-if="page === 'inventory' && dataSource === 'api'">
              <input class="threshold-input" type="number" min="0" step="1" :value="product.threshold"
                :disabled="stockSaving" :aria-label="`Ngưỡng tồn kho ${product.name}`"
                @change="emit('update-threshold', product.categoryId!, Number(($event.target as HTMLInputElement).value))" />
            </td>
            <td>
              <button
                v-if="dataSource === 'demo' && page !== 'products'"
                class="button button-small button-outline"
                @click="emit('open-stock', product.id)"
              >
                <Plus :size="14" />Nhập kho</button
              ><button
                v-else-if="dataSource === 'demo'"
                class="row-menu"
                :aria-label="`Nhập hàng cho ${product.name}`"
                @click="emit('open-stock', product.id)"
              >
                <PackagePlus :size="17" />
              </button>
              <span v-else class="muted-cell">Chỉ đọc</span>
            </td>
          </tr>
          <tr v-if="!apiProductLoading && !filteredProducts.length">
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
        @change-page="emit('change-page', $event)"
        @change-size="emit('change-size', $event)"
      />
    </div>
  </section>
  <div class="page-footnote">
    <span class="live-dot"></span>{{ dataSource === 'api' ? 'Dữ liệu từ hệ thống cửa hàng' : 'Dữ liệu được lưu trên thiết bị này' }}
    <span>·</span> Đồng bộ gần nhất: vừa xong
  </div>
</template>
