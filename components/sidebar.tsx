"use client";

import { useState, useEffect } from "react";
import {
  Menu,
  X,
  Plus,
  MessageSquare,
  FileText,
  Trash2,
  FolderPlus,
  RefreshCw,
  Upload,
  CheckCircle2,
  AlertCircle,
  Clock,
  PencilIcon,
  Pencil,
} from "lucide-react";
import { Button } from "./ui/button";
import {
  Workspace,
  DocumentItem,
  Conversation,
  IndexHealthResponse,
  RebuildResponse,
  fetchWorkspaces,
  createWorkspace,
  deleteWorkspace,
  renameWorkspace,
  fetchDocuments,
  uploadDocument,
  deleteDocument,
  fetchConversations,
  createConversation,
  deleteConversation,
  renameConversation,
  getIndexHealth,
} from "@/lib/api";
import { messageForApiError } from "@/lib/api-error-messages";
import { IndexHealthBadge } from "@/components/workspace/index-health-badge";
import { RebuildIndexDialog } from "@/components/workspace/rebuild-index-dialog";

interface SidebarProps {
  currentWorkspaceId: string | null;
  onSelectWorkspace: (id: string | null) => void;
  currentConversationId: string | null;
  onSelectConversation: (id: string | null) => void;
  /** Fired only after successful create (bumps compose focus in parent). */
  onConversationCreated?: (id: string) => void;
  activeTab: "chat" | "documents";
  onSelectTab: (tab: "chat" | "documents") => void;
  refreshTrigger: number;
  onRefresh: () => void;
  navOpen?: boolean;
  onNavOpenChange?: (open: boolean) => void;
  requestCreateWorkspace?: number;
  requestStartConversation?: number;
}

import { ModalIdName } from "./modal-id-name";
import { EmptyState, ErrorBanner, LoadingRegion } from "@/components/ui-state";
import { filterByName } from "@/lib/list-filter";
import { AppearanceMode, getAppearance, setAppearance } from "@/lib/appearance";

export default function Sidebar({
  currentWorkspaceId,
  onSelectWorkspace,
  currentConversationId,
  onSelectConversation,
  onConversationCreated,
  activeTab,
  onSelectTab,
  refreshTrigger,
  onRefresh,
  navOpen,
  onNavOpenChange,
  requestCreateWorkspace = 0,
  requestStartConversation = 0,
}: SidebarProps) {
  const [isOpen, setIsOpen] = useState(true);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [showNewWorkspaceModal, setShowNewWorkspaceModal] = useState(false);
  const [newWorkspaceName, setNewWorkspaceName] = useState("");
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [createConversationError, setCreateConversationError] = useState<
    string | null
  >(null);
  const [restoreCreateModalFocus, setRestoreCreateModalFocus] = useState(true);
  const [workspaceFilter, setWorkspaceFilter] = useState("");
  const [conversationFilter, setConversationFilter] = useState("");
  const [appearance, setAppearanceState] = useState<AppearanceMode>("system");

  //Renaming Logic
  const [showRenameWorkspaceModal, setShowRenameWorkspaceModal] =
    useState(false);
  const [renameWorkspaceText, setRenameWorkspaceText] = useState("");
  const [renamingWorkspaceId, setRenamingWorkspaceId] = useState<string>("");

  //Delete Logic
  const [showDeleteWorkspaceModal, setShowDeleteWorkspaceModal] =
    useState(false);
  const [deleteWorkspaceId, setDeleteWorkspaceId] = useState("");
  const [deleteWorkspaceName, setDeleteWorkspaceName] = useState("");
  const [deleteWorkspaceError, setDeleteWorkspaceError] = useState<
    string | null
  >(null);
  const [isDeletingWorkspace, setIsDeletingWorkspace] = useState(false);
  const [workspaceMutationError, setWorkspaceMutationError] = useState<
    string | null
  >(null);
  const [itemMutationError, setItemMutationError] = useState<string | null>(
    null,
  );

  //NewConversationModal
  const [showNewConversationModal, setShowNewConversationModal] =
    useState(false);
  const [newConverSationName, setNewConversationName] = useState("");

  //Rename Conversation Modal
  const [showRenameConvoModal, setShowRenameConvoModal] = useState(false);
  const [renameConvoText, setRenameConvoText] = useState("");

  // Index Health
  const [indexHealth, setIndexHealth] = useState<IndexHealthResponse | null>(
    null,
  );
  const [showRebuildDialog, setShowRebuildDialog] = useState(false);

  useEffect(() => {
    setAppearanceState(getAppearance());
  }, []);

  useEffect(() => {
    if (currentWorkspaceId && activeTab === "documents") {
      getIndexHealth(currentWorkspaceId)
        .then(setIndexHealth)
        .catch(() => setIndexHealth(null));
    } else {
      setIndexHealth(null);
    }
  }, [currentWorkspaceId, activeTab, documents]);

  useEffect(() => {
    if (requestCreateWorkspace > 0) {
      setShowNewWorkspaceModal(true);
    }
  }, [requestCreateWorkspace]);

  const openNewConversationModal = () => {
    setCreateConversationError(null);
    setRestoreCreateModalFocus(true);
    setShowNewConversationModal(true);
  };

  useEffect(() => {
    if (requestStartConversation > 0) {
      onSelectTab("chat");
      openNewConversationModal();
    }
  }, [requestStartConversation, onSelectTab]);

  useEffect(() => {
    if (typeof navOpen === "boolean") {
      setIsOpen(navOpen);
    }
  }, [navOpen]);

  const setSidebarOpen = (open: boolean) => {
    setIsOpen(open);
    onNavOpenChange?.(open);
  };

  // Load Workspaces
  useEffect(() => {
    loadWorkspaces();
  }, [refreshTrigger]);

  // Load Workspace Documents & Conversations when workspace changes
  useEffect(() => {
    if (currentWorkspaceId) {
      loadWorkspaceData(currentWorkspaceId);
    } else {
      setDocuments([]);
      setConversations([]);
    }
  }, [currentWorkspaceId, refreshTrigger]);

  const loadWorkspaces = async () => {
    try {
      setIsLoading(true);
      setListError(null);
      const data = await fetchWorkspaces();
      setWorkspaces(data);
    } catch (err) {
      console.error("Failed to load workspaces", err);
      setListError("Could not load workspaces.");
    } finally {
      setIsLoading(false);
    }
  };

  const loadWorkspaceData = async (wsId: string) => {
    try {
      const [docs, convs] = await Promise.all([
        fetchDocuments(wsId).catch(() => []),
        fetchConversations(wsId).catch(() => []),
      ]);
      setDocuments(docs);
      setConversations(convs);
    } catch (err) {
      console.error("Error loading workspace items", err);
    }
  };

  const handleCreateWorkspace = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWorkspaceName.trim()) return;
    setWorkspaceMutationError(null);
    try {
      const ws = await createWorkspace(newWorkspaceName.trim());
      setNewWorkspaceName("");
      setShowNewWorkspaceModal(false);
      await loadWorkspaces();
      onSelectWorkspace(ws.id);
    } catch (err) {
      setWorkspaceMutationError(messageForApiError(err, "create workspace"));
    }
  };

  const openRenameModal = (ws: Workspace) => {
    setRenamingWorkspaceId(ws.id);
    setRenameWorkspaceText(ws.name); // pre-fill input with current name
    setShowRenameWorkspaceModal(true);
  };

  const handleRenameWorkspace = async (e: React.FormEvent) => {
    e.preventDefault(); //prevents default focus
    if (!renameWorkspaceText.trim() || !renamingWorkspaceId) return;
    setWorkspaceMutationError(null);
    try {
      const ws = await renameWorkspace(
        renamingWorkspaceId,
        renameWorkspaceText,
      );
      setShowRenameWorkspaceModal(false);
      setRenamingWorkspaceId("");
      setRenameWorkspaceText("");
      await loadWorkspaces();
      onSelectWorkspace(ws.id);
    } catch (err) {
      setWorkspaceMutationError(messageForApiError(err, "rename workspace"));
    }
  };

  const handleDeleteWorkspace = async (id: string, name: string) => {
    if (!id.trim() || !name.trim()) return;
    setDeleteWorkspaceError(null);
    setIsDeletingWorkspace(true);
    try {
      await deleteWorkspace(id);
      const updated = workspaces.filter((w) => w.id !== id);
      setWorkspaces(updated);
      setShowDeleteWorkspaceModal(false);
      setDeleteWorkspaceId("");
      setDeleteWorkspaceName("");
      if (currentWorkspaceId === id) {
        if (updated.length > 0) {
          onSelectWorkspace(updated[0].id);
        } else {
          onSelectWorkspace(null);
          setDocuments([]);
          setConversations([]);
        }
      }
    } catch (err) {
      setDeleteWorkspaceError(messageForApiError(err, "delete workspace"));
    } finally {
      setIsDeletingWorkspace(false);
    }
  };

  //New Conversation and Modal
  const handleNewConversation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentWorkspaceId) return;
    const title = newConverSationName.trim();
    if (!title) return;
    setCreateConversationError(null);
    try {
      const conv = await createConversation(currentWorkspaceId, title);
      setConversations((prev) => [conv, ...prev]);
      onSelectConversation(conv.id);
      onSelectTab("chat");
      onConversationCreated?.(conv.id);
      setRestoreCreateModalFocus(false);
      setShowNewConversationModal(false);
      setNewConversationName("");
    } catch (err) {
      setCreateConversationError(
        messageForApiError(err, "create conversation"),
      );
    }
  };
  //

  const handleDeleteConversation = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    if (!currentWorkspaceId) return;
    setItemMutationError(null);
    try {
      await deleteConversation(currentWorkspaceId, id);
      setConversations((prev) => prev.filter((c) => c.id !== id));
      if (currentConversationId === id) {
        onSelectConversation(null);
      }
    } catch (err) {
      setItemMutationError(messageForApiError(err, "delete conversation"));
    }
  };

  const handleRenameConversation = async () => {
    if (!currentConversationId || !currentWorkspaceId) return;
    setItemMutationError(null);
    try {
      await renameConversation(
        currentWorkspaceId,
        currentConversationId,
        renameConvoText,
      );
      setConversations(
        await fetchConversations(currentWorkspaceId).catch(() => []),
      );
    } catch (err) {
      setItemMutationError(messageForApiError(err, "rename conversation"));
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0 || !currentWorkspaceId) return;
    setIsUploading(true);
    setUploadError(null);
    const wsId = currentWorkspaceId;
    try {
      for (let i = 0; i < files.length; i++) {
        await uploadDocument(wsId, files[i]);
      }
      // Initial fetch to show the document immediately
      await loadWorkspaceData(wsId);
      onRefresh();

      // Poll every 2s until all documents are out of PENDING/PROCESSING state
      const deadline = Date.now() + 2 * 60 * 1000; // 2 minute timeout
      const intervalId = setInterval(async () => {
        if (Date.now() > deadline) {
          clearInterval(intervalId);
          return;
        }
        try {
          const docs = await fetchDocuments(wsId);
          setDocuments(docs);
          const stillProcessing = docs.some(
            (d) => d.status === "PENDING" || d.status === "PROCESSING",
          );
          if (!stillProcessing) {
            clearInterval(intervalId);
            onRefresh();
          }
        } catch {
          clearInterval(intervalId);
        }
      }, 2000);
    } catch (err: unknown) {
      setUploadError(messageForApiError(err, "upload document"));
    } finally {
      setIsUploading(false);
      e.target.value = "";
    }
  };

  const handleDeleteDocument = async (e: React.MouseEvent, docId: string) => {
    e.stopPropagation();
    if (!currentWorkspaceId) return;
    setItemMutationError(null);
    try {
      await deleteDocument(currentWorkspaceId, docId);
      setDocuments((prev) => prev.filter((d) => d.id !== docId));
      onRefresh();
    } catch (err) {
      setItemMutationError(messageForApiError(err, "delete document"));
    }
  };

  const currentWorkspace = workspaces.find((w) => w.id === currentWorkspaceId);
  const filteredWorkspaces = filterByName(workspaces, workspaceFilter);
  const filteredConversations = filterByName(
    conversations,
    conversationFilter,
    (c) => c.title,
  );

  const handleAppearanceChange = (mode: AppearanceMode) => {
    setAppearance(mode);
    setAppearanceState(mode);
  };

  return (
    <>
      {/* Mobile Toggle Button */}
      <button
        onClick={() => setSidebarOpen(!isOpen)}
        className="fixed top-4 left-4 z-50 md:hidden bg-card border border-border rounded-lg p-2 shadow-md"
        aria-label="Toggle navigation"
      >
        {isOpen ? <X size={20} /> : <Menu size={20} />}
      </button>

      {/* Mobile Overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar Container */}
      <aside
        data-testid="nav-sidebar"
        className={`fixed md:relative top-0 left-0 h-screen w-72 bg-sidebar border-r border-sidebar-border flex flex-col transition-transform duration-300 z-40 md:z-0 ${
          isOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
        }`}
      >
        {/* Header Branding */}
        <div className="p-5 border-b border-sidebar-border flex items-center justify-between gap-2">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 bg-primary/90 text-primary-foreground font-bold rounded-lg flex items-center justify-center shadow shrink-0">
              A
            </div>
            <div className="min-w-0">
              <h1 className="font-bold text-base tracking-tight text-sidebar-foreground">
                Atlas
              </h1>
              <p className="text-xs text-muted-foreground">
                AI Knowledge Workspace
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <button
              type="button"
              onClick={() => loadWorkspaces()}
              className="p-1.5 rounded-lg text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground transition-colors"
              title="Refresh workspaces"
              aria-label="Refresh workspaces"
            >
              <RefreshCw
                size={14}
                className={isLoading ? "animate-spin" : ""}
              />
            </button>
            <button
              type="button"
              onClick={() => setSidebarOpen(false)}
              className="hidden md:inline-flex p-1.5 rounded-lg text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground transition-colors"
              title="Hide navigation"
              aria-label="Hide navigation"
            >
              <X size={14} />
            </button>
          </div>
        </div>

        {listError ? (
          <ErrorBanner
            message={listError}
            onRetry={() => void loadWorkspaces()}
            onDismiss={() => setListError(null)}
          />
        ) : null}

        {createConversationError ? (
          <ErrorBanner
            message={createConversationError}
            onRetry={() => {
              openNewConversationModal();
            }}
            onDismiss={() => setCreateConversationError(null)}
          />
        ) : null}
        {workspaceMutationError ? (
          <ErrorBanner
            message={workspaceMutationError}
            onDismiss={() => setWorkspaceMutationError(null)}
          />
        ) : null}
        {itemMutationError ? (
          <ErrorBanner
            message={itemMutationError}
            onDismiss={() => setItemMutationError(null)}
          />
        ) : null}

        {/* Content Navigation */}
        <nav className="flex-1 overflow-y-auto p-4 space-y-6">
          {/* Workspace Switcher */}
          <div>
            <div className="flex items-center justify-between mb-2 px-1">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Workspaces
              </label>
              <button
                type="button"
                onClick={() => setShowNewWorkspaceModal(true)}
                className="text-xs text-primary hover:underline flex items-center gap-1 font-medium"
              >
                <FolderPlus size={12} /> New
              </button>
            </div>

            <label className="sr-only" htmlFor="workspace-filter">
              Filter workspaces
            </label>
            <input
              id="workspace-filter"
              type="search"
              value={workspaceFilter}
              onChange={(e) => setWorkspaceFilter(e.target.value)}
              placeholder="Filter workspaces…"
              className="w-full mb-2 px-3 py-1.5 rounded-lg bg-input border border-border text-xs text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />

            <div className="space-y-1">
              {isLoading ? <LoadingRegion label="Loading workspaces" /> : null}
              {!isLoading && workspaces.length === 0 ? (
                <EmptyState
                  title="No workspaces yet"
                  description="Create a workspace to organize your knowledge."
                  actionLabel="Create workspace"
                  onAction={() => setShowNewWorkspaceModal(true)}
                />
              ) : null}
              {!isLoading && filteredWorkspaces.emptyReason === "no-matches" ? (
                <EmptyState
                  title="No matches"
                  description="No workspaces match this filter."
                  actionLabel="Clear filter"
                  onAction={() => setWorkspaceFilter("")}
                />
              ) : null}
              {filteredWorkspaces.items.map((ws) => (
                <div
                  key={ws.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => onSelectWorkspace(ws.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      onSelectWorkspace(ws.id);
                    }
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer group ${
                    currentWorkspaceId === ws.id
                      ? "bg-sidebar-primary/15 text-sidebar-foreground ring-1 ring-sidebar-primary/40 shadow-sm"
                      : "hover:bg-sidebar-accent/50 text-sidebar-foreground/80"
                  }`}
                >
                  <div className="flex items-center gap-2 truncate min-w-0">
                    <div
                      className={`w-2.5 h-2.5 rounded-full shrink-0 ${currentWorkspaceId === ws.id ? "bg-primary" : "bg-muted"}`}
                    />
                    <span className="truncate" title={ws.name}>
                      {ws.name}
                    </span>
                  </div>
                  <div className="flex items-center gap-0.5 shrink-0">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setDeleteWorkspaceError(null);
                        setDeleteWorkspaceId(ws.id);
                        setDeleteWorkspaceName(ws.name);
                        setShowDeleteWorkspaceModal(true);
                      }}
                      className="opacity-0 group-hover:opacity-100 p-1 hover:text-destructive transition-opacity cursor-pointer"
                      title="Delete workspace"
                    >
                      <Trash2 size={12} />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setWorkspaceMutationError(null);
                        openRenameModal(ws);
                      }}
                      className="opacity-70 group-hover:opacity-100 p-1 hover:text-zinc-600 transition-opacity cursor-pointer"
                      title="Rename Workspace"
                    >
                      <PencilIcon size={12} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Navigation View Modes */}
          <div className="grid grid-cols-2 gap-1 bg-sidebar-accent/40 p-1 rounded-lg">
            <button
              onClick={() => onSelectTab("chat")}
              className={`flex items-center justify-center gap-2 py-1.5 rounded-md text-xs font-medium transition-colors ${
                activeTab === "chat"
                  ? "bg-card text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <MessageSquare size={14} /> Chat
            </button>
            <button
              onClick={() => onSelectTab("documents")}
              className={`flex items-center justify-center gap-2 py-1.5 rounded-md text-xs font-medium transition-colors ${
                activeTab === "documents"
                  ? "bg-card text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <FileText size={14} /> Docs ({documents.length})
            </button>
          </div>

          {/* Recent Conversations */}
          {activeTab === "chat" && (
            <div>
              <div className="flex items-center justify-between mb-2 px-1">
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Conversations
                </label>
                <button
                  type="button"
                  onClick={openNewConversationModal}
                  disabled={!currentWorkspaceId}
                  className="text-xs text-primary hover:underline flex items-center gap-1 font-medium disabled:opacity-40"
                >
                  <Plus size={12} /> New
                </button>
              </div>
              <label className="sr-only" htmlFor="conversation-filter">
                Filter conversations
              </label>
              <input
                id="conversation-filter"
                type="search"
                value={conversationFilter}
                onChange={(e) => setConversationFilter(e.target.value)}
                placeholder="Filter conversations…"
                disabled={!currentWorkspaceId}
                className="w-full mb-2 px-3 py-1.5 rounded-lg bg-input border border-border text-xs text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
              />
              <div className="space-y-1">
                {currentWorkspaceId && conversations.length === 0 ? (
                  <EmptyState
                    title="No conversations yet"
                    description="Start a conversation to ask grounded questions."
                    actionLabel="Start conversation"
                    onAction={openNewConversationModal}
                  />
                ) : null}
                {filteredConversations.emptyReason === "no-matches" ? (
                  <EmptyState
                    title="No matches"
                    description="No conversations match this filter."
                    actionLabel="Clear filter"
                    onAction={() => setConversationFilter("")}
                  />
                ) : null}
                {filteredConversations.items.map((c) => (
                  <div
                    key={c.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => onSelectConversation(c.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onSelectConversation(c.id);
                      }
                    }}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm transition-colors cursor-pointer group ${
                      currentConversationId === c.id
                        ? "bg-primary/15 text-primary font-medium ring-1 ring-primary/30"
                        : "hover:bg-sidebar-accent/50 text-sidebar-foreground/70"
                    }`}
                  >
                    <span className="truncate flex-1" title={c.title}>
                      {c.title}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => handleDeleteConversation(e, c.id)}
                      className="opacity-0 group-hover:opacity-100 p-1 hover:text-destructive transition-opacity"
                      title="Delete conversation"
                      aria-label={`Delete ${c.title}`}
                    >
                      <Trash2 size={12} />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setRenameConvoText(c.title);
                        onSelectConversation(c.id);
                        setShowRenameConvoModal(true);
                      }}
                      aria-label={`Rename ${c.title}`}
                    >
                      <Pencil size={12} />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Documents View */}
          {activeTab === "documents" && (
            <div className="space-y-3">
              <div className="flex items-center justify-between px-1">
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Workspace Sources
                </label>
              </div>

              {currentWorkspaceId && documents.length === 0 ? (
                <EmptyState
                  title="No documents yet"
                  description="Upload a source to ground answers in this workspace."
                  actionLabel="Upload source"
                  onAction={() => {
                    document.getElementById("atlas-doc-upload")?.click();
                  }}
                />
              ) : null}

              {/* Upload Drop Area */}
              <label className="border-2 border-dashed border-sidebar-border hover:border-primary/50 rounded-lg p-3 flex flex-col items-center justify-center cursor-pointer transition-colors text-center bg-card/40">
                <Upload size={18} className="text-muted-foreground mb-1" />
                <span className="text-xs font-medium text-foreground">
                  Upload Document
                </span>
                <span className="text-[10px] text-muted-foreground mt-0.5">
                  PDF, Markdown, Plain Text
                </span>
                <input
                  id="atlas-doc-upload"
                  type="file"
                  multiple
                  accept=".pdf,.md,.txt,text/plain,application/pdf"
                  className="hidden"
                  onChange={handleFileUpload}
                  disabled={isUploading}
                />
              </label>

              {isUploading ? (
                <LoadingRegion label="Ingesting documents…" />
              ) : null}

              {uploadError ? (
                <ErrorBanner
                  message={uploadError}
                  onRetry={() => setUploadError(null)}
                  onDismiss={() => setUploadError(null)}
                />
              ) : null}

              <div className="space-y-1.5 pt-1">
                {documents.map((doc) => (
                  <div
                    key={doc.id}
                    className="p-2.5 rounded-lg border border-sidebar-border bg-card/60 flex items-start justify-between gap-2 group"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5 mb-1">
                        <FileText
                          size={14}
                          className="text-primary flex-shrink-0"
                        />
                        <span
                          className="text-xs font-medium text-foreground truncate"
                          title={doc.filename}
                        >
                          {doc.filename}
                        </span>
                      </div>
                      <div className="flex flex-col gap-0.5 text-[10px]">
                        <div className="flex items-center gap-2">
                          <StatusBadge
                            status={doc.status}
                            failureReason={doc.failureReason}
                          />
                          <span className="text-muted-foreground">
                            {new Date(doc.createdAt).toLocaleDateString()}
                          </span>
                        </div>
                        {doc.status === "FAILED" && doc.failureReason ? (
                          <p
                            className="text-rose-500/90 truncate"
                            title={doc.failureReason}
                          >
                            {doc.failureReason}
                          </p>
                        ) : null}
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

              {/* Index Health Section */}
              {indexHealth && currentWorkspaceId && (
                <div className="mt-4 rounded-xl border border-sidebar-border bg-card/40 p-3 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Index Health
                    </span>
                    <IndexHealthBadge status={indexHealth.status} />
                  </div>
                  <div className="grid grid-cols-4 gap-1.5 text-center text-[10px]">
                    <div className="rounded-lg bg-emerald-500/10 py-1.5">
                      <div className="font-semibold text-emerald-400">
                        {indexHealth.readyDocuments}
                      </div>
                      <div className="text-muted-foreground">Ready</div>
                    </div>
                    <div className="rounded-lg bg-amber-500/10 py-1.5">
                      <div className="font-semibold text-amber-400">
                        {indexHealth.staleDocuments}
                      </div>
                      <div className="text-muted-foreground">Stale</div>
                    </div>
                    <div className="rounded-lg bg-blue-500/10 py-1.5">
                      <div className="font-semibold text-blue-400">
                        {indexHealth.pendingDocuments}
                      </div>
                      <div className="text-muted-foreground">Pending</div>
                    </div>
                    <div className="rounded-lg bg-red-500/10 py-1.5">
                      <div className="font-semibold text-red-400">
                        {indexHealth.failedDocuments}
                      </div>
                      <div className="text-muted-foreground">Failed</div>
                    </div>
                  </div>
                  {(indexHealth.status === "STALE" ||
                    indexHealth.status === "FAILED") && (
                    <button
                      id="sidebar-rebuild-index-btn"
                      onClick={() => setShowRebuildDialog(true)}
                      className="w-full rounded-lg border border-amber-500/30 bg-amber-500/10 py-1.5 text-xs font-medium text-amber-400 transition hover:bg-amber-500/20 hover:text-amber-300"
                    >
                      Rebuild Index
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </nav>

        {showRebuildDialog && currentWorkspaceId && indexHealth && (
          <RebuildIndexDialog
            workspaceId={currentWorkspaceId}
            health={indexHealth}
            onClose={() => setShowRebuildDialog(false)}
            onRebuildComplete={(result: RebuildResponse) => {
              setShowRebuildDialog(false);
              // Refresh health after rebuild
              getIndexHealth(currentWorkspaceId)
                .then(setIndexHealth)
                .catch(() => {});
              void result;
            }}
          />
        )}

        {/* Footer */}
        <div className="p-4 border-t border-sidebar-border space-y-3">
          <div>
            <label
              htmlFor="appearance-preference"
              className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-1 mb-1.5 block"
            >
              Appearance
            </label>
            <select
              id="appearance-preference"
              value={appearance}
              onChange={(e) =>
                handleAppearanceChange(e.target.value as AppearanceMode)
              }
              className="w-full px-3 py-2 rounded-lg bg-input border border-border text-xs text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="Appearance preference"
            >
              <option value="system">System</option>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </div>
          <Button
            type="button"
            onClick={openNewConversationModal}
            className="w-full shadow-sm"
            size="sm"
            disabled={!currentWorkspaceId}
          >
            <Plus size={16} /> New Conversation
          </Button>
        </div>
      </aside>

      {/* New Workspace Modal */}
      {
        <ModalIdName
          labelValue={newWorkspaceName}
          title="Create New Workspace"
          inputLabel="Workspace Name"
          labelPlaceHolder="e.g. Research Papers, Finance 2026"
          showModal={showNewWorkspaceModal}
          onChange={(e) => {
            setNewWorkspaceName(e);
          }}
          onCancel={() => {
            setShowNewWorkspaceModal(false);
          }}
          onSubmit={handleCreateWorkspace}
        ></ModalIdName>
      }

      {/* Rename Workspace Modal */}
      {
        <ModalIdName
          labelValue={renameWorkspaceText}
          showModal={showRenameWorkspaceModal}
          onSubmit={handleRenameWorkspace}
          title="Rename Workspace"
          inputLabel=""
          onCancel={() => {
            setRenamingWorkspaceId("");
            setRenameWorkspaceText("");
            setShowRenameWorkspaceModal(false);
          }}
          onChange={(e) => {
            setRenameWorkspaceText(e);
          }}
          labelPlaceHolder="e.g. Research Papers, Coding Projects"
        ></ModalIdName>
      }

      {/* Delete Workspace Modal */}
      {showDeleteWorkspaceModal && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
            <h3 className="text-base font-semibold text-foreground mb-4">
              Delete Workspace
            </h3>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void handleDeleteWorkspace(
                  deleteWorkspaceId,
                  deleteWorkspaceName,
                );
              }}
              className="space-y-4"
            >
              <div>
                <label className="text-xs text-muted-foreground block mb-1">
                  Delete "{deleteWorkspaceName}"?
                </label>
              </div>
              {deleteWorkspaceError ? (
                <ErrorBanner
                  message={deleteWorkspaceError}
                  onDismiss={() => setDeleteWorkspaceError(null)}
                />
              ) : null}
              <div className="flex gap-2 justify-end">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={isDeletingWorkspace}
                  onClick={() => {
                    setShowDeleteWorkspaceModal(false);
                    setDeleteWorkspaceId("");
                    setDeleteWorkspaceName("");
                    setDeleteWorkspaceError(null);
                  }}
                >
                  Cancel
                </Button>
                <Button type="submit" size="sm" disabled={isDeletingWorkspace}>
                  {isDeletingWorkspace ? "Deleting…" : "Confirm"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* TODO Rename Conversation Modal */}
      {
        <ModalIdName
          showModal={showRenameConvoModal}
          labelValue={renameConvoText}
          title="Rename Conversation"
          labelPlaceHolder="e.g. Chapter 1 summary, Audit reports,..."
          inputLabel="Enter new conversation name"
          onCancel={() => {
            setShowRenameConvoModal(false);
            setRenameConvoText("");
          }}
          onChange={(e) => {
            setRenameConvoText(e);
          }}
          onSubmit={(e) => {
            e.preventDefault();
            handleRenameConversation();
            setShowRenameConvoModal(false);
            setRenameConvoText("");
          }}
        ></ModalIdName>
      }

      {/* New Conversation Modal */}
      {
        <ModalIdName
          showModal={showNewConversationModal}
          labelValue={newConverSationName}
          title="Create New Conversation"
          labelPlaceHolder="e.g. Rust Documentation Questions, Tailwind Guide"
          inputLabel="Conversation Name"
          restoreFocus={restoreCreateModalFocus}
          onCancel={() => {
            setNewConversationName("");
            setRestoreCreateModalFocus(true);
            setShowNewConversationModal(false);
          }}
          onChange={(e) => {
            setNewConversationName(e);
          }}
          onSubmit={(e) => {
            void handleNewConversation(e);
          }}
        ></ModalIdName>
      }
    </>
  );
}

function StatusBadge({
  status,
  failureReason,
}: {
  status: DocumentItem["status"];
  failureReason?: string;
}) {
  switch (status) {
    case "COMPLETE":
      return (
        <span className="inline-flex items-center gap-1 text-emerald-500 font-medium">
          <CheckCircle2 size={10} /> Ready
        </span>
      );
    case "PROCESSING":
      return (
        <span className="inline-flex items-center gap-1 text-amber-500 font-medium">
          <RefreshCw size={10} className="animate-spin" /> Processing
        </span>
      );
    case "PENDING":
      return (
        <span className="inline-flex items-center gap-1 text-blue-400 font-medium">
          <Clock size={10} /> Queued
        </span>
      );
    case "FAILED":
      return (
        <span
          className="inline-flex items-center gap-1 text-rose-500 font-medium"
          title={failureReason}
        >
          <AlertCircle size={10} /> Failed
        </span>
      );
    default:
      return null;
  }
}
