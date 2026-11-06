import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as vue from 'vue';
import { loadSource } from './load-source.mjs';

function login({ signIn = async () => {}, signUp = async () => {}, redirect = '' } = {}) {
  const destinations = [];
  const component = loadSource('src/views/LoginPage.vue', { vueSetup: true, imports: {
    vue, 'lucide-vue-next': {},
    'vue-router': { useRoute: () => ({ query: { redirect } }), useRouter: () => ({ replace: async path => destinations.push(path) }) },
    '../auth/session': { currentRole: vue.ref('CUSTOMER'), dataSource: 'api', signIn, signUp },
    '../auth/permissions': loadSource('src/auth/permissions.ts'),
  } });
  const app = component.default.setup({}, { expose() {} });
  Object.assign(app.loginForm, { email: ' musician@example.com ', password: 'test-pass-123', displayName: ' Music lover ' });
  return { app, destinations };
}

test('registration blocks mismatched confirmation and sends the trimmed account only after correction', async () => {
  const calls = [];
  const { app, destinations } = login({ signUp: async input => calls.push(input) });
  app.changeMode('register');
  app.confirmPassword.value = 'different-pass';
  await app.submitLogin();
  assert.equal(calls.length, 0);
  assert.match(app.loginError.value, /chưa khớp/);
  assert.equal(app.submitting.value, false);
  app.confirmPassword.value = app.loginForm.password;
  await app.submitLogin();
  assert.deepEqual(calls, [{ email: 'musician@example.com', password: 'test-pass-123', displayName: 'Music lover' }]);
  assert.deepEqual(destinations, ['/customer']);
});

test('an in-flight login prevents duplicate submissions and switching the account form', async () => {
  let complete;
  let calls = 0;
  const { app } = login({ signIn: () => { calls++; return new Promise(resolve => { complete = resolve; }); } });
  const pending = app.submitLogin();
  await app.submitLogin();
  app.changeMode('register');
  assert.equal(calls, 1);
  assert.equal(app.loginMode.value, 'login');
  complete();
  await pending;
  app.showPassword.value = true;
  app.loginError.value = 'Old error';
  app.confirmPassword.value = 'old confirmation';
  app.changeMode('register');
  assert.equal(app.showPassword.value, false);
  assert.equal(app.loginError.value, '');
  assert.equal(app.confirmPassword.value, '');
  assert.equal(app.loginForm.email, ' musician@example.com ');
});

test('login keeps allowed checkout redirects and rejects external or employee-only destinations', async () => {
  for (const [redirect, expected] of [['/checkout?step=address', '/checkout?step=address'], ['//other.example', '/customer'], ['/staff', '/customer']]) {
    const { app, destinations } = login({ redirect });
    await app.submitLogin();
    assert.deepEqual(destinations, [expected]);
  }
});

test('a failed sign-in leaves the form available for retry and does not navigate', async () => {
  const { app, destinations } = login({ signIn: async () => { throw new Error('Email hoặc mật khẩu chưa đúng'); } });
  await app.submitLogin();
  assert.equal(app.submitting.value, false);
  assert.equal(app.loginError.value, 'Email hoặc mật khẩu chưa đúng');
  assert.deepEqual(destinations, []);
});
