# Connecting Google Gemini (Free Tier) to Atlas

This guide shows how to wire **Google Gemini** into the Atlas LLM provider
abstraction using the **free Gemini Developer API** (no billing required for
the free tier limits).

---

## 1. Get a free Gemini API key

1. Go to **https://aistudio.google.com/app/apikey**.
2. Sign in with a Google account and click **Create API key**.
3. Copy the key — it starts with `AIza...`.

Free tier limits (as of mid-2026):

| Model            | RPM | TPM       | RPD   |
| ---------------- | --- | --------- | ----- |
| gemini-2.0-flash | 15  | 1,000,000 | 1,500 |
| gemini-1.5-flash | 15  | 1,000,000 | 1,500 |

More than enough for a self-hosted single-user knowledge workspace.

---

## 2. How the provider abstraction works

Atlas routes all LLM calls through the LlmProvider interface at:
backend/src/main/java/dev/atlas/providers/LlmProvider.java

The interface exposes:

- generate(messages) — synchronous call, returns full response string
- stream(messages, chunkConsumer, onComplete, onError) — simulated streaming
- providerName()

DefaultLlmProvider handles ollama and openai by inspecting atlas.provider.type.
The cleanest approach is to add a gemini branch in the same class — no new Spring
beans, no new files.

---

## 3. Add the Gemini config properties

File: backend/src/main/resources/application.yml
Add under the existing atlas: block:

atlas:
storage-dir: ${ATLAS_STORAGE_DIR:./data/uploads}
    web-origin: ${ATLAS_WEB_ORIGIN:http://localhost:3000}
provider:
type: ${ATLAS_PROVIDER_TYPE:local}
      ollama:
        url: ${ATLAS_OLLAMA_URL:http://localhost:11434}
model: ${ATLAS_OLLAMA_MODEL:llama3}
      openai:
        api-key: ${ATLAS_OPENAI_API_KEY:}
model: ${ATLAS_OPENAI_MODEL:gpt-4o-mini}
      gemini:
        api-key: ${ATLAS_GEMINI_API_KEY:}
model: ${ATLAS_GEMINI_MODEL:gemini-2.0-flash}

gemini-2.0-flash is the recommended free-tier model.

---

## 4. Update DefaultLlmProvider.java

File: backend/src/main/java/dev/atlas/providers/DefaultLlmProvider.java

### Step 4a — Inject config values (add after the existing @Value fields)

@Value("${atlas.provider.gemini.api-key:}")
private String geminiApiKey;

@Value("${atlas.provider.gemini.model:gemini-2.0-flash}")
private String geminiModel;

### Step 4b — Route to Gemini in generate()

Add an else-if block inside the existing generate() method:

@Override
public String generate(List<ChatMessage> messages) {
if ("ollama".equalsIgnoreCase(providerType)) {
try { return generateOllama(messages); }
catch (Exception e) { log.warn("Ollama call failed: {}", e.getMessage()); }

      } else if ("openai".equalsIgnoreCase(providerType) && !openAiApiKey.isBlank()) {
          try { return generateOpenAi(messages); }
          catch (Exception e) { log.warn("OpenAI call failed: {}", e.getMessage()); }

      // ---- ADD THIS BLOCK -----------------------------------------------
      } else if ("gemini".equalsIgnoreCase(providerType) && !geminiApiKey.isBlank()) {
          try { return generateGemini(messages); }
          catch (Exception e) { log.warn("Gemini call failed: {}", e.getMessage()); }
      // -------------------------------------------------------------------
      }
      return generateFallback(messages);

}

### Step 4c — Add the generateGemini() private method

Paste this alongside generateOllama() and generateOpenAi():

/**

- Calls the Gemini Developer API (generateContent endpoint).
-
- Endpoint:
- POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
-
- Gemini role values: "user" and "model" (not "assistant").
- System messages are passed via the systemInstruction field (v1beta).
  */
  private String generateGemini(List<ChatMessage> messages) throws Exception {
  String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

  HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
  conn.setRequestMethod("POST");
  conn.setDoOutput(true);
  conn.setRequestProperty("Content-Type", "application/json");

  // Separate system message from conversation turns
  String systemText = null;
  for (ChatMessage m : messages) {
  if ("system".equalsIgnoreCase(m.role())) systemText = m.content();
  }

  StringBuilder body = new StringBuilder("{");
  if (systemText != null) {
  body.append("\"systemInstruction\":{\"parts\":[{\"text\":")
  .append(escapeJson(systemText)).append("}]},");
  }

  body.append("\"contents\":[");
  boolean first = true;
  for (ChatMessage m : messages) {
  if ("system".equalsIgnoreCase(m.role())) continue;
  String geminiRole = "assistant".equalsIgnoreCase(m.role()) ? "model" : "user";
  if (!first) body.append(",");
  body.append("{\"role\":\"").append(geminiRole).append("\",")
  .append("\"parts\":[{\"text\":").append(escapeJson(m.content())).append("}]}");
  first = false;
  }
  body.append("]}");

  try (OutputStream os = conn.getOutputStream()) {
  os.write(body.toString().getBytes(StandardCharsets.UTF_8));
  }

  int status = conn.getResponseCode();
  if (status != 200) {
  try (InputStream err = conn.getErrorStream()) {
  String errBody = err != null
  ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "";
  throw new RuntimeException("Gemini HTTP " + status + ": " + errBody);
  }
  }

  String response;
  try (InputStream is = conn.getInputStream()) {
  response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
  }

  // Parse: candidates[0].content.parts[0].text
  int textIdx = response.indexOf("\"text\":");
  if (textIdx != -1) {
  int start = response.indexOf("\"", textIdx + 7) + 1;
  int end = response.indexOf("\"", start);
  while (end > 0 && response.charAt(end - 1) == '\\') {
  end = response.indexOf("\"", end + 1);
  }
  return response.substring(start, end)
  .replace("\\n", "\n")
  .replace("\\\"", "\"")
  .replace("\\\\", "\\");
  }
  return response;
  }

---

## 5. Configure the environment

Add to your .env file (copy from .env.example):

# LLM provider — set to "gemini" to use Google Gemini

ATLAS_PROVIDER_TYPE=gemini
ATLAS_GEMINI_API_KEY=AIzaSy...your-key-here...
ATLAS_GEMINI_MODEL=gemini-3.5-flash

Add to the api service in docker-compose.yml under environment:

ATLAS_PROVIDER_TYPE: ${ATLAS_PROVIDER_TYPE:-local}
  ATLAS_GEMINI_API_KEY: ${ATLAS_GEMINI_API_KEY:-}
ATLAS_GEMINI_MODEL: ${ATLAS_GEMINI_MODEL:-gemini-2.0-flash}

Rebuild and restart:

docker compose up --build api

---

## 6. Optional: Gemini Java SDK approach

If you prefer the official SDK over raw HTTP, add to backend/pom.xml:

  <dependency>
    <groupId>com.google.ai.client.generativeai</groupId>
    <artifactId>google-generativeai</artifactId>
    <version>0.9.0</version>
  </dependency>

With the SDK the call becomes:

GenerativeModel model = new GenerativeModel(geminiModel,
GenerativeModelOptions.builder().apiKey(geminiApiKey).build());
GenerateContentResponse resp = model.generateContent(ContentMaker.fromString(userText));
return resp.text();

The raw HTTP implementation in Section 4 is sufficient for the free tier.

---

## 7. Smoke-test the API key

Validate the key before rebuilding the container:

curl -s -X POST \
"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$ATLAS_GEMINI_API_KEY" \
-H "Content-Type: application/json" \
-d '{"contents":[{"role":"user","parts":[{"text":"Say hello"}]}]}' \
| python3 -m json.tool

A 200 response with a candidates array confirms the key is valid.
A 400 or 403 means the key is wrong or not yet active.

---

## 8. Choosing a model

| Model            | Quality | Free RPD | Notes                              |
| ---------------- | ------- | -------- | ---------------------------------- |
| gemini-2.0-flash | ****    | 1,500    | Recommended                        |
| gemini-1.5-flash | ***     | 1,500    | Older but stable                   |
| gemini-1.5-pro   | *****   | 50       | Better reasoning, lower free quota |
| gemini-2.5-pro   | *****   | —        | Requires billing                   |

Set your preferred model via ATLAS_GEMINI_MODEL in .env.

---

## 9. Troubleshooting

| Symptom                           | Likely cause                          | Fix                                                           |
| --------------------------------- | ------------------------------------- | ------------------------------------------------------------- |
| WARN Gemini call failed: HTTP 400 | Malformed request body                | Check JSON escaping in logs                                   |
| WARN Gemini call failed: HTTP 403 | Invalid or missing API key            | Re-check ATLAS_GEMINI_API_KEY in .env                         |
| WARN Gemini call failed: HTTP 429 | Free-tier rate limit hit              | Wait 1 min or switch to a lower-traffic model                 |
| Response is blank / fallback text | ATLAS_PROVIDER_TYPE not set to gemini | Check the env var is passed to the container                  |
| App falls back without a warning  | geminiApiKey is blank                 | Ensure ATLAS_GEMINI_API_KEY is non-empty in the container env |
