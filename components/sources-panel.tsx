"use client";

import { useState } from "react";
import {
  ChevronDown,
  FileText,
  Sparkles,
  X,
} from "lucide-react";
import { Citation } from "@/lib/api";
import { EmptyState } from "@/components/ui-state";
import { Button } from "@/components/ui/button";

interface SourcesPanelProps {
  citations: Citation[];
  onClose?: () => void;
}

export default function SourcesPanel({
  citations,
  onClose,
}: SourcesPanelProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <aside
      className="flex flex-col w-80 max-w-[40vw] bg-card border-l border-border h-screen overflow-hidden"
      data-testid="sources-panel"
      aria-label="Retrieved context and provenance"
    >
      <div className="p-4 border-b border-border bg-card/40 flex items-start justify-between gap-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Sparkles size={16} className="text-primary" aria-hidden />
            <h2 className="font-semibold text-sm text-foreground">
              Retrieved Context & Provenance
            </h2>
          </div>
          <p className="text-xs text-muted-foreground">
            Source chunks used to ground response
          </p>
        </div>
        {onClose ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="Hide sources"
            onClick={onClose}
          >
            <X size={16} />
          </Button>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto">
        {citations.length === 0 ? (
          <EmptyState
            title="No sources cited yet"
            description="Ask a question to see retrieved document chunks. This is not an error."
            className="h-full"
          />
        ) : (
          <div className="divide-y divide-border">
            {citations.map((citation, index) => {
              const citId = citation.chunkId || `cit-${index}`;
              const isExpanded = expandedId === citId;

              let locatorText = "Document Section";
              try {
                if (citation.sourceLocator) {
                  const loc = JSON.parse(citation.sourceLocator);
                  if (loc.location) locatorText = loc.location;
                }
              } catch {
                // Default locator text
              }

              return (
                <div
                  key={citId}
                  className="p-4 hover:bg-muted/30 transition-colors cursor-pointer"
                  onClick={() => setExpandedId(isExpanded ? null : citId)}
                >
                  <div className="flex items-start justify-between gap-3 mb-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-bold text-primary bg-primary/10 px-1.5 py-0.5 rounded">
                          [{index + 1}]
                        </span>
                        <h3
                          className="font-medium text-xs text-foreground truncate"
                          title={citation.documentFilename}
                        >
                          {citation.documentFilename}
                        </h3>
                      </div>
                      <div className="flex items-center gap-2 flex-wrap text-[11px] text-muted-foreground">
                        <span>Chunk #{citation.ordinal}</span>
                        <span>•</span>
                        <span className="truncate" title={locatorText}>
                          {locatorText}
                        </span>
                      </div>
                    </div>
                    <ChevronDown
                      size={14}
                      className={`text-muted-foreground transition-transform ${isExpanded ? "rotate-180" : ""}`}
                      aria-hidden
                    />
                  </div>
                  {isExpanded ? (
                    <p className="text-xs text-muted-foreground leading-relaxed mt-2 whitespace-pre-wrap">
                      {citation.snippet}
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground line-clamp-2">
                      {citation.snippet}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </aside>
  );
}
