import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as vue from 'vue';
import { loadSource } from './load-source.mjs';
const policy = loadSource('src/orders/cancellations.ts');
const permissions = loadSource('src/auth/permissions.ts');
class ApiError extends Error { constructor(message, status) { super(message); this.status = status; } }
function page(api, role = 'CUSTOMER', query = { order: '5', scope: 'mine' }) {
  const route = vue.reactive({ query });
  const component = loadSource('src/views/OrderCancellationsPage.vue', { vueSetup: true, imports: {
    vue: { ...vue, watch() {}, onUnmounted() {} },
    'vue-router': { useRoute: () => route },
    '../api/shopflowApi': { shopflowApi: api }, '../api/httpClient': { ApiError },
    '../auth/session': { currentRole: vue.ref(role), dataSource: 'api' },
    '../auth/permissions': permissions, '../orders/cancellations': policy, '../data': { money: String },
  } });
  return { app: component.default.setup({}, { expose() {} }), route };
}
const order = (id = 5, status = 'PENDING_PAYMENT', deliveryStatus = 'NONE') => ({ id, status, deliveryStatus, paymentMethod: 'COD', items: [], totalAmount: 100000 });
const cancelled = { ...order(), status: 'CANCELLED', cancellation: { reason: 'Ordered twice', actorKind: 'CUSTOMER', paymentOutcome: 'NOT_CHARGED', refundAmount: 0, cancelledAt: new Date().toISOString() } };

test('cancellation is allowed only before shipment and blocks collected COD orders', () => {
  for (const status of ['PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED']) {
    for (const deliveryStatus of ['NONE', 'PREPARING']) assert.equal(policy.canCancelOrder({ status, deliveryStatus, paymentMethod: 'CARD' }), true);
    for (const deliveryStatus of ['SHIPPED', 'DELIVERED']) assert.equal(policy.canCancelOrder({ status, deliveryStatus }), false);
  }
  assert.equal(policy.canCancelOrder(order(1, 'CANCELLED')), false);
  assert.equal(policy.canCancelOrder(order(1, 'PAID')), false);
  assert.equal(policy.canCancelOrder(null), false);
  assert.equal(permissions.canAccessPage('cancellations', 'CUSTOMER'), true);
  assert.equal(permissions.canAccessPage('cancellations', null), false);
});

test('customers cannot select the staff API by changing the scope in the URL', async () => {
  let mine = 0;
  const { app } = page({ getMyOrders: async input => { mine++; assert.equal(input.status, 'CANCELLED'); return { items: [], totalPages: 0 }; }, getOrders() { throw Error('Staff API must not be called'); } }, 'CUSTOMER', { scope: 'all' });
  await app.loadList();
  assert.equal(mine, 1);
  assert.equal(app.staff.value, false);
});

test('cancellation blocks duplicate clicks and refreshes cancelled history after success', async () => {
  let finish, calls = 0, history = 0;
  const { app } = page({ cancelMyOrder: (id, reason) => { calls++; assert.equal(id, 5); assert.equal(reason, 'Ordered twice'); return new Promise(resolve => finish = resolve); }, getMyOrders: async input => { history++; assert.equal(input.status, 'CANCELLED'); return { items: [cancelled], totalPages: 1 }; } });
  app.selected.value = order(); app.reason.value = '  Ordered twice  ';
  const pending = app.cancel();
  assert.equal(app.busy.value, true); await app.cancel(); assert.equal(calls, 1);
  finish(cancelled); await pending;
  assert.equal(app.busy.value, false); assert.equal(app.selected.value.status, 'CANCELLED'); assert.equal(history, 1);
  assert.equal(app.canSubmit.value, false); assert.ok(app.success.value);
});

test('an old detail response cannot overwrite the order selected more recently', async () => {
  const pending = [];
  const { app, route } = page({ getMyOrder: () => new Promise(resolve => pending.push(resolve)) });
  const first = app.loadSelected(); route.query.order = '6'; const second = app.loadSelected();
  pending[1]({ order: order(6) }); await second;
  pending[0]({ order: order(5) }); await first;
  assert.equal(app.selected.value.id, 6);
});

test('a shipment conflict reloads the latest order and blocks another cancellation', async () => {
  const { app } = page({ cancelMyOrder: async () => { throw new ApiError('Already shipped', 409); }, getMyOrder: async () => ({ order: order(5, 'PENDING_PAYMENT', 'SHIPPED') }) });
  app.selected.value = order(); app.reason.value = 'Changed my mind';
  await app.cancel();
  assert.equal(app.selected.value.deliveryStatus, 'SHIPPED'); assert.equal(app.canSubmit.value, false);
  assert.equal(app.actionError.value, 'Already shipped'); assert.equal(app.success.value, ''); assert.equal(app.busy.value, false);
});

test('invalid order IDs never reach the order detail API', async () => {
  const { app } = page({ getMyOrder() { throw Error('Must not call'); } }, 'CUSTOMER', { order: '-2' });
  await app.loadSelected(); assert.ok(app.detailError.value); assert.equal(app.selected.value, null);
});

test('payment failures and explicit cancellation have separate presentation states', () => {
  const { toOrderView } = loadSource('src/api/viewModels.ts');
  const base = { id: 5, orderRef: 'ref', customer: 'Test', createdAt: new Date().toISOString(), totalAmount: 1000, product: 'Guitar', quantity: 1, deliveryStatus: 'NONE', paymentMethod: 'COD' };
  assert.equal(toOrderView({ ...base, status: 'PAYMENT_FAILED', paymentStatus: 'FAILED' }).status, 'Thanh toán lỗi');
  const refunded = toOrderView({ ...base, status: 'CANCELLED', paymentStatus: 'REFUNDED' });
  assert.equal(refunded.status, 'Đã hủy'); assert.equal(refunded.payment, 'Hoàn tiền mô phỏng');
});

test('API encodes cancelled history filters and the cancellation reason safely', async () => {
  const calls = [];
  const api = loadSource('src/api/shopflowApi.ts', { imports: { './httpClient': { apiRequest: async (...args) => { calls.push(args); return {}; } } } }).shopflowApi;
  await api.getMyOrders({ page: 0, size: 10, status: 'CANCELLED', query: 'SF-5' });
  const query = new URL(calls[0][0], 'http://example.invalid').searchParams;
  assert.equal(query.get('status'), 'CANCELLED'); assert.equal(query.get('q'), 'SF-5');
  await api.cancelMyOrder(5, 'Wrong "address"');
  assert.equal(calls[1][0], '/api/account/orders/5/cancel'); assert.equal(calls[1][1].method, 'POST');
  assert.equal(JSON.parse(calls[1][1].body).reason, 'Wrong "address"');
});
