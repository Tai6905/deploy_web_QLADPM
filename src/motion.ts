import type { App, ObjectDirective } from "vue";

/** One observer for the whole app; reveal once and release each element. */
export function installMotion(app: App) {
  const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
  const waiting = new Set<HTMLElement>();
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
      if (!observer || preference.matches) return;
      element.dataset.reveal = "pending";
      waiting.add(element);
      observer.observe(element);
    },
    beforeUnmount(element) { observer?.unobserve(element); waiting.delete(element); },
  };
  app.directive("reveal", reveal);
  const onPreferenceChange = () => { if (preference.matches) [...waiting].forEach(show); };
  preference.addEventListener("change", onPreferenceChange);
  return () => {
    [...waiting].forEach(show);
    observer?.disconnect();
    preference.removeEventListener("change", onPreferenceChange);
  };
}
