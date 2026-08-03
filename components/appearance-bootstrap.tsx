"use client";

import { useEffect } from "react";
import {
  bootstrapAppearance,
  getAppearance,
  applyAppearance,
} from "@/lib/appearance";

/** Applies stored appearance on mount and listens for system scheme when mode is system. */
export function AppearanceBootstrap() {
  useEffect(() => {
    bootstrapAppearance();
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => {
      if (getAppearance() === "system") applyAppearance("system");
    };
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);
  return null;
}
