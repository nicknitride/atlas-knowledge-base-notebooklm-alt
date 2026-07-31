import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  APPEARANCE_STORAGE_KEY,
  applyAppearance,
  getAppearance,
  setAppearance,
  type AppearanceMode,
} from "@/lib/appearance";

describe("appearance", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove("dark", "light");
  });

  afterEach(() => {
    localStorage.clear();
  });

  it("defaults to system when unset", () => {
    expect(getAppearance()).toBe("system");
  });

  it("persists and reads light/dark/system", () => {
    setAppearance("dark");
    expect(localStorage.getItem(APPEARANCE_STORAGE_KEY)).toBe("dark");
    expect(getAppearance()).toBe("dark");
    setAppearance("light");
    expect(getAppearance()).toBe("light");
  });

  it("falls back to system for invalid stored values", () => {
    localStorage.setItem(APPEARANCE_STORAGE_KEY, "neon");
    expect(getAppearance()).toBe("system");
  });

  it("applies dark class for dark mode", () => {
    applyAppearance("dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(document.documentElement.classList.contains("light")).toBe(false);
  });

  it("applies light class and removes dark for light mode", () => {
    document.documentElement.classList.add("dark");
    applyAppearance("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(document.documentElement.classList.contains("light")).toBe(true);
  });

  it("system mode follows prefers-color-scheme", () => {
    const matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("dark"),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
    vi.stubGlobal("matchMedia", matchMedia);
    applyAppearance("system" satisfies AppearanceMode);
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    vi.unstubAllGlobals();
  });
});
