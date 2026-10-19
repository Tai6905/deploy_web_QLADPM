import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Read credentials locally; print only role names and verification outcomes.
const settings = new Map((await readFile(new URL('../accounts.local.properties', import.meta.url), 'utf8'))
  .split(/\r?\n/).filter(line => line.trim() && !line.trim().startsWith('#'))
  .map(line => { const at = line.indexOf('='); return [line.slice(0, at).trim(), line.slice(at + 1).trim()]; }));
const base = process.env.SHOPFLOW_URL || 'http://localhost:5173';
const apiPrefix = process.env.SHOPFLOW_API_PREFIX || '';
const roles = [
  ['owner', 'SHOP_OWNER', 200, 200, 200],
  ['sales', 'SALES_STAFF', 200, 403, 403],
  ['warehouse', 'WAREHOUSE', 403, 200, 403],
  ['customer', 'CUSTOMER', 403, 403, 403],
];
for (const [prefix, role, ordersStatus, inventoryStatus, employeesStatus] of roles) {
  const cookies = new Map();
  async function request(path, init = {}) {
    const response = await fetch(base + (init.headers?.Accept === 'text/html' ? '' : apiPrefix) + path, {
      ...init,
      headers: { Accept: 'application/json', 'Content-Type': 'application/json',
        Cookie: [...cookies].map(([name, value]) => `${name}=${value}`).join('; '), ...init.headers },
      signal: AbortSignal.timeout(20000),
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0];
      const at = pair.indexOf('=');
      cookies.set(pair.slice(0, at), pair.slice(at + 1));
    }
    return response;
  }
  const login = await request('/api/auth/login', { method: 'POST', body: JSON.stringify({
    email: settings.get(`${prefix}.email`), password: settings.get(`${prefix}.password`),
  }) });
  assert.equal(login.status, 200, `${role}: login`);
  assert.equal((await login.json()).role, role);
  try {
    const profile = await request('/api/account');
    assert.equal(profile.status, 200, `${role}: profile`);
    assert.equal((await profile.json()).role, role);
    const history = await request('/api/account/orders');
    assert.equal(history.status, 200, `${role}: purchase history`);
    assert.ok(Array.isArray((await history.json()).items));
    for (const [path, expected] of [['/orders', ordersStatus], ['/inventory', inventoryStatus], ['/api/employees', employeesStatus]]) {
      const result = await request(path);
      assert.equal(result.status, expected, `${role}: ${path}`);
      await result.arrayBuffer();
    }
    for (const page of ['/profile', '/purchases']) {
      const response = await request(page, { headers: { Accept: 'text/html' } });
      assert.equal(response.status, 200, `${role}: ${page}`);
      assert.match(response.headers.get('content-type'), /text\/html/);
      await response.arrayBuffer();
    }
  } finally {
    const session = await request('/api/auth/session');
    const { csrfToken } = await session.json();
    const logout = await request('/api/auth/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': csrfToken } });
    assert.equal(logout.status, 204, `${role}: logout`);
    const after = await request('/api/auth/session');
    assert.equal((await after.json()).authenticated, false, `${role}: logged out`);
  }
  console.log(`PASS: ${role} login, profile, history, permissions, frontend routes and logout through ${base}`);
}
