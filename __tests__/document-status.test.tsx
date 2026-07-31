import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import Sidebar from "@/components/sidebar";
import {
  fetchConversations,
  fetchDocuments,
  fetchWorkspaces,
} from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    fetchWorkspaces: vi.fn(),
    createWorkspace: vi.fn(),
    deleteWorkspace: vi.fn(),
    renameWorkspace: vi.fn(),
    fetchDocuments: vi.fn(),
    uploadDocument: vi.fn(),
    deleteDocument: vi.fn(),
    fetchConversations: vi.fn(),
    createConversation: vi.fn(),
    deleteConversation: vi.fn(),
    renameConversation: vi.fn(),
  };
});

describe("document status labels (US4)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-1", name: "Alpha", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    vi.mocked(fetchConversations).mockResolvedValue([]);
  });

  it("shows Queued, Processing, Ready, and Failed with reason", async () => {
    vi.mocked(fetchDocuments).mockResolvedValue([
      {
        id: "d1",
        filename: "a.md",
        contentType: "text/markdown",
        status: "PENDING",
        createdAt: "2026-01-01T00:00:00Z",
      },
      {
        id: "d2",
        filename: "b.md",
        contentType: "text/markdown",
        status: "PROCESSING",
        createdAt: "2026-01-01T00:00:00Z",
      },
      {
        id: "d3",
        filename: "c.md",
        contentType: "text/markdown",
        status: "COMPLETE",
        createdAt: "2026-01-01T00:00:00Z",
      },
      {
        id: "d4",
        filename: "d.md",
        contentType: "text/markdown",
        status: "FAILED",
        failureReason: "Parse error in PDF",
        createdAt: "2026-01-01T00:00:00Z",
      },
    ]);

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={vi.fn()}
        currentConversationId={null}
        onSelectConversation={vi.fn()}
        activeTab="documents"
        onSelectTab={vi.fn()}
        refreshTrigger={0}
        onRefresh={vi.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByText("Queued")).toBeInTheDocument());
    expect(screen.getByText("Processing")).toBeInTheDocument();
    expect(screen.getByText("Ready")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText("Parse error in PDF")).toBeInTheDocument();
  });
});
