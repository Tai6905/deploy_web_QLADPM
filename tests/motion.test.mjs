import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as vue from 'vue';
import { loadSource } from './load-source.mjs';

function motionEnvironment(t) {
  const previous = Object.fromEntries(['window', 'document', 'IntersectionObserver', 'ResizeObserver', 'localStorage'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  t.after(() => {
    for (const [key, descriptor] of Object.entries(previous)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor); else delete globalThis[key];
    }
  });
  const preference = Object.assign(new EventTarget(), { matches: false });
  const browser = Object.assign(new EventTarget(), { matchMedia: () => preference, innerHeight: 600, scrollY: 500 });
  globalThis.window = browser;
  globalThis.document = { documentElement: { dataset: {}, scrollHeight: 1600 }, body: {} };
  return { browser, preference };
}

test('pausing reveals pending content immediately and new content never becomes hidden', t => {
  const { browser } = motionEnvironment(t);
  const observed = new Set();
  let disconnected = false;
  globalThis.IntersectionObserver = class {
    observe(element) { observed.add(element); }
    unobserve(element) { observed.delete(element); }
    disconnect() { disconnected = true; }
  };
  browser.IntersectionObserver = globalThis.IntersectionObserver;
  let directive;
  const { installMotion } = loadSource('src/motion.ts');
  const dispose = installMotion({ directive: (_name, value) => { directive = value; } });
  const element = () => ({ dataset: {}, style: { setProperty() {} } });
  const first = element();
  directive.mounted(first, { value: 2 });
  assert.equal(first.dataset.reveal, 'pending');
  document.documentElement.dataset.motion = 'paused';
  browser.dispatchEvent(new Event('shopflow:motion-change'));
  assert.equal(first.dataset.reveal, 'visible');
  assert.equal(observed.size, 0);
  const second = element();
  directive.mounted(second, { value: 0 });
  assert.notEqual(second.dataset.reveal, 'pending');
  dispose();
  assert.equal(disconnected, true);
});

test('system reduced motion also releases content that was awaiting scroll reveal', t => {
  const { browser, preference } = motionEnvironment(t);
  globalThis.IntersectionObserver = class { observe() {} unobserve() {} disconnect() {} };
  browser.IntersectionObserver = globalThis.IntersectionObserver;
  let directive;
  const dispose = loadSource('src/motion.ts').installMotion({ directive: (_name, value) => { directive = value; } });
  const first = { dataset: {}, style: { setProperty() {} } };
  directive.mounted(first, {});
  preference.matches = true;
  preference.dispatchEvent(new Event('change'));
  assert.equal(first.dataset.reveal, 'visible');
  const second = { dataset: {}, style: { setProperty() {} } };
  directive.mounted(second, {});
  assert.notEqual(second.dataset.reveal, 'pending');
  dispose();
});

test('motion controls survive blocked storage, respect system settings and release scroll listeners', t => {
  const { browser, preference } = motionEnvironment(t);
  let mount, unmount, pendingFrame, cancelledFrame;
  let requestedFrames = 0;
  browser.requestAnimationFrame = callback => { pendingFrame = callback; return ++requestedFrames; };
  browser.cancelAnimationFrame = id => { cancelledFrame = id; };
  const scrolls = [];
  browser.scrollTo = value => scrolls.push(value);
  globalThis.localStorage = { getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); } };
  const component = loadSource('src/components/MotionControls.vue', { vueSetup: true, imports: {
    vue: { ...vue, onMounted: callback => { mount = callback; }, onUnmounted: callback => { unmount = callback; } },
    'lucide-vue-next': {},
  } });
  const app = component.default.setup({}, { expose() {} });
  mount();
  assert.equal(app.progress.value, .5);
  assert.equal(app.scrolled.value, true);
  app.toggleMotion();
  assert.equal(document.documentElement.dataset.motion, 'paused');
  app.scrollToTop();
  assert.equal(scrolls[0].behavior, 'instant');
  preference.matches = true;
  preference.dispatchEvent(new Event('change'));
  app.toggleMotion();
  assert.equal(app.stopped.value, true);
  browser.dispatchEvent(new Event('scroll'));
  browser.dispatchEvent(new Event('scroll'));
  assert.equal(requestedFrames, 1);
  pendingFrame();
  browser.dispatchEvent(new Event('scroll'));
  unmount();
  assert.equal(cancelledFrame, 2);
  browser.dispatchEvent(new Event('scroll'));
  assert.equal(requestedFrames, 2);
  assert.equal(document.documentElement.dataset.motion, undefined);
});
