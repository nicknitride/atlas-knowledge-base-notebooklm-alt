import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Sidebar from "@/components/sidebar";
import {
  deleteWorkspace,
  fetchConversations,
  fetchDocuments,
  fetchWorkspaces,
} from "@/lib/api";
import { AtlasApiError } from "@/lib/api";

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
    fetchConversationDetail: vi.fn(),
    streamChatMessage: vi.fn(),
  };
});

function renderSidebar(
  overrides: Partial<{
    currentWorkspaceId: string | null;
    onSelectWorkspace: (id: string) => void;
  }> = {},
) {
  const onSelectWorkspace = overrides.onSelectWorkspace ?? vi.fn();
  return {
    onSelectWorkspace,
    ...render(
      <Sidebar
        currentWorkspaceId={overrides.currentWorkspaceId ?? "ws-1"}
        onSelectWorkspace={onSelectWorkspace}
        currentConversationId={null}
        onSelectConversation={vi.fn()}
        activeTab="chat"
        onSelectTab={vi.fn()}
        refreshTrigger={0}
        onRefresh={vi.fn()}
      />,
    ),
  };
}

describe("workspace delete (US1)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchDocuments).mockResolvedValue([]);
    vi.mocked(fetchConversations).mockResolvedValue([]);
  });

  it("prevents default form submit, awaits delete once, closes modal, updates list", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-1", name: "Alpha", createdAt: "2026-01-01T00:00:00Z" },
      { id: "ws-2", name: "Beta", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    vi.mocked(deleteWorkspace).mockResolvedValue(undefined);

    const { onSelectWorkspace } = renderSidebar({ currentWorkspaceId: "ws-1" });

    await waitFor(() => {
      expect(screen.getByText("Alpha")).toBeInTheDocument();
    });

    const deleteButtons = screen.getAllByTitle("Delete workspace");
    await user.click(deleteButtons[0]);
    expect(screen.getByText(/Delete "Alpha"\?/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    await waitFor(() => {
      expect(deleteWorkspace).toHaveBeenCalledTimes(1);
      expect(deleteWorkspace).toHaveBeenCalledWith("ws-1");
    });

    await waitFor(() => {
      expect(screen.queryByText(/Delete "Alpha"\?/i)).not.toBeInTheDocument();
      expect(screen.queryByText("Alpha")).not.toBeInTheDocument();
      expect(screen.getByText("Beta")).toBeInTheDocument();
    });
    expect(onSelectWorkspace).toHaveBeenCalledWith("ws-2");
  });

  it("shows delete control when only one workspace exists", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-only", name: "Solo", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    vi.mocked(deleteWorkspace).mockResolvedValue(undefined);

    const { onSelectWorkspace } = renderSidebar({
      currentWorkspaceId: "ws-only",
    });

    await waitFor(() => {
      expect(screen.getByText("Solo")).toBeInTheDocument();
    });

    expect(screen.getByTitle("Delete workspace")).toBeInTheDocument();
    await user.click(screen.getByTitle("Delete workspace"));
    await user.click(screen.getByRole("button", { name: /confirm/i }));

    await waitFor(() => {
      expect(deleteWorkspace).toHaveBeenCalledWith("ws-only");
      expect(screen.queryByText("Solo")).not.toBeInTheDocument();
    });
  });

  it("shows ErrorBanner on delete failure and keeps workspace", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-1", name: "Alpha", createdAt: "2026-01-01T00:00:00Z" },
      { id: "ws-2", name: "Beta", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    vi.mocked(deleteWorkspace).mockRejectedValue(
      new AtlasApiError("PROVIDER_UNAVAILABLE", "Backend unreachable"),
    );

    renderSidebar();

    await waitFor(() => {
      expect(screen.getByText("Alpha")).toBeInTheDocument();
    });

    await user.click(screen.getAllByTitle("Delete workspace")[0]);
    await user.click(screen.getByRole("button", { name: /confirm/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/unreachable|Backend/i);
    });
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText(/Delete "Alpha"\?/i)).toBeInTheDocument();
  });

  it("treats delete 404 success without error banner", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-1", name: "Alpha", createdAt: "2026-01-01T00:00:00Z" },
      { id: "ws-2", name: "Beta", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    // API helper already treats 404 as resolve; simulate success
    vi.mocked(deleteWorkspace).mockResolvedValue(undefined);

    renderSidebar();

    await waitFor(() => {
      expect(screen.getByText("Alpha")).toBeInTheDocument();
    });

    await user.click(screen.getAllByTitle("Delete workspace")[0]);
    await user.click(screen.getByRole("button", { name: /confirm/i }));

    await waitFor(() => {
      expect(screen.queryByText("Alpha")).not.toBeInTheDocument();
    });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
