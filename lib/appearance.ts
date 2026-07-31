export const APPEARANCE_STORAGE_KEY = "atlas.appearance";

export type AppearanceMode = "light" | "dark" | "system";

const VALID: ReadonlySet<string> = new Set(["light", "dark", "system"]);

export function getAppearance(): AppearanceMode {
  if (typeof window === "undefined") return "system";
  const raw = localStorage.getItem(APPEARANCE_STORAGE_KEY);
  if (raw && VALID.has(raw)) return raw as AppearanceMode;
  return "system";
}

export function setAppearance(mode: AppearanceMode): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(APPEARANCE_STORAGE_KEY, mode);
  applyAppearance(mode);
}

function systemPrefersDark(): boolean {
  if (typeof window === "undefined" || !window.matchMedia) return false;
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function applyAppearance(mode: AppearanceMode): void {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  const effectiveDark =
    mode === "dark" || (mode === "system" && systemPrefersDark());
  root.classList.toggle("dark", effectiveDark);
  root.classList.toggle("light", mode === "light");
}

export function bootstrapAppearance(): AppearanceMode {
  const mode = getAppearance();
  applyAppearance(mode);
  return mode;
}
