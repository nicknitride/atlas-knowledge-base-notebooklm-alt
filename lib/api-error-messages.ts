import type { AtlasApiError } from "@/lib/api";

const FALLBACKS: Record<string, string> = {
  VALIDATION_ERROR: "Please check your input and try again.",
  NOT_FOUND: "That item no longer exists.",
  UPLOAD_EMPTY: "Choose a non-empty file to upload.",
  UPLOAD_TOO_LARGE: "File is too large. Upload a file smaller than 80 MB.",
  UPLOAD_UNSUPPORTED_TYPE:
    "Unsupported file type. Use PDF, Markdown, or plain text.",
  EMBEDDING_CONFIG_MISMATCH:
    "This workspace’s documents need to be re-processed before answers can be grounded. Re-upload or restore the previous embedding settings.",
  PROVIDER_UNAVAILABLE:
    "The AI service is unavailable. Check that your local model endpoint is running and try again.",
  PROVIDER_MISCONFIGURED:
    "The AI service is misconfigured. Check model name and endpoint settings.",
  RETRIEVAL_UNAVAILABLE:
    "Document search is temporarily unavailable. Please try again.",
};

function asAtlasError(err: unknown): AtlasApiError | null {
  if (
    typeof err === "object" &&
    err !== null &&
    "code" in err &&
    typeof (err as { code: unknown }).code === "string" &&
    (err as Error).name === "AtlasApiError"
  ) {
    return err as AtlasApiError;
  }
  return null;
}

/**
 * Prefer backend message when present; otherwise map known codes to user copy.
 */
export function messageForApiError(
  err: unknown,
  actionLabel: string,
): string {
  const apiErr = asAtlasError(err);
  if (apiErr) {
    const trimmed = apiErr.message?.trim();
    // Constructor may fall back Error.message to the code when message is empty
    if (trimmed && trimmed !== apiErr.code) return trimmed;
    const mapped = FALLBACKS[apiErr.code];
    if (mapped) return mapped;
    return `Failed to ${actionLabel}.`;
  }

  if (err instanceof TypeError) {
    return "Could not reach Atlas. Check that the backend is running and try again.";
  }

  if (err instanceof Error && err.message.trim()) {
    return err.message;
  }

  return `Failed to ${actionLabel}.`;
}
