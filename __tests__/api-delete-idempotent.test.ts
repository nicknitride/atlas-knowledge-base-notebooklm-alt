import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { deleteConversation, deleteDocument, deleteWorkspace } from "@/lib/api";

function jsonResponse(status: number, body?: unknown): Response {
  if (body === undefined) {
    return new Response(null, { status });
  }
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("idempotent DELETE helpers", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("deleteWorkspace resolves on 204 without reading JSON", async () => {
    const res = jsonResponse(204);
    const jsonSpy = vi.spyOn(res, "json");
    vi.mocked(fetch).mockResolvedValue(res);

    await expect(deleteWorkspace("ws-1")).resolves.toBeUndefined();
    expect(jsonSpy).not.toHaveBeenCalled();
  });

  it("deleteWorkspace resolves on 404 NOT_FOUND", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(404, {
        code: "NOT_FOUND",
        message: "Workspace not found",
      }),
    );
    await expect(deleteWorkspace("ws-missing")).resolves.toBeUndefined();
  });

  it("deleteWorkspace throws on other errors", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(503, {
        code: "PROVIDER_UNAVAILABLE",
        message: "down",
      }),
    );
    await expect(deleteWorkspace("ws-1")).rejects.toMatchObject({
      code: "PROVIDER_UNAVAILABLE",
    });
  });

  it("deleteDocument resolves on 204 and 404 NOT_FOUND", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(204));
    await expect(deleteDocument("ws-1", "d-1")).resolves.toBeUndefined();

    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(404, { code: "NOT_FOUND", message: "gone" }),
    );
    await expect(deleteDocument("ws-1", "d-2")).resolves.toBeUndefined();
  });

  it("deleteConversation resolves on 204 and 404 NOT_FOUND", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(204));
    await expect(deleteConversation("ws-1", "c-1")).resolves.toBeUndefined();

    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(404, { code: "NOT_FOUND", message: "gone" }),
    );
    await expect(deleteConversation("ws-1", "c-2")).resolves.toBeUndefined();
  });
});
