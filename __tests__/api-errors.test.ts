import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createWorkspace,
  deleteWorkspace,
  fetchWorkspaces,
  renameWorkspace,
  uploadDocument,
  AtlasApiError,
  isAtlasApiError,
} from "@/lib/api";

describe("api error parsing (contracts/api-client-errors)", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("throws AtlasApiError with code and message from JSON body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "VALIDATION_ERROR",
          message: "name must not be blank",
          requestId: "req-1",
        }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(createWorkspace("")).rejects.toMatchObject({
      name: "AtlasApiError",
      code: "VALIDATION_ERROR",
      message: "name must not be blank",
      requestId: "req-1",
    });
  });

  it("uses fallback message when body is not JSON", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("Gateway Timeout", { status: 504 }),
    );

    try {
      await fetchWorkspaces();
      expect.fail("should throw");
    } catch (err) {
      expect(isAtlasApiError(err)).toBe(true);
      expect((err as AtlasApiError).message).toMatch(/workspace|fetch|reach/i);
    }
  });

  it("prefers backend message on rename failure", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "NOT_FOUND",
          message: "Workspace not found",
        }),
        { status: 404 },
      ),
    );

    await expect(renameWorkspace("ws-1", "New")).rejects.toMatchObject({
      code: "NOT_FOUND",
      message: "Workspace not found",
    });
  });

  it("parses upload error codes", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "UPLOAD_TOO_LARGE",
          message: "Upload a non-empty file smaller than 80 MB",
        }),
        { status: 400 },
      ),
    );

    await expect(
      uploadDocument("ws-1", new File(["x"], "big.pdf")),
    ).rejects.toMatchObject({
      code: "UPLOAD_TOO_LARGE",
    });
  });
});
