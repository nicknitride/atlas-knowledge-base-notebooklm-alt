'use client'

import { useState } from 'react'
import { ChevronDown, FileText, Link, Sparkles, ExternalLink } from 'lucide-react'
import { Citation } from '@/lib/api'

interface SourcesPanelProps {
  citations: Citation[]
}

export default function SourcesPanel({ citations }: SourcesPanelProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null)

  return (
    <aside className="hidden lg:flex flex-col w-80 bg-card border-l border-border h-screen overflow-hidden">
      {/* Panel Header */}
      <div className="p-4 border-b border-border bg-card/40">
        <div className="flex items-center gap-2 mb-1">
          <Sparkles size={16} className="text-primary" />
          <h2 className="font-semibold text-sm text-foreground">Retrieved Context & Provenance</h2>
        </div>
        <p className="text-xs text-muted-foreground">Source chunks used to ground response</p>
      </div>

      {/* Sources List */}
      <div className="flex-1 overflow-y-auto">
        {citations.length === 0 ? (
          <div className="p-6 text-center">
            <div className="w-12 h-12 bg-muted/50 rounded-xl flex items-center justify-center mx-auto mb-3">
              <FileText size={24} className="text-muted-foreground" />
            </div>
            <p className="text-sm font-medium text-foreground mb-1">No Sources Cited</p>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Ask a question to see the retrieved document chunks and citation sources.
            </p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {citations.map((citation, index) => {
              const citId = citation.chunkId || `cit-${index}`
              const isExpanded = expandedId === citId

              let locatorText = 'Document Section'
              try {
                if (citation.sourceLocator) {
                  const loc = JSON.parse(citation.sourceLocator)
                  if (loc.location) locatorText = loc.location
                }
              } catch (e) {
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
                        <h3 className="font-medium text-xs text-foreground truncate">{citation.documentFilename}</h3>
                      </div>
                      <div className="flex items-center gap-2 flex-wrap text-[11px] text-muted-foreground">
                        <span>Chunk #{citation.ordinal}</span>
                        <span>•</span>
                        <span className="truncate">{locatorText}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-1.5 flex-shrink-0">
                      {citation.similarity > 0 && (
                        <div className="text-right">
                          <p className="text-[11px] font-semibold text-emerald-500">
                            {Math.round(citation.similarity * 100)}%
                          </p>
                          <p className="text-[9px] text-muted-foreground">match</p>
                        </div>
                      )}
                      <ChevronDown
                        size={14}
                        className={`text-muted-foreground transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                      />
                    </div>
                  </div>

                  {/* Excerpt Box */}
                  <div className="mt-2 text-xs text-muted-foreground bg-muted/20 p-2.5 rounded-lg border border-border/40 font-mono text-[11px] leading-relaxed line-clamp-3">
                    {citation.snippet}
                  </div>

                  {isExpanded && (
                    <div className="mt-3 pt-3 border-t border-border/50 space-y-2">
                      <div className="text-xs text-foreground font-sans leading-relaxed bg-background p-3 rounded-lg border border-border">
                        <p className="font-semibold text-[11px] text-primary mb-1">Full Chunk Content:</p>
                        {citation.snippet}
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-border text-[11px] text-muted-foreground bg-muted/20 text-center">
        {citations.length > 0 ? `${citations.length} document sources verified` : 'Workspace isolated citation engine'}
      </div>
    </aside>
  )
}
