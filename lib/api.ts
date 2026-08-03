const API_BASE =
  process.env.NEXT_PUBLIC_ATLAS_API_URL || "http://localhost:8080";

export interface ApiErrorBody {
  code?: string;
  message?: string;
  requestId?: string;
}

export class AtlasApiError extends Error {
  readonly code: string;
  readonly requestId?: string;

  constructor(code: string, message: string, requestId?: string) {
    super(message || code);
    this.name = "AtlasApiError";
    this.code = code;
    this.requestId = requestId;
  }
}

export function isAtlasApiError(err: unknown): err is AtlasApiError {
  return err instanceof AtlasApiError;
}

export async function readApiError(
  res: Response,
  fallback: string,
): Promise<AtlasApiError> {
  const body = (await res.json().catch(() => null)) as ApiErrorBody | null;
  const code = body?.code || "VALIDATION_ERROR";
  const message = body?.message?.trim() || fallback;
  return new AtlasApiError(code, message, body?.requestId);
}

async function throwIfNotOk(res: Response, fallback: string): Promise<void> {
  if (!res.ok) {
    throw await readApiError(res, fallback);
  }
}

/** Treat 404 NOT_FOUND as success for DELETE idempotency. */
async function assertDeleteOk(res: Response, fallback: string): Promise<void> {
  if (res.status === 204 || res.ok) return;
  if (res.status === 404) {
    const body = (await res.json().catch(() => null)) as ApiErrorBody | null;
    if (!body?.code || body.code === "NOT_FOUND") return;
    throw new AtlasApiError(
      body.code,
      body.message?.trim() || fallback,
      body.requestId,
    );
  }
  throw await readApiError(res, fallback);
}

export interface Workspace {
  id: string;
  name: string;
  createdAt: string;
}

export interface DocumentItem {
  id: string;
  filename: string;
  contentType: string;
  status: "PENDING" | "PROCESSING" | "COMPLETE" | "FAILED";
  failureReason?: string;
  createdAt: string;
}

export interface Conversation {
  id: string;
  workspaceId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Citation {
  chunkId: string;
  documentId: string;
  documentFilename: string;
  ordinal: number;
  sourceLocator: string;
  snippet: string;
  similarity: number;
}

export interface Message {
  id: string;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  createdAt: string;
  citations?: Citation[];
}

export interface ConversationDetail extends Conversation {
  messages: Message[];
}

export type DocumentHealthStatus = "READY" | "STALE" | "PENDING" | "FAILED";

export interface EmbeddingIdentity {
  model: string;
  dimensions: number;
}

export interface DocumentHealthItem {
  id: string;
  originalFilename: string;
  ingestionStatus: string;
  healthStatus: DocumentHealthStatus;
  embeddingModel: string | null;
  embeddingDimensions: number | null;
  errorMessage: string | null;
}

export interface IndexHealthResponse {
  workspaceId: string;
  activeEmbeddingIdentity: EmbeddingIdentity;
  indexedEmbeddingIdentity: EmbeddingIdentity | null;
  status: DocumentHealthStatus;
  totalDocuments: number;
  readyDocuments: number;
  staleDocuments: number;
  pendingDocuments: number;
  failedDocuments: number;
  documents: DocumentHealthItem[];
}

export interface RebuildErrorItem {
  documentId: string;
  filename: string;
  errorMessage: string;
}

export interface RebuildResponse {
  workspaceId: string;
  status: "COMPLETED" | "PARTIAL_FAILURE" | "FAILED";
  totalProcessed: number;
  rebuiltCount: number;
  failedCount: number;
  activeEmbeddingIdentity: EmbeddingIdentity;
  errors: RebuildErrorItem[];
}

export async function fetchWorkspaces(): Promise<Workspace[]> {
  const res = await fetch(`${API_BASE}/api/workspaces`);
  await throwIfNotOk(res, "Could not load workspaces.");
  return res.json();
}

export async function createWorkspace(name: string): Promise<Workspace> {
  const res = await fetch(`${API_BASE}/api/workspaces`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  await throwIfNotOk(res, "Failed to create workspace");
  return res.json();
}

export async function renameWorkspace(
  id: string,
  name: string,
): Promise<Workspace> {
  const res = await fetch(`${API_BASE}/api/workspaces/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  await throwIfNotOk(res, "Failed to rename workspace");
  return res.json();
}

export async function deleteWorkspace(id: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/workspaces/${id}`, {
    method: "DELETE",
  });
  await assertDeleteOk(res, "Failed to delete workspace");
}

export async function fetchDocuments(
  workspaceId: string,
): Promise<DocumentItem[]> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/documents`,
  );
  await throwIfNotOk(res, "Failed to fetch documents");
  return res.json();
}

export async function uploadDocument(
  workspaceId: string,
  file: File,
): Promise<DocumentItem> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/documents`,
    {
      method: "POST",
      body: formData,
    },
  );
  await throwIfNotOk(res, "Failed to upload document");
  return res.json();
}

export async function deleteDocument(
  workspaceId: string,
  documentId: string,
): Promise<void> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/documents/${documentId}`,
    {
      method: "DELETE",
    },
  );
  await assertDeleteOk(res, "Failed to delete document");
}

export async function fetchConversations(
  workspaceId: string,
): Promise<Conversation[]> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations`,
  );
  await throwIfNotOk(res, "Failed to fetch conversations");
  return res.json();
}

export async function createConversation(
  workspaceId: string,
  title?: string,
): Promise<Conversation> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: title || "New conversation" }),
    },
  );
  await throwIfNotOk(res, "Failed to create conversation");
  return res.json();
}

export async function fetchConversationDetail(
  workspaceId: string,
  conversationId: string,
): Promise<ConversationDetail> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}`,
  );
  await throwIfNotOk(res, "Failed to fetch conversation detail");
  return res.json();
}

export async function renameConversation(
  workspaceId: string,
  conversationId: string,
  title: string,
): Promise<Conversation> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title }),
    },
  );
  await throwIfNotOk(res, "Failed to rename conversation");
  return res.json();
}

export async function deleteConversation(
  workspaceId: string,
  conversationId: string,
): Promise<void> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}`,
    {
      method: "DELETE",
    },
  );
  await assertDeleteOk(res, "Failed to delete conversation");
}

export function streamChatMessage(
  workspaceId: string,
  conversationId: string,
  query: string,
  onChunk: (chunk: string) => void,
  onCitations: (citations: Citation[]) => void,
  onComplete: () => void,
  onError: (err: Error) => void,
): () => void {
  const url = `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}/messages/stream`;
  const controller = new AbortController();

  fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ query }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        throw await readApiError(
          response,
          `Failed to initiate stream (${response.status})`,
        );
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let sawError = false;
      let sawDone = false;

      let currentEvent = "message";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          if (line.startsWith("event:")) {
            currentEvent = line.substring(6).trim();
          } else if (line.startsWith("data:")) {
            const data = line.startsWith("data: ")
              ? line.substring(6)
              : line.substring(5);
            if (currentEvent === "chunk") {
              // console.log("chunk from react backend: ",data);
              console.log(JSON.stringify(data));
              onChunk(data);
            } else if (currentEvent === "citations") {
              try {
                const parsed = JSON.parse(data.trim());
                onCitations(parsed);
              } catch (err) {
                console.error("Failed to parse citations", err);
              }
            } else if (currentEvent === "error") {
              sawError = true;
              let parsed: ApiErrorBody | null = null;
              try {
                const raw = JSON.parse(data.trim());
                parsed =
                  typeof raw === "string"
                    ? (JSON.parse(raw) as ApiErrorBody)
                    : (raw as ApiErrorBody);
              } catch {
                parsed = null;
              }
              onError(
                new AtlasApiError(
                  parsed?.code || "PROVIDER_UNAVAILABLE",
                  parsed?.message?.trim() || "Answer could not be completed.",
                  parsed?.requestId,
                ),
              );
              return;
            } else if (currentEvent === "done") {
              sawDone = true;
              onComplete();
              return;
            }
          }
        }
      }
      if (sawError) return;
      if (!sawDone) {
        onError(
          new AtlasApiError(
            "PROVIDER_UNAVAILABLE",
            "Answer could not be completed.",
          ),
        );
        return;
      }
      onComplete();
    })
    .catch((err) => {
      if (err.name !== "AbortError") {
        onError(err);
      }
    });

  return () => {
    controller.abort();
  };
}

export async function getIndexHealth(
  workspaceId: string,
): Promise<IndexHealthResponse> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/index-health`,
  );
  await throwIfNotOk(res, "Failed to fetch index health");
  return res.json();
}

export async function rebuildWorkspaceIndex(
  workspaceId: string,
): Promise<RebuildResponse> {
  const res = await fetch(`${API_BASE}/api/workspaces/${workspaceId}/rebuild`, {
    method: "POST",
  });
  await throwIfNotOk(res, "Failed to rebuild workspace index");
  return res.json();
}
