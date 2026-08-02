"use client";

import type { DocumentHealthStatus } from "@/lib/api";

interface IndexHealthBadgeProps {
  status: DocumentHealthStatus;
  className?: string;
}

const STATUS_CONFIG: Record<
  DocumentHealthStatus,
  { label: string; className: string }
> = {
  READY: {
    label: "Indexed",
    className:
      "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30",
  },
  STALE: {
    label: "Stale — rebuild needed",
    className: "bg-amber-500/15 text-amber-400 border border-amber-500/30",
  },
  PENDING: {
    label: "Indexing…",
    className: "bg-blue-500/15 text-blue-400 border border-blue-500/30",
  },
  FAILED: {
    label: "Index failed",
    className: "bg-red-500/15 text-red-400 border border-red-500/30",
  },
};

export function IndexHealthBadge({
  status,
  className = "",
}: IndexHealthBadgeProps) {
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.FAILED;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${config.className} ${className}`}
    >
      <span
        className={`inline-block size-1.5 rounded-full ${
          status === "PENDING"
            ? "animate-pulse bg-blue-400"
            : status === "READY"
              ? "bg-emerald-400"
              : status === "STALE"
                ? "bg-amber-400"
                : "bg-red-400"
        }`}
      />
      {config.label}
    </span>
  );
}
