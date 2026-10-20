<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from "vue";
import { Star, ShoppingBag, Check, ChevronLeft, ChevronRight, ZoomIn } from "lucide-vue-next";
import { shopflowApi, type ProductDetail, type ProductReview, type ApiPage, type ReviewEligibility } from "../api/shopflowApi";
import { currentUser } from "../auth/session";
import { money, type Product } from "../data";
import StorefrontNav from "../components/StorefrontNav.vue";
import ProductImage from "../components/ProductImage.vue";
const props = defineProps<{ productId: number; cartQuantity: number }>();
const emit = defineEmits<{ add: [product: Product, quantity: number] }>();
const detail = ref<ProductDetail | null>(null), reviews = ref<ApiPage<ProductReview> | null>(null), eligibility = ref<ReviewEligibility | null>(null);
const loading = ref(false), saving = ref(false), error = ref(""), success = ref(""), selectedImage = ref(0), zoom = ref(false), quantity = ref(1), reviewPage = ref(0);
const reviewForm = reactive({ rating: 5, comment: "" });
const editing = ref(false), savingContent = ref(false);
const contentForm = reactive({ description: '', usageInstructions: '', imageUrls: '' });
const contentImages = computed(() => contentForm.imageUrls.split(/\r?\n/).map(value => value.trim()).filter(Boolean));
function editContent() {
  if (!detail.value) return;
  contentForm.description = detail.value.description || '';
  contentForm.usageInstructions = detail.value.usageInstructions || '';
  contentForm.imageUrls = pictures.value.map(picture => picture.imageUrl).join('\n');
  editing.value = true;
}
async function saveContent() {
  if (savingContent.value || contentImages.value.length > 8) return;
  const productId = props.productId, generation = requestId;
  savingContent.value = true; error.value = '';
  try {
    const product = await shopflowApi.updateProductContent(productId, { description: contentForm.description.trim(), usageInstructions: contentForm.usageInstructions.trim(), imageUrls: contentImages.value });
    if (generation !== requestId) return;
    detail.value = product; selectedImage.value = 0; editing.value = false;
    success.value = 'Đã cập nhật thông tin và ảnh sản phẩm.';
  } catch (cause) { if (generation === requestId) error.value = cause instanceof Error ? cause.message : 'Không lưu được sản phẩm.'; }
  finally { savingContent.value = false; }
}
let requestId = 0, reviewRequestId = 0;
const lightbox = ref<HTMLElement | null>(null);
let previousFocus: HTMLElement | null = null;
let previousOverflow = '';
watch(zoom, async open => {
  if (open) { previousFocus = document.activeElement as HTMLElement; previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; await nextTick(); lightbox.value?.querySelector('button')?.focus(); }
  else { document.body.style.overflow = previousOverflow; if (previousFocus?.isConnected) previousFocus.focus(); }
});
const pictures = computed(() => detail.value?.productImages || []);
const image = computed(() => pictures.value[selectedImage.value]?.imageUrl || "");
const available = computed(() => detail.value?.productVariants.reduce((sum, item) => sum + item.availableQuantity, 0) || 0);
const maxQuantity = computed(() => Math.max(0, available.value - props.cartQuantity));
watch(maxQuantity, value => { quantity.value = Math.max(1, Math.min(quantity.value, value)); });
async function load() {
  const id = ++requestId; ++reviewRequestId; loading.value = true; error.value = ""; success.value = ""; detail.value = null; reviews.value = null; eligibility.value = null;
  selectedImage.value = 0; reviewPage.value = 0; quantity.value = 1; zoom.value = false; editing.value = false;
  try {
    const result = await shopflowApi.getProduct(props.productId);
    if (id !== requestId) return;
    detail.value = result;
    await loadReviews();
    if (currentUser.value) {
      const permission = await shopflowApi.getReviewEligibility(props.productId);
      if (id !== requestId) return;
      eligibility.value = permission;
      reviewForm.rating = permission.myReview?.rating || 5; reviewForm.comment = permission.myReview?.comment || "";
    }
  } catch (cause) { if (id === requestId) error.value = cause instanceof Error ? cause.message : 'Không tải được sản phẩm.'; }
  finally { if (id === requestId) loading.value = false; }
}
async function loadReviews(page = 0) {
  const id = ++reviewRequestId;
  const result = await shopflowApi.getReviews(props.productId, page);
  if (id !== reviewRequestId) return;
  reviews.value = result; reviewPage.value = page;
}
async function changeReviewPage(page: number) {
  try { await loadReviews(page); } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Không tải được đánh giá.'; }
}
async function submitReview() {
  if (saving.value) return;
  saving.value = true; error.value = "";
  const productId = props.productId, generation = requestId;
  try {
    await shopflowApi.saveReview(productId, { rating: reviewForm.rating, comment: reviewForm.comment.trim() });
    if (generation !== requestId) return;
    success.value = 'Đã lưu đánh giá của bạn. Cảm ơn bạn đã chia sẻ!';
    const id = productId;
    const [product, permission] = await Promise.all([shopflowApi.getProduct(id), shopflowApi.getReviewEligibility(id)]);
    if (id !== props.productId) return;
    detail.value = product; eligibility.value = permission; await loadReviews();
  } catch (cause) { if (generation === requestId) error.value = cause instanceof Error ? cause.message : 'Không gửi được đánh giá.'; }
  finally { saving.value = false; }
}
function add() {
  const product = detail.value;
  if (!product || !Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > maxQuantity.value) return;
  emit('add', { id: product.id, categoryId: product.categoryId, name: product.name, sku: product.sku, category: product.categoryName, price: product.listPrice, stock: available.value, threshold: 0, image: pictures.value[0]?.imageUrl || '', color: 'sage' }, quantity.value);
  success.value = `Đã thêm ${quantity.value} sản phẩm vào giỏ. Bạn có thể tiếp tục mua sắm hoặc mở giỏ hàng.`;
}
watch(() => props.productId, load, { immediate: true });
onUnmounted(() => { ++requestId; ++reviewRequestId; if (zoom.value) document.body.style.overflow = previousOverflow; });
</script>
<template>
  <main class="commerce-page"><StorefrontNav v-model:error="error" />
    <div class="commerce-container">
      <nav class="product-breadcrumb"><RouterLink to="/customer">Cửa hàng</RouterLink><span>/</span><span>{{ detail?.categoryName || 'Sản phẩm' }}</span><span>/</span><strong>{{ detail?.name || 'Chi tiết sản phẩm' }}</strong></nav>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p><p v-if="success" class="form-success" role="status">{{ success }} <RouterLink to="/customer#cart">Mở giỏ hàng →</RouterLink></p>
      <p v-if="loading && !detail" class="empty-state motion-loading" role="status">Đang mở sản phẩm…</p>
      <section v-if="detail" class="product-page-grid">
        <div class="product-photo-column">
          <button class="product-main-photo" :disabled="!image" aria-label="Phóng to ảnh sản phẩm" @click="zoom = true"><ProductImage :src="image" :alt="detail.name" loading="eager" /><ZoomIn v-if="image" :size="22" class="zoom-icon" /></button>
          <div v-if="pictures.length > 1" class="product-photo-strip"><button v-for="(picture, index) in pictures" :key="picture.id" :class="{ selected: selectedImage === index }" :aria-label="`Xem ảnh ${index + 1}`" :aria-pressed="selectedImage === index" @click="selectedImage = index"><ProductImage :src="picture.imageUrl" :alt="`${detail.name} · ảnh ${index + 1}`" /></button></div>
        </div>
        <div class="product-purchase-panel"><span class="auth-kicker">{{ detail.categoryName }} · {{ detail.sku }}</span><h1>{{ detail.name }}</h1>
          <a class="product-rating-summary" href="#reviews"><Star :size="17" fill="currentColor" />{{ detail.reviewCount ? detail.averageRating.toFixed(1) : 'Chưa có đánh giá' }}<span>({{ detail.reviewCount }} đánh giá)</span></a>
          <p class="product-page-price">{{ money(detail.listPrice) }}</p><p class="availability" :class="{ empty: !available }"><Check :size="16" />{{ available > 0 ? `Còn ${available} sản phẩm` : 'Tạm hết hàng' }}</p>
          <p class="product-intro">{{ detail.description || 'Thông tin sản phẩm đang được Mộc Studio cập nhật. Xem ảnh và hướng dẫn bên dưới trước khi chọn mua.' }}</p>
          <label class="form-label">Số lượng<input v-model.number="quantity" class="purchase-quantity" type="number" min="1" :max="Math.max(1, maxQuantity)" :disabled="maxQuantity === 0" /></label>
          <div class="product-purchase-actions"><button class="button button-primary" :disabled="maxQuantity === 0 || !Number.isInteger(quantity) || quantity < 1 || quantity > maxQuantity" @click="add"><ShoppingBag :size="18" />Thêm vào giỏ hàng</button><RouterLink class="button button-outline" to="/customer#cart">Xem giỏ hàng →</RouterLink></div>
          <p class="muted-cell">{{ cartQuantity ? `Bạn đã có ${cartQuantity} sản phẩm này trong giỏ.` : 'Giá niêm yết bằng VND. Tồn kho được kiểm tra lại khi đặt hàng.' }}</p>
          <div class="product-benefits"><span>✓ Theo dõi đơn trực tuyến</span><span>✓ Đánh giá từ khách đã nhận hàng</span><span>✓ Gửi yêu cầu đổi trả từ đơn mua</span></div>
          <button v-if="currentUser?.role === 'SHOP_OWNER' && !editing" class="button button-outline" @click="editContent">Chỉnh sửa ảnh & nội dung</button>
        </div>
      </section>
      <form v-if="detail && editing && currentUser?.role === 'SHOP_OWNER'" class="story-card product-content-editor" @submit.prevent="saveContent">
        <h2>Cập nhật sản phẩm</h2>
        <label class="form-label">Giới thiệu sản phẩm<textarea v-model="contentForm.description" maxlength="5000" rows="4" /></label>
        <label class="form-label">Hướng dẫn sử dụng & bảo quản<textarea v-model="contentForm.usageInstructions" maxlength="5000" rows="4" /></label>
        <label class="form-label">Đường link hình ảnh<textarea v-model="contentForm.imageUrls" rows="4" placeholder="Mỗi dòng một URL HTTP/HTTPS, tối đa 8 ảnh. Ảnh đầu là ảnh đại diện." /></label>
        <p class="muted-cell">Để trống danh sách để gỡ ảnh hiện tại. Ưu tiên link HTTPS trỏ trực tiếp đến hình ảnh.</p>
        <p v-if="contentImages.length > 8" class="form-error">Chỉ được sử dụng tối đa 8 ảnh.</p>
        <div class="image-url-previews"><ProductImage v-for="(url, index) in contentImages.slice(0, 8)" :key="url + index" :src="url" :alt="`Ảnh xem trước ${index + 1}`" /></div>
        <div class="account-actions"><button class="button button-primary" :disabled="savingContent || contentImages.length > 8">{{ savingContent ? 'Đang lưu…' : 'Lưu thay đổi' }}</button><button type="button" class="button button-outline" :disabled="savingContent" @click="editing = false">Hủy</button></div>
      </form>
      <section v-if="detail" class="product-information-grid"><article v-reveal class="story-card"><span>THÔNG TIN SẢN PHẨM</span><h2>Được chọn cho ngày thường</h2><p class="preserve-lines">{{ detail.description || 'Chưa có mô tả chi tiết. Vui lòng xem lại sau khi cửa hàng cập nhật.' }}</p><dl><dt>Thương hiệu cửa hàng</dt><dd>Mộc Studio</dd><dt>Mã sản phẩm</dt><dd>{{ detail.sku }}</dd><dt>Danh mục</dt><dd>{{ detail.categoryName }}</dd></dl></article><article v-reveal="1" class="story-card"><span>SỬ DỤNG & BẢO QUẢN</span><h2>Giữ món đồ luôn như ý</h2><p class="preserve-lines">{{ detail.usageInstructions || 'Cửa hàng chưa cập nhật hướng dẫn riêng cho sản phẩm này. Hãy làm theo nhãn và hướng dẫn của nhà sản xuất trước khi dùng.' }}</p><RouterLink to="/guide" class="text-link">Xem cẩm nang mua sắm →</RouterLink></article></section>
      <section v-if="detail" id="reviews" class="product-reviews-section"><div class="account-section-heading"><div><span class="auth-kicker">TRẢI NGHIỆM THẬT</span><h2>Khách hàng nói gì?</h2></div><strong>{{ detail.reviewCount ? `${detail.averageRating.toFixed(1)} / 5` : 'Chưa có đánh giá' }}</strong></div>
        <div class="reviews-layout"><div><article v-for="review in reviews?.items || []" :key="review.id" class="review-card"><div><strong>{{ review.displayName }}</strong><span class="review-stars" :aria-label="`${review.rating} trên 5 sao`">{{ '★'.repeat(review.rating) }}{{ '☆'.repeat(5 - review.rating) }}</span></div><small>✓ Đã mua và nhận hàng · {{ new Date(review.updatedAt).toLocaleDateString('vi-VN') }}</small><p class="preserve-lines">{{ review.comment }}</p></article><p v-if="reviews && !reviews.totalItems" class="empty-state">Sản phẩm chưa có đánh giá. Trải nghiệm của bạn sẽ giúp người mua sau chọn dễ hơn.</p><div v-if="(reviews?.totalPages || 0) > 1" class="account-actions"><button class="button button-outline" :disabled="reviewPage === 0" @click="changeReviewPage(reviewPage - 1)"><ChevronLeft :size="15" />Trước</button><span>Trang {{ reviewPage + 1 }} / {{ reviews?.totalPages }}</span><button class="button button-outline" :disabled="reviewPage + 1 >= (reviews?.totalPages || 0)" @click="changeReviewPage(reviewPage + 1)">Sau<ChevronRight :size="15" /></button></div></div>
          <form v-if="eligibility?.canReview" class="review-form story-card" @submit.prevent="submitReview"><h3>{{ eligibility.myReview ? 'Cập nhật đánh giá' : 'Viết đánh giá của bạn' }}</h3><fieldset><legend>Mức độ hài lòng</legend><label v-for="star in 5" :key="star" class="rating-choice"><input v-model.number="reviewForm.rating" type="radio" name="rating" :value="star" :aria-label="`${star} sao`" /><span :class="{ chosen: star <= reviewForm.rating }">★</span></label></fieldset><label class="form-label">Chia sẻ trải nghiệm<textarea v-model="reviewForm.comment" required minlength="1" maxlength="2000" rows="5" placeholder="Bạn thấy sản phẩm thế nào khi sử dụng?" /></label><button class="button button-primary" :disabled="saving || !reviewForm.comment.trim()">{{ saving ? 'Đang gửi…' : 'Lưu đánh giá' }}</button></form>
          <aside v-else class="story-card"><h3>Chia sẻ sau khi trải nghiệm</h3><p>{{ eligibility?.reason || 'Đăng nhập và mua sản phẩm để chia sẻ đánh giá sau khi nhận hàng.' }}</p><RouterLink v-if="!currentUser" :to="{ path: '/login', query: { redirect: `/product/${productId}` } }" class="button button-outline">Đăng nhập</RouterLink><RouterLink v-else to="/purchases" class="button button-outline">Xem đơn mua</RouterLink></aside>
        </div>
      </section>
    </div>
    <Transition name="dialog"><div v-if="zoom" ref="lightbox" class="photo-lightbox" role="dialog" aria-modal="true" aria-label="Ảnh sản phẩm phóng to" tabindex="-1" @click.self="zoom = false" @keydown.esc="zoom = false" @keydown.tab.prevent><button class="lightbox-close" @click="zoom = false">Đóng ✕</button><img :src="image" :alt="detail?.name || 'Sản phẩm Mộc Studio'" /></div></Transition>
    <footer class="commerce-footer"><strong>Mộc Studio</strong><RouterLink to="/about">Về chúng tôi</RouterLink><RouterLink to="/guide">Cẩm nang mua sắm</RouterLink><RouterLink to="/customer">Cửa hàng →</RouterLink></footer>
  </main>
</template>
