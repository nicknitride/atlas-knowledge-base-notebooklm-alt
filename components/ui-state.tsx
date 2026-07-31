"use client";

import { AlertCircle, Loader2 } from "lucide-react";
import { Button } from "./ui/button";

interface EmptyStateProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
}

export function EmptyState({
  title,
  description,
  actionLabel,
  onAction,
  className = "",
}: EmptyStateProps) {
  return (
    <div
      className={`flex flex-col items-center justify-center text-center p-6 ${className}`}
      data-testid="empty-state"
    >
      <h3 className="text-sm font-semibold text-foreground mb-1">{title}</h3>
      <p className="text-xs text-muted-foreground leading-relaxed max-w-xs mb-4">
        {description}
      </p>
      {actionLabel && onAction ? (
        <Button type="button" size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      ) : null}
    </div>
  );
}

interface LoadingRegionProps {
  label: string;
  className?: string;
}

export function LoadingRegion({ label, className = "" }: LoadingRegionProps) {
  return (
    <div
      role="status"
      className={`flex items-center gap-2 p-4 text-xs text-muted-foreground ${className}`}
      data-testid="loading-region"
    >
      <Loader2 size={14} className="animate-spin text-primary" aria-hidden />
      {label}
    </div>
  );
}

interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
  onDismiss?: () => void;
  className?: string;
}

export function ErrorBanner({
  message,
  onRetry,
  onDismiss,
  className = "",
}: ErrorBannerProps) {
  return (
    <div
      role="alert"
      className={`mx-2 my-2 p-3 bg-destructive/10 border border-destructive/20 rounded-xl text-xs text-destructive flex items-center justify-between gap-2 ${className}`}
      data-testid="error-banner"
    >
      <span className="flex items-center gap-2 min-w-0">
        <AlertCircle size={14} className="shrink-0" aria-hidden />
        <span className="truncate">{message}</span>
      </span>
      <span className="flex items-center gap-2 shrink-0">
        {onRetry ? (
          <button
            type="button"
            className="font-medium hover:underline"
            onClick={onRetry}
          >
            Retry
          </button>
        ) : null}
        {onDismiss ? (
          <button
            type="button"
            className="font-medium hover:underline"
            onClick={onDismiss}
          >
            Dismiss
          </button>
        ) : null}
      </span>
    </div>
  );
}
