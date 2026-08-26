# rmyndharis-01flux-wa

Official Python SDK for the [01flux WA](https://github.com/rmyndharis/01flux-wa) WhatsApp API Gateway.

A synchronous client built on [httpx](https://www.python-httpx.org/), with bundled type hints (PEP 561).

## Install

```bash
pip install rmyndharis-01flux-wa
```

Requires Python 3.9+. The importable module is `01flux-wa`.

## Usage

```python
from fluxwa import FluxWaClient

client = FluxWaClient(
    base_url="https://your-gateway.example.com",
    api_key="flx_k1_…",
)

client.sessions.start("my-session")

result = client.messages.send_text("my-session", {
    "chatId": "628123456789@c.us",
    "text": "Hello from the 01flux WA Python SDK!",
})
print(result["messageId"])
```

The client is also a context manager (it closes the underlying connection pool on exit):

```python
with FluxWaClient(base_url="…", api_key="…") as client:
    client.messages.send_text("my-session", {"chatId": "…@c.us", "text": "hi"})
```

For tests, pass an httpx transport — no global monkey-patching required:

```python
import httpx
client = FluxWaClient(base_url="…", api_key="…", transport=httpx.MockTransport(handler))
```

## Search

`GET /search` is wrapped as `client.search.search(params)`. Only `q` is required;
the rest (`sessionId`, `chatId`, `direction`, `type`, `from`, `dateFrom`,
`dateTo`, `limit`, `offset`) are optional. `dateFrom` / `dateTo` are epoch-ms.
The active search provider (built-in DB full-text, or a plugin) answers; if none
is configured the server returns 501.

```python
res = client.search.search({"q": "invoice", "sessionId": "my-session", "limit": 20})
for hit in res["hits"]:
    print(hit["snippet"], hit["score"])
```

## Messaging

> Voice notes: pass `ptt=True` inside the body dict to `send_audio` to send a real WhatsApp voice note (PTT). Supply `audio/ogg; codecs=opus` audio for reliable playback; the server defaults the mimetype to that when `ptt` is set without one.

## Errors

A non-2xx response raises a typed `FluxWaApiError` subclass — `FluxWaAuthError` (401),
`FluxWaForbiddenError` (403), `FluxWaNotFoundError` (404), `FluxWaConflictError` (409),
`FluxWaRateLimitError` (429), `FluxWaNotImplementedError` (501),
`FluxWaServiceUnavailableError` (503 — the only retryable one) — each carrying `.status`
and the parsed `.body`. A timeout raises `FluxWaTimeoutError`.

```python
from fluxwa import FluxWaNotFoundError

try:
    client.sessions.get("missing")
except FluxWaNotFoundError as e:
    print(e.status)  # 404
```

## Notes

- **Use HTTPS in production** — the API key is sent as `X-API-Key` and is bearer-equivalent.
- The SDK does **not** retry, and **never follows redirects** (so the key is never re-sent to
  a redirect target). Path segments are percent-encoded; a base-URL path prefix (e.g. behind a
  reverse proxy) is preserved.
- Escape hatch for endpoints the SDK does not wrap:
  `client.request(method, path, query=…, body=…)`.

## Releasing

Publishing to PyPI is done by the
[`python-sdk-release.yml`](../../.github/workflows/python-sdk-release.yml)
workflow, which authenticates with **PyPI Trusted Publishing (OIDC)**. There is
no PyPI token in the workflow or in the repository secrets: PyPI mints a
short-lived credential from the GitHub OIDC token, so nothing long-lived exists
to leak or rotate.

One-time setup, required **before** the first tag — on pypi.org, open the
project's publishing settings and add a GitHub trusted publisher:

- Owner: `rmyndharis`
- Repository: `01flux WA`
- Workflow name: `python-sdk-release.yml`

There are no repository secrets to add. Until the trusted publisher exists PyPI
rejects the upload, so configure it first.

Cutting a release:

1. Bump `version` in `pyproject.toml` and land it on `main`.
2. Tag that commit `py-sdk-v<version>` (e.g. `py-sdk-v0.5.0`) and push the tag.
   The SDK has its own version line — the monorepo's `v*` tags are the app
   version and never trigger an SDK publish.
3. The workflow re-runs the test suite, builds the sdist and wheel, and
   uploads. The artifacts published are the ones those tests passed against.

## License

MIT
