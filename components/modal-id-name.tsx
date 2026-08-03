"use client";

import { useEffect, useId, useRef } from "react";
import { Button } from "./ui/button";

interface ModalProps {
  showModal: boolean;
  onSubmit: React.FormEventHandler<HTMLFormElement>;
  title: string;
  inputLabel: string;
  onCancel: () => void;
  labelPlaceHolder: string;
  labelValue: string;
  onChange: (value: string) => void;
  /** When false, skip restoring focus to the trigger on close (e.g. post-create compose focus). */
  restoreFocus?: boolean;
}

export function ModalIdName({
  showModal,
  onSubmit,
  title,
  inputLabel,
  labelPlaceHolder,
  labelValue,
  onChange,
  onCancel,
  restoreFocus = true,
}: ModalProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);
  const restoreFocusRef = useRef(restoreFocus);
  restoreFocusRef.current = restoreFocus;

  useEffect(() => {
    if (!showModal) return;
    previouslyFocused.current = document.activeElement as HTMLElement | null;
    const root = dialogRef.current;
    const focusable = root?.querySelector<HTMLElement>(
      "input, button, [href], select, textarea, [tabindex]:not([tabindex='-1'])",
    );
    focusable?.focus();

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCancel();
        return;
      }
      if (e.key !== "Tab" || !root) return;
      const nodes = Array.from(
        root.querySelectorAll<HTMLElement>(
          "input, button, [href], select, textarea, [tabindex]:not([tabindex='-1'])",
        ),
      ).filter((el) => !el.hasAttribute("disabled"));
      if (nodes.length === 0) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      if (restoreFocusRef.current) {
        previouslyFocused.current?.focus?.();
      }
    };
  }, [showModal, onCancel]);

  if (!showModal) return null;

  return (
    <div
      className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="bg-card border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl"
      >
        <h3
          id={titleId}
          className="text-base font-semibold text-foreground mb-4"
        >
          {title}
        </h3>
        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label className="text-xs text-muted-foreground block mb-1">
              {inputLabel}
            </label>
            <input
              type="text"
              value={labelValue}
              onChange={(e) => onChange(e.target.value)}
              placeholder={labelPlaceHolder}
              className="w-full px-3 py-2 rounded-lg bg-input border border-border text-sm text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          <div className="flex gap-2 justify-end">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onCancel}
            >
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={!labelValue.trim()}>
              Confirm
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
