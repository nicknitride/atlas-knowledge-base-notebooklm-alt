"use client";

import { useState } from "react";
import { IndexHealthBadge } from "./index-health-badge";
import type { IndexHealthResponse, RebuildResponse } from "@/lib/api";
import { rebuildWorkspaceIndex } from "@/lib/api";

interface RebuildIndexDialogProps {
  workspaceId: string;
  health: IndexHealthResponse;
  onClose: () => void;
  onRebuildComplete: (result: RebuildResponse) => void;
}

export function RebuildIndexDialog({
  workspaceId,
  health,
  onClose,
  onRebuildComplete,
}: RebuildIndexDialogProps) {
  const [isRebuilding, setIsRebuilding] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RebuildResponse | null>(null);

  async function handleRebuild() {
    setIsRebuilding(true);
    setError(null);
    try {
      const rebuildResult = await rebuildWorkspaceIndex(workspaceId);
      setResult(rebuildResult);
      onRebuildComplete(rebuildResult);
    } catch (err) {
      const msg =
        err instanceof Error ? err.message : "Rebuild failed unexpectedly.";
      setError(msg);
    } finally {
      setIsRebuilding(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="rebuild-dialog-title"
    >
      <div className="relative w-full max-w-md rounded-2xl border border-white/10 bg-[#1a1a2e] p-6 shadow-2xl">
        {/* Header */}
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h2
              id="rebuild-dialog-title"
              className="text-base font-semibold text-white"
            >
              Rebuild Workspace Index
            </h2>
            <p className="mt-1 text-sm text-white/50">
              Re-embed all documents using the current embedding model.
            </p>
          </div>
          <button
            id="rebuild-dialog-close"
            onClick={onClose}
            aria-label="Close dialog"
            className="mt-0.5 rounded-lg p-1 text-white/40 transition hover:bg-white/10 hover:text-white"
          >
            ✕
          </button>
        </div>

        {/* Index health summary */}
        <div className="mb-5 rounded-xl border border-white/10 bg-white/5 p-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-white/60">Current status</span>
            <IndexHealthBadge status={health.status} />
          </div>
          <div className="mt-3 grid grid-cols-4 gap-2 text-center text-xs">
            <div className="rounded-lg bg-emerald-500/10 p-2">
              <div className="font-semibold text-emerald-400">
                {health.readyDocuments}
              </div>
              <div className="text-white/50">Ready</div>
            </div>
            <div className="rounded-lg bg-amber-500/10 p-2">
              <div className="font-semibold text-amber-400">
                {health.staleDocuments}
              </div>
              <div className="text-white/50">Stale</div>
            </div>
            <div className="rounded-lg bg-blue-500/10 p-2">
              <div className="font-semibold text-blue-400">
                {health.pendingDocuments}
              </div>
              <div className="text-white/50">Pending</div>
            </div>
            <div className="rounded-lg bg-red-500/10 p-2">
              <div className="font-semibold text-red-400">
                {health.failedDocuments}
              </div>
              <div className="text-white/50">Failed</div>
            </div>
          </div>
          <div className="mt-3 text-xs text-white/40">
            Active model:{" "}
            <span className="font-mono text-white/70">
              {health.activeEmbeddingIdentity.model}
            </span>{" "}
            ({health.activeEmbeddingIdentity.dimensions}d)
          </div>
        </div>

        {/* Result */}
        {result && (
          <div
            className={`mb-4 rounded-xl border p-3 text-sm ${
              result.status === "COMPLETED"
                ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                : "border-amber-500/30 bg-amber-500/10 text-amber-300"
            }`}
          >
            {result.status === "COMPLETED"
              ? `✓ Rebuild complete — ${result.rebuiltCount} document${result.rebuiltCount === 1 ? "" : "s"} re-indexed.`
              : `⚠ Partial rebuild — ${result.rebuiltCount} succeeded, ${result.failedCount} failed.`}
            {result.errors.length > 0 && (
              <ul className="mt-2 list-disc pl-4 text-xs text-amber-400/80">
                {result.errors.map((e) => (
                  <li key={e.documentId}>
                    {e.filename}: {e.errorMessage}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2">
          <button
            id="rebuild-dialog-confirm"
            onClick={handleRebuild}
            disabled={isRebuilding || !!result}
            className="flex-1 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isRebuilding
              ? "Rebuilding…"
              : result
                ? "Done"
                : `Rebuild ${health.totalDocuments} document${health.totalDocuments === 1 ? "" : "s"}`}
          </button>
          <button
            id="rebuild-dialog-cancel"
            onClick={onClose}
            disabled={isRebuilding}
            className="rounded-xl border border-white/10 px-4 py-2.5 text-sm font-medium text-white/60 transition hover:border-white/20 hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {result ? "Close" : "Cancel"}
          </button>
        </div>
      </div>
    </div>
  );
}
