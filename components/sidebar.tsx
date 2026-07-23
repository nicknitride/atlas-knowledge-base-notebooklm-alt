'use client'

import { useState, useEffect } from 'react'
import { Menu, X, Plus, Settings, Search, MessageSquare, FileText, Trash2, FolderPlus, RefreshCw, Upload, CheckCircle2, AlertCircle, Clock } from 'lucide-react'
import { Button } from './ui/button'
import {
  Workspace,
  DocumentItem,
  Conversation,
  fetchWorkspaces,
  createWorkspace,
  deleteWorkspace,
  fetchDocuments,
  uploadDocument,
  deleteDocument,
  fetchConversations,
  createConversation,
  deleteConversation,
} from '@/lib/api'

interface SidebarProps {
  currentWorkspaceId: string | null
  onSelectWorkspace: (id: string) => void
  currentConversationId: string | null
  onSelectConversation: (id: string | null) => void
  activeTab: 'chat' | 'documents'
  onSelectTab: (tab: 'chat' | 'documents') => void
  refreshTrigger: number
  onRefresh: () => void
}

export default function Sidebar({
  currentWorkspaceId,
  onSelectWorkspace,
  currentConversationId,
  onSelectConversation,
  activeTab,
  onSelectTab,
  refreshTrigger,
  onRefresh,
}: SidebarProps) {
  const [isOpen, setIsOpen] = useState(true)
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [showNewWorkspaceModal, setShowNewWorkspaceModal] = useState(false)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [uploadError, setUploadError] = useState<string | null>(null)

  // Load Workspaces
  useEffect(() => {
    loadWorkspaces()
  }, [refreshTrigger])

  // Load Workspace Documents & Conversations when workspace changes
  useEffect(() => {
    if (currentWorkspaceId) {
      loadWorkspaceData(currentWorkspaceId)
    }
  }, [currentWorkspaceId, refreshTrigger])

  const loadWorkspaces = async () => {
    try {
      setIsLoading(true)
      const data = await fetchWorkspaces()
      setWorkspaces(data)
      if (data.length > 0 && !currentWorkspaceId) {
        onSelectWorkspace(data[0].id)
      } else if (data.length === 0) {
        // Auto-create default workspace if none exists
        const defaultWs = await createWorkspace('Default Workspace')
        setWorkspaces([defaultWs])
        onSelectWorkspace(defaultWs.id)
      }
    } catch (err) {
      console.error('Failed to load workspaces', err)
    } finally {
      setIsLoading(false)
    }
  }

  const loadWorkspaceData = async (wsId: string) => {
    try {
      const [docs, convs] = await Promise.all([
        fetchDocuments(wsId).catch(() => []),
        fetchConversations(wsId).catch(() => []),
      ])
      setDocuments(docs)
      setConversations(convs)
    } catch (err) {
      console.error('Error loading workspace items', err)
    }
  }

  const handleCreateWorkspace = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newWorkspaceName.trim()) return
    try {
      const ws = await createWorkspace(newWorkspaceName.trim())
      setNewWorkspaceName('')
      setShowNewWorkspaceModal(false)
      await loadWorkspaces()
      onSelectWorkspace(ws.id)
    } catch (err) {
      console.error(err)
    }
  }

  const handleDeleteWorkspace = async (id: string, name: string) => {
    if (!confirm(`Are you sure you want to delete workspace "${name}"?`)) return
    try {
      await deleteWorkspace(id)
      const updated = workspaces.filter((w) => w.id !== id)
      setWorkspaces(updated)
      if (updated.length > 0) {
        onSelectWorkspace(updated[0].id)
      } else {
        loadWorkspaces()
      }
    } catch (err) {
      console.error(err)
    }
  }

  const handleNewConversation = async () => {
    if (!currentWorkspaceId) return
    try {
      const conv = await createConversation(currentWorkspaceId, 'New conversation')
      setConversations((prev) => [conv, ...prev])
      onSelectConversation(conv.id)
      onSelectTab('chat')
    } catch (err) {
      console.error(err)
    }
  }

  const handleDeleteConversation = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    if (!currentWorkspaceId) return
    try {
      await deleteConversation(currentWorkspaceId, id)
      setConversations((prev) => prev.filter((c) => c.id !== id))
      if (currentConversationId === id) {
        onSelectConversation(null)
      }
    } catch (err) {
      console.error(err)
    }
  }

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || files.length === 0 || !currentWorkspaceId) return
    setIsUploading(true)
    setUploadError(null)
    try {
      for (let i = 0; i < files.length; i++) {
        await uploadDocument(currentWorkspaceId, files[i])
      }
      await loadWorkspaceData(currentWorkspaceId)
      onRefresh()
    } catch (err: any) {
      setUploadError(err.message || 'Failed to upload document')
    } finally {
      setIsUploading(false)
      e.target.value = ''
    }
  }

  const handleDeleteDocument = async (e: React.MouseEvent, docId: string) => {
    e.stopPropagation()
    if (!currentWorkspaceId) return
    try {
      await deleteDocument(currentWorkspaceId, docId)
      setDocuments((prev) => prev.filter((d) => d.id !== docId))
      onRefresh()
    } catch (err) {
      console.error(err)
    }
  }

  const currentWorkspace = workspaces.find((w) => w.id === currentWorkspaceId)

  return (
    <>
      {/* Mobile Toggle Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="fixed top-4 left-4 z-50 md:hidden bg-card border border-border rounded-lg p-2 shadow-md"
        aria-label="Toggle sidebar"
      >
        {isOpen ? <X size={20} /> : <Menu size={20} />}
      </button>

      {/* Mobile Overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 md:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed md:relative top-0 left-0 h-screen w-72 bg-sidebar border-r border-sidebar-border flex flex-col transition-transform duration-300 z-40 md:z-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        }`}
      >
        {/* Header Branding */}
        <div className="p-5 border-b border-sidebar-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-primary/90 text-primary-foreground font-bold rounded-lg flex items-center justify-center shadow">
              A
            </div>
            <div>
              <h1 className="font-bold text-base tracking-tight text-sidebar-foreground">Atlas</h1>
              <p className="text-xs text-muted-foreground">AI Knowledge Workspace</p>
            </div>
          </div>
          <button
            onClick={() => loadWorkspaces()}
            className="p-1.5 rounded-lg text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground transition-colors"
            title="Refresh workspaces"
          >
            <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
          </button>
        </div>

        {/* Content Navigation */}
        <nav className="flex-1 overflow-y-auto p-4 space-y-6">
          {/* Workspace Switcher */}
          <div>
            <div className="flex items-center justify-between mb-2 px-1">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Workspaces
              </label>
              <button
                onClick={() => setShowNewWorkspaceModal(true)}
                className="text-xs text-primary hover:underline flex items-center gap-1 font-medium"
              >
                <FolderPlus size={12} /> New
              </button>
            </div>

            <div className="space-y-1">
              {workspaces.map((ws) => (
                <div
                  key={ws.id}
                  onClick={() => onSelectWorkspace(ws.id)}
                  className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer group ${
                    currentWorkspaceId === ws.id
                      ? 'bg-sidebar-accent text-sidebar-accent-foreground shadow-sm'
                      : 'hover:bg-sidebar-accent/50 text-sidebar-foreground/80'
                  }`}
                >
                  <div className="flex items-center gap-2 truncate">
                    <div className={`w-2.5 h-2.5 rounded-full ${currentWorkspaceId === ws.id ? 'bg-primary' : 'bg-muted'}`} />
                    <span className="truncate">{ws.name}</span>
                  </div>
                  {workspaces.length > 1 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDeleteWorkspace(ws.id, ws.name)
                      }}
                      className="opacity-0 group-hover:opacity-100 p-1 hover:text-destructive transition-opacity"
                      title="Delete workspace"
                    >
                      <Trash2 size={12} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Navigation View Modes */}
          <div className="grid grid-cols-2 gap-1 bg-sidebar-accent/40 p-1 rounded-lg">
            <button
              onClick={() => onSelectTab('chat')}
              className={`flex items-center justify-center gap-2 py-1.5 rounded-md text-xs font-medium transition-colors ${
                activeTab === 'chat' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <MessageSquare size={14} /> Chat
            </button>
            <button
              onClick={() => onSelectTab('documents')}
              className={`flex items-center justify-center gap-2 py-1.5 rounded-md text-xs font-medium transition-colors ${
                activeTab === 'documents' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <FileText size={14} /> Docs ({documents.length})
            </button>
          </div>

          {/* Recent Conversations */}
          {activeTab === 'chat' && (
            <div>
              <div className="flex items-center justify-between mb-2 px-1">
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Conversations
                </label>
              </div>
              <div className="space-y-1">
                {conversations.length === 0 ? (
                  <p className="text-xs text-muted-foreground px-2 py-1">No chats yet in this workspace.</p>
                ) : (
                  conversations.map((c) => (
                    <div
                      key={c.id}
                      onClick={() => onSelectConversation(c.id)}
                      className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm transition-colors cursor-pointer group ${
                        currentConversationId === c.id
                          ? 'bg-primary/10 text-primary font-medium'
                          : 'hover:bg-sidebar-accent/50 text-sidebar-foreground/70'
                      }`}
                    >
                      <span className="truncate flex-1">{c.title}</span>
                      <button
                        onClick={(e) => handleDeleteConversation(e, c.id)}
                        className="opacity-0 group-hover:opacity-100 p-1 hover:text-destructive transition-opacity"
                        title="Delete conversation"
                      >
                        <Trash2 size={12} />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* Documents View */}
          {activeTab === 'documents' && (
            <div className="space-y-3">
              <div className="flex items-center justify-between px-1">
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Workspace Sources
                </label>
              </div>

              {/* Upload Drop Area */}
              <label className="border-2 border-dashed border-sidebar-border hover:border-primary/50 rounded-lg p-3 flex flex-col items-center justify-center cursor-pointer transition-colors text-center bg-card/40">
                <Upload size={18} className="text-muted-foreground mb-1" />
                <span className="text-xs font-medium text-foreground">Upload Document</span>
                <span className="text-[10px] text-muted-foreground mt-0.5">PDF, Markdown, Plain Text</span>
                <input type="file" multiple accept=".pdf,.md,.txt,text/plain,application/pdf" className="hidden" onChange={handleFileUpload} disabled={isUploading} />
              </label>

              {isUploading && (
                <div className="text-xs text-primary flex items-center justify-center gap-2 p-2 bg-primary/10 rounded">
                  <RefreshCw size={12} className="animate-spin" /> Ingesting documents...
                </div>
              )}

              {uploadError && (
                <div className="text-xs text-destructive bg-destructive/10 p-2 rounded flex items-center gap-1.5">
                  <AlertCircle size={12} /> {uploadError}
                </div>
              )}

              <div className="space-y-1.5 pt-1">
                {documents.map((doc) => (
                  <div key={doc.id} className="p-2.5 rounded-lg border border-sidebar-border bg-card/60 flex items-start justify-between gap-2 group">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5 mb-1">
                        <FileText size={14} className="text-primary flex-shrink-0" />
                        <span className="text-xs font-medium text-foreground truncate">{doc.filename}</span>
                      </div>
                      <div className="flex items-center gap-2 text-[10px]">
                        <StatusBadge status={doc.status} />
                        <span className="text-muted-foreground">{new Date(doc.createdAt).toLocaleDateString()}</span>
                      </div>
                    </div>
                    <button
                      onClick={(e) => handleDeleteDocument(e, doc.id)}
                      className="opacity-0 group-hover:opacity-100 p-1 text-muted-foreground hover:text-destructive transition-opacity"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </nav>

        {/* Footer */}
        <div className="p-4 border-t border-sidebar-border space-y-2">
          <Button onClick={handleNewConversation} className="w-full shadow-sm" size="sm">
            <Plus size={16} /> New Conversation
          </Button>
        </div>
      </aside>

      {/* New Workspace Modal */}
      {showNewWorkspaceModal && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
            <h3 className="text-base font-semibold text-foreground mb-4">Create New Workspace</h3>
            <form onSubmit={handleCreateWorkspace} className="space-y-4">
              <div>
                <label className="text-xs text-muted-foreground block mb-1">Workspace Name</label>
                <input
                  type="text"
                  value={newWorkspaceName}
                  onChange={(e) => setNewWorkspaceName(e.target.value)}
                  placeholder="e.g. Research Papers, Finance 2026"
                  className="w-full px-3 py-2 rounded-lg bg-input border border-border text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                  autoFocus
                />
              </div>
              <div className="flex gap-2 justify-end">
                <Button type="button" variant="outline" size="sm" onClick={() => setShowNewWorkspaceModal(false)}>
                  Cancel
                </Button>
                <Button type="submit" size="sm" disabled={!newWorkspaceName.trim()}>
                  Create Workspace
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}

function StatusBadge({ status }: { status: DocumentItem['status'] }) {
  switch (status) {
    case 'COMPLETE':
      return (
        <span className="inline-flex items-center gap-1 text-emerald-500 font-medium">
          <CheckCircle2 size={10} /> Ready
        </span>
      )
    case 'PROCESSING':
      return (
        <span className="inline-flex items-center gap-1 text-amber-500 font-medium">
          <RefreshCw size={10} className="animate-spin" /> Processing
        </span>
      )
    case 'PENDING':
      return (
        <span className="inline-flex items-center gap-1 text-blue-400 font-medium">
          <Clock size={10} /> Queued
        </span>
      )
    case 'FAILED':
      return (
        <span className="inline-flex items-center gap-1 text-rose-500 font-medium">
          <AlertCircle size={10} /> Failed
        </span>
      )
    default:
      return null
  }
}
