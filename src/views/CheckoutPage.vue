<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { MapPin, CreditCard, Truck, TicketPercent, ArrowLeft, Check, ShieldCheck, PackageCheck, ShoppingBag } from 'lucide-vue-next';
import { shopflowApi, type SavedAddress, type ShippingAddress, type CheckoutQuote, type CheckoutVoucher, type PaymentMethod, type OrderResponse } from '../api/shopflowApi';
import { currentUser, dataSource } from '../auth/session';
import { money, type Product } from '../data';
import StorefrontNav from '../components/StorefrontNav.vue';
import ProductImage from '../components/ProductImage.vue';
const props=defineProps<{ cartItems: Array<{product:Product;quantity:number}>;cartTotal:number }>();
const emit=defineEmits<{ 'clear-cart': []; 'refresh-products': [products: Product[]] }>();
const addresses=ref<SavedAddress[]>([]), offers=ref<CheckoutVoucher[]>([]), quote=ref<CheckoutQuote|null>(null), order=ref<OrderResponse|null>(null);
const loading=ref(true), busy=ref(false), addressBusy=ref(false), quoting=ref(false), error=ref(''), quoteError=ref(''), message=ref('');
const selected=ref<number|null>(null), editor=ref(true), remember=ref(true), makeDefault=ref(true), payment=ref<PaymentMethod>('COD');
const address=reactive<ShippingAddress>({receiverName:currentUser.value?.displayName||'',phone:'',addressLine:'',district:'',city:''});
const email=ref(''), voucherDraft=ref(''), appliedVoucher=ref('');
const checkoutKey=crypto.randomUUID();
let quoteGeneration=0, alive=true;
const lines=computed(()=>props.cartItems.map(({product,quantity})=>({productId:product.id,quantity})));
const methodLabel=computed(()=>payment.value==='COD'?'Thanh toán khi nhận hàng':payment.value==='CARD'?'Thẻ ngân hàng · mô phỏng':'Chuyển khoản · mô phỏng');
function select(value:SavedAddress) { selected.value=value.id;Object.assign(address,{receiverName:value.receiverName,phone:value.phone,addressLine:value.addressLine,district:value.district||'',city:value.city});makeDefault.value=value.isDefault;editor.value=false;message.value=''; }
function newAddress() { selected.value=null;Object.assign(address,{receiverName:currentUser.value?.displayName||'',phone:'',addressLine:'',district:'',city:''});editor.value=true;makeDefault.value=addresses.value.length===0; }
function validAddress() {
  if(!address.receiverName.trim() || !address.addressLine.trim() || !address.city.trim()) { error.value='Điền đủ họ tên, địa chỉ chi tiết và tỉnh/thành phố.';editor.value=true;return false; }
  if(!/^\+?[0-9][0-9 .()-]{7,19}$/.test(address.phone.trim())) {error.value='Nhập số điện thoại nhận hàng hợp lệ.';editor.value=true;return false;}return true;
}
async function saveAddress() {
  error.value='';if(!validAddress()||addressBusy.value)return;addressBusy.value=true;
  try {const saved=await shopflowApi.saveAddress(selected.value,{shippingAddress:{...address},makeDefault:makeDefault.value});addresses.value=await shopflowApi.getAddresses();select(saved);message.value='Đã lưu địa chỉ vào tài khoản cho lần mua sau.';}
  catch(cause){error.value=cause instanceof Error?cause.message:'Không lưu được địa chỉ.';}finally{addressBusy.value=false;}
}
async function removeAddress(id:number) {
  if(addressBusy.value)return;addressBusy.value=true;error.value='';
  try {await shopflowApi.deleteAddress(id);addresses.value=await shopflowApi.getAddresses();if(selected.value===id){if(addresses.value[0])select(addresses.value[0]);else newAddress();}}
  catch(cause){error.value=cause instanceof Error?cause.message:'Không xóa được địa chỉ.';}finally{addressBusy.value=false;}
}
async function refreshQuote() {
  const generation=++quoteGeneration;quote.value=null;quoteError.value='';
  if(dataSource!=='api'||!lines.value.length||order.value)return;
  quoting.value=true;
  try {const value=await shopflowApi.quoteCheckout(lines.value,appliedVoucher.value||null);if(alive&&generation===quoteGeneration)quote.value=value;}
  catch(cause){if(alive&&generation===quoteGeneration)quoteError.value=cause instanceof Error?cause.message:'Không tính được giá đơn hàng.';}
  finally{if(generation===quoteGeneration)quoting.value=false;}
}
async function applyVoucher(code=voucherDraft.value) {appliedVoucher.value=code.trim().toUpperCase();voucherDraft.value=appliedVoucher.value;await refreshQuote();}
async function place() {
  if(busy.value||!quote.value||loading.value||addressBusy.value)return;error.value='';if(!validAddress())return;
  busy.value=true;
  try {
    const result=await shopflowApi.placeCheckout({shippingAddress:{...address},contactEmail:email.value.trim()||undefined,paymentMethod:payment.value,items:lines.value,voucherCode:appliedVoucher.value||null,checkoutKey,saveAddress:remember.value,addressId:selected.value,makeDefault:makeDefault.value,expectedTotal:quote.value.totalAmount});
    order.value=result;emit('clear-cart');
  }catch(cause){error.value=cause instanceof Error?cause.message:'Chưa xác nhận được đơn. Bạn có thể thử lại; cùng yêu cầu sẽ không tạo thêm đơn.';if(cause&&typeof cause==='object'&&'status' in cause&&Number(cause.status)>=400&&Number(cause.status)<500)await refreshQuote();}
  finally{busy.value=false;}
}
async function payDemo() {
  if(!order.value||busy.value)return;busy.value=true;error.value='';
  try {const result=await shopflowApi.simulatePayment(order.value.orderRef,'SUCCESS');if(result.status==='SUCCESS')order.value={...order.value,status:'PAID'};else error.value='Thời gian giữ hàng đã hết. Vui lòng kiểm tra đơn trong Đơn mua.';}
  catch(cause){error.value=cause instanceof Error?cause.message:'Chưa xác nhận được thanh toán. Vui lòng kiểm tra Đơn mua.';}finally{busy.value=false;}
}
watch(()=>JSON.stringify(lines.value),()=>{if(!order.value)void refreshQuote();});
onMounted(async()=>{
  if(dataSource!=='api'){loading.value=false;return;}
  try {
    const [saved,vouchers,profile,products]=await Promise.all([shopflowApi.getAddresses(),shopflowApi.getCheckoutVouchers(),shopflowApi.getProfile(),Promise.all(props.cartItems.map(async({product})=>{
      const detail=await shopflowApi.getProduct(product.id);return {...product,name:detail.name,price:detail.listPrice,stock:detail.productVariants.reduce((total,item)=>total+item.availableQuantity,0),image:detail.productImages[0]?.imageUrl||''};
    }))]);
    if(!alive)return;addresses.value=saved;offers.value=vouchers;email.value=profile.email;
    if(saved[0])select(saved[0]);else {address.receiverName=profile.displayName;address.phone=profile.phone||'';}
    emit('refresh-products',products);await refreshQuote();
  }catch(cause){error.value=cause instanceof Error?cause.message:'Không tải được thông tin thanh toán. Vui lòng tải lại trang.';}
  finally{if(alive)loading.value=false;}
});
onUnmounted(()=>{alive=false;++quoteGeneration;});
</script>
<template>
  <main class="commerce-page checkout-page"><StorefrontNav v-model:error="error" />
    <div class="commerce-container">
      <RouterLink class="checkout-back" to="/customer#cart"><ArrowLeft :size="16" />Quay lại giỏ hàng</RouterLink>
      <div class="checkout-heading"><div><span class="auth-kicker">SHOPFLOW · HOÀN TẤT LỰA CHỌN</span><h1>Thanh toán</h1><p>Nhạc cụ yêu thích, thông tin rõ ràng. Kiểm tra lại trước khi bắt đầu hành trình âm nhạc.</p></div><span><ShieldCheck :size="18" />Thông tin được lưu theo tài khoản</span></div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="message" class="form-success" role="status">{{ message }}</p>
      <section v-if="order" class="checkout-confirmation story-card" role="status"><PackageCheck :size="48" /><h2>Đã nhận đơn SF-{{ order.id }}</h2><p>{{ order.paymentMethod==='COD'?'Cửa hàng sẽ chuẩn bị đơn. Bạn thanh toán khi nhận hàng.':order.status==='PAID'?'Thanh toán mô phỏng thành công. Cửa hàng sẽ chuẩn bị đơn.':'Đơn đã được tạo. Hoàn tất bước thanh toán mô phỏng bên dưới; hàng được giữ trong 15 phút.' }}</p><strong>{{ money(order.totalAmount) }}</strong><p v-if="order.discountAmount">Đã áp dụng {{ order.voucherCode }} · giảm {{ money(order.discountAmount) }}</p><p>{{ order.shippingAddress.receiverName }} · {{ order.shippingAddress.phone }}<br />{{ [order.shippingAddress.addressLine,order.shippingAddress.district,order.shippingAddress.city].filter(Boolean).join(', ') }}</p><button v-if="order.paymentMethod!=='COD'&&order.status==='PENDING_PAYMENT'" class="button button-primary" :disabled="busy" @click="payDemo">{{ busy?'Đang xác nhận…':'Xác nhận thanh toán mô phỏng' }}</button><div class="account-actions"><RouterLink to="/purchases" class="button button-outline">Theo dõi đơn mua</RouterLink><RouterLink to="/customer" class="button button-outline">Tiếp tục mua sắm</RouterLink></div></section>
      <p v-else-if="dataSource!=='api'" class="empty-state">Trang thanh toán lưu địa chỉ và voucher dùng với dữ liệu API. Hãy mở bản kết nối backend để đặt hàng.</p>
      <section v-else-if="!cartItems.length" class="story-card checkout-empty"><ShoppingBag :size="40" /><h2>Giỏ hàng đang trống</h2><RouterLink to="/customer" class="button button-primary">Khám phá cửa hàng</RouterLink></section>
      <p v-else-if="loading" class="empty-state motion-loading" role="status">Đang tải địa chỉ và kiểm tra giỏ hàng…</p>
      <form v-else class="checkout-layout" @submit.prevent="place">
        <div class="checkout-sections">
          <section class="checkout-card"><div class="checkout-section-title"><h2><MapPin :size="20" />1. Địa chỉ nhận hàng</h2><button type="button" class="text-link" :disabled="busy" @click="newAddress">+ Địa chỉ mới</button></div>
            <div v-if="addresses.length" class="checkout-address-list"><article v-for="saved in addresses" :key="saved.id" :class="{selected:selected===saved.id}"><button type="button" class="address-select" :aria-pressed="selected===saved.id" :disabled="busy||addressBusy" @click="select(saved)"><span class="checkout-radio">{{ selected===saved.id?'●':'○' }}</span><span><strong>{{ saved.receiverName }} · {{ saved.phone }}</strong><small>{{ [saved.addressLine,saved.district,saved.city].filter(Boolean).join(', ') }}</small><em v-if="saved.isDefault">Mặc định</em></span></button><div><button type="button" class="text-link" :disabled="busy||addressBusy" @click="select(saved);editor=true">Sửa</button><button type="button" class="text-link" :disabled="busy||addressBusy" @click="removeAddress(saved.id)">Xóa</button></div></article></div>
            <fieldset v-if="editor" class="checkout-address-form" :disabled="busy||addressBusy"><legend>{{ selected?'Chỉnh sửa địa chỉ':'Địa chỉ mới' }}</legend><div class="form-row"><label class="form-label">Họ và tên người nhận<input v-model="address.receiverName" required maxlength="255" autocomplete="shipping name" placeholder="Nguyễn Minh Anh" /></label><label class="form-label">Số điện thoại<input v-model="address.phone" required maxlength="20" type="tel" autocomplete="shipping tel" placeholder="09xxxxxxxx" /></label></div><label class="form-label">Số nhà, tên đường, phường/xã<input v-model="address.addressLine" required maxlength="500" autocomplete="shipping street-address" placeholder="Địa chỉ chi tiết để giao hàng" /></label><div class="form-row"><label class="form-label">Quận / huyện<input v-model="address.district" maxlength="100" autocomplete="shipping address-level2" placeholder="Có thể bỏ trống" /></label><label class="form-label">Tỉnh / thành phố<input v-model="address.city" required maxlength="100" autocomplete="shipping address-level1" placeholder="Ví dụ: TP. Hồ Chí Minh" /></label></div><label class="checkout-check"><input v-model="makeDefault" type="checkbox" />Đặt làm địa chỉ mặc định</label><button type="button" class="button button-outline" @click="saveAddress">{{ addressBusy?'Đang lưu…':'Lưu địa chỉ này' }}</button></fieldset>
            <label class="checkout-check"><input v-model="remember" type="checkbox" :disabled="busy" />Lưu thông tin nhận hàng vào tài khoản cho lần mua sau</label>
            <label class="form-label checkout-email">Email liên hệ <small>(tùy chọn)</small><input v-model="email" type="email" maxlength="255" autocomplete="email" :disabled="busy" /></label>
          </section>
          <section class="checkout-card"><div class="checkout-section-title"><h2><TicketPercent :size="20" />2. Ưu đãi ShopFlow</h2></div><div class="checkout-voucher-input"><input v-model="voucherDraft" maxlength="50" aria-label="Mã voucher" placeholder="Nhập mã ưu đãi" :disabled="busy||quoting" @keydown.enter.prevent="applyVoucher()" /><button type="button" class="button button-outline" :disabled="busy||quoting||!voucherDraft.trim()" @click="applyVoucher()">Áp dụng</button></div><p v-if="quoteError" class="form-error" role="alert">{{ quoteError }}</p><p v-if="appliedVoucher" class="checkout-applied">{{ quote?.voucherCode?'Đã áp dụng':'Đang chọn' }}: {{ appliedVoucher }}<button type="button" class="text-link" :disabled="busy||quoting" @click="applyVoucher('')">Gỡ mã</button></p><div class="checkout-offers"><button v-for="offer in offers" :key="offer.code" type="button" :disabled="busy||quoting" @click="applyVoucher(offer.code)"><strong>{{ offer.code }}</strong><span>{{ offer.title }}</span><small>Đơn từ {{ money(offer.minimum) }}</small></button></div><p class="muted-cell">Mỗi đơn áp dụng một mã. Điều kiện và lượt sử dụng được kiểm tra lại khi đặt hàng.</p></section>
          <section class="checkout-card"><div class="checkout-section-title"><h2><CreditCard :size="20" />3. Phương thức thanh toán</h2></div><fieldset class="checkout-payments" :disabled="busy"><legend class="sr-only">Chọn cách thanh toán</legend><label :class="{selected:payment==='COD'}"><input v-model="payment" type="radio" value="COD" name="payment" /><Truck :size="21" /><span><strong>Thanh toán khi nhận hàng (COD)</strong><small>Thanh toán tiền mặt khi đơn được giao và xác nhận thu tiền.</small></span></label><label :class="{selected:payment==='CARD'}"><input v-model="payment" type="radio" value="CARD" name="payment" /><CreditCard :size="21" /><span><strong>Thẻ ngân hàng · mô phỏng</strong><small>Xác nhận thanh toán thử sau khi đặt. Không nhập số thẻ thật.</small></span></label><label :class="{selected:payment==='BANK_TRANSFER'}"><input v-model="payment" type="radio" value="BANK_TRANSFER" name="payment" /><CreditCard :size="21" /><span><strong>Chuyển khoản · mô phỏng</strong><small>Luồng thử nghiệm của đồ án, chưa kết nối ngân hàng.</small></span></label></fieldset></section>
        </div>
        <aside class="checkout-summary checkout-card"><h2>Đơn hàng của bạn <small>{{ cartItems.reduce((total,item)=>total+item.quantity,0) }} sản phẩm</small></h2><div class="checkout-items"><article v-for="item in cartItems" :key="item.product.id"><ProductImage :src="item.product.image" :alt="item.product.name" /><div><RouterLink :to="`/product/${item.product.id}`">{{ item.product.name }}</RouterLink><small>{{ money(item.product.price) }} × {{ item.quantity }}</small></div><strong>{{ money(item.product.price*item.quantity) }}</strong></article></div><dl><div><dt>Tạm tính</dt><dd>{{ money(quote?.subtotalAmount??cartTotal) }}</dd></div><div><dt>Ưu đãi{{ quote?.voucherCode?' · '+quote.voucherCode:'' }}</dt><dd class="discount">−{{ money(quote?.discountAmount??0) }}</dd></div><div><dt>Phí giao hàng</dt><dd>0 ₫</dd></div><div class="checkout-total"><dt>Tổng thanh toán</dt><dd>{{ money(quote?.totalAmount??cartTotal) }}</dd></div></dl><small class="muted-cell">Phí giao hàng đang miễn phí trong bản đồ án.</small><p>{{ methodLabel }}</p><button type="submit" class="button button-primary checkout-place-button" :disabled="busy||quoting||!quote||addressBusy"><Check :size="18" />{{ busy?'Đang xác nhận…':quoting?'Đang tính giá…':'Đặt hàng' }}</button><p class="checkout-fine-print">Kiểm tra thông tin và tổng tiền trước khi xác nhận. Đơn hàng được lưu trong Đơn mua.</p></aside>
      </form>
    </div><footer class="commerce-footer"><strong>ShopFlow</strong><RouterLink to="/guide">Cẩm nang mua sắm</RouterLink><RouterLink to="/customer">Tiếp tục khám phá →</RouterLink></footer>
  </main>
</template>
