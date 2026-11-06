import type { App, ObjectDirective } from "vue";

/** One observer for the whole app; reveal once and release each element. */
export function installMotion(app: App) {
  const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
  const waiting = new Set<HTMLElement>();
  const isPaused = () => preference.matches || document.documentElement.dataset.motion === "paused";
  const show = (element: HTMLElement) => {
    element.dataset.reveal = "visible";
    waiting.delete(element);
    observer?.unobserve(element);
  };
  const observer = "IntersectionObserver" in window ? new IntersectionObserver(entries => {
    entries.forEach(entry => { if (entry.isIntersecting) show(entry.target as HTMLElement); });
  }, { threshold: 0.08, rootMargin: "0px 0px -16px 0px" }) : null;

  const reveal: ObjectDirective<HTMLElement, number | undefined> = {
    mounted(element, binding) {
      element.style.setProperty("--reveal-delay", `${Math.min(5, Math.max(0, binding.value || 0)) * 55}ms`);
      if (!observer || isPaused()) return;
      element.dataset.reveal = "pending";
      waiting.add(element);
      observer.observe(element);
    },
    beforeUnmount(element) { observer?.unobserve(element); waiting.delete(element); },
  };
  app.directive("reveal", reveal);
  const onPreferenceChange = () => { if (isPaused()) [...waiting].forEach(show); };
  preference.addEventListener("change", onPreferenceChange);
  window.addEventListener("shopflow:motion-change", onPreferenceChange);
  return () => {
    [...waiting].forEach(show);
    observer?.disconnect();
    preference.removeEventListener("change", onPreferenceChange);
    window.removeEventListener("shopflow:motion-change", onPreferenceChange);
  };
}
