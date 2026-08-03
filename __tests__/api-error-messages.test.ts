import { describe, expect, it } from "vitest";
import { messageForApiError } from "@/lib/api-error-messages";
import { AtlasApiError } from "@/lib/api";

describe("api error message mapping", () => {
  it("prefers backend message when present", () => {
    const err = new AtlasApiError(
      "VALIDATION_ERROR",
      "name must not be blank",
      "r1",
    );
    expect(messageForApiError(err, "create workspace")).toBe(
      "name must not be blank",
    );
  });

  it("maps EMBEDDING_CONFIG_MISMATCH to re-process guidance", () => {
    const err = new AtlasApiError("EMBEDDING_CONFIG_MISMATCH", "", undefined);
    expect(messageForApiError(err, "ask")).toMatch(/re-process|reprocess/i);
  });

  it("maps PROVIDER_UNAVAILABLE distinctly from PROVIDER_MISCONFIGURED", () => {
    const unavailable = messageForApiError(
      new AtlasApiError("PROVIDER_UNAVAILABLE", "", undefined),
      "ask",
    );
    const misconfigured = messageForApiError(
      new AtlasApiError("PROVIDER_MISCONFIGURED", "", undefined),
      "ask",
    );
    expect(unavailable).toMatch(/unavailable|reach|AI/i);
    expect(misconfigured).toMatch(/misconfigured|config/i);
    expect(unavailable).not.toBe(misconfigured);
  });

  it("maps RETRIEVAL_UNAVAILABLE to search messaging", () => {
    expect(
      messageForApiError(
        new AtlasApiError("RETRIEVAL_UNAVAILABLE", "", undefined),
        "ask",
      ),
    ).toMatch(/search/i);
  });

  it("maps UPLOAD codes to specific copy when message empty", () => {
    expect(
      messageForApiError(
        new AtlasApiError("UPLOAD_EMPTY", "", undefined),
        "upload",
      ),
    ).toMatch(/empty/i);
    expect(
      messageForApiError(
        new AtlasApiError("UPLOAD_TOO_LARGE", "", undefined),
        "upload",
      ),
    ).toMatch(/large|size|80/i);
    expect(
      messageForApiError(
        new AtlasApiError("UPLOAD_UNSUPPORTED_TYPE", "", undefined),
        "upload",
      ),
    ).toMatch(/type|supported|pdf|markdown/i);
  });

  it("maps network/unknown errors to reachability copy", () => {
    expect(
      messageForApiError(new TypeError("Failed to fetch"), "delete"),
    ).toMatch(/reach|network|backend|Atlas/i);
  });

  it("maps NOT_FOUND for non-delete actions", () => {
    expect(
      messageForApiError(
        new AtlasApiError("NOT_FOUND", "", undefined),
        "rename",
      ),
    ).toMatch(/no longer exists|not found|gone/i);
  });
});
