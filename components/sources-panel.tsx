'use client'

import { useState } from 'react'
import { ChevronDown, FileText, X, Link } from 'lucide-react'

interface SourceCard {
  id: string
  title: string
  relevance: number
  excerpt: string
  workspace: string
  fileType: string
}

export default function SourcesPanel() {
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const sources: SourceCard[] = [
    {
      id: '1',
      title: 'Q3 Financial Report',
      relevance: 0.92,
      excerpt: 'Revenue increased by 23% year-over-year, with significant growth in enterprise segment...',
      workspace: 'Finance',
      fileType: 'PDF',
    },
    {
      id: '2',
      title: 'Budget Analysis 2024',
      relevance: 0.87,
      excerpt: 'The proposed budget allocation shows a 15% increase in R&D spending...',
      workspace: 'Operations',
      fileType: 'Document',
    },
    {
      id: '3',
      title: 'Team Restructuring Notes',
      relevance: 0.79,
      excerpt: 'Key discussion points included team optimization and resource allocation...',
      workspace: 'HR',
      fileType: 'Note',
    },
  ]

  return (
    <aside className="hidden lg:flex flex-col w-80 bg-card border-l border-border h-screen overflow-hidden">
      {/* Header */}
      <div className="p-4 border-b border-border">
        <h2 className="font-semibold text-foreground mb-1">Retrieved Context</h2>
        <p className="text-xs text-muted-foreground">Sources related to your query</p>
      </div>

      {/* Sources List */}
      <div className="flex-1 overflow-y-auto">
        {sources.length === 0 ? (
          <div className="p-6 text-center">
            <div className="w-12 h-12 bg-muted rounded-lg flex items-center justify-center mx-auto mb-3">
              <FileText size={24} className="text-muted-foreground" />
            </div>
            <p className="text-sm text-muted-foreground">No sources retrieved yet</p>
            <p className="text-xs text-muted-foreground/60 mt-1">Start a conversation to see related documents</p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {sources.map((source) => (
              <div
                key={source.id}
                className="p-4 hover:bg-muted/30 transition-colors cursor-pointer"
                onClick={() => setExpandedId(expandedId === source.id ? null : source.id)}
              >
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <FileText size={16} className="text-primary flex-shrink-0" />
                      <h3 className="font-medium text-sm text-foreground truncate">{source.title}</h3>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs px-2 py-0.5 bg-primary/10 text-primary rounded">
                        {source.fileType}
                      </span>
                      <span className="text-xs text-muted-foreground">{source.workspace}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <div className="text-right">
                      <p className="text-xs font-medium text-foreground">{Math.round(source.relevance * 100)}%</p>
                      <p className="text-xs text-muted-foreground">match</p>
                    </div>
                    <ChevronDown
                      size={16}
                      className={`transition-transform ${expandedId === source.id ? 'rotate-180' : ''}`}
                    />
                  </div>
                </div>

                {expandedId === source.id && (
                  <div className="mt-3 pt-3 border-t border-border/50">
                    <p className="text-xs text-muted-foreground leading-relaxed mb-3">{source.excerpt}</p>
                    <div className="flex gap-2">
                      <button className="flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded bg-primary/10 hover:bg-primary/20 text-primary text-xs font-medium transition-colors">
                        <Link size={12} />
                        Jump to source
                      </button>
                      <button className="p-1.5 rounded hover:bg-muted transition-colors text-muted-foreground hover:text-foreground">
                        <X size={14} />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Footer Info */}
      <div className="p-4 border-t border-border text-xs text-muted-foreground bg-muted/20">
        <p>Click on a source to expand and view excerpts</p>
      </div>
    </aside>
  )
}
