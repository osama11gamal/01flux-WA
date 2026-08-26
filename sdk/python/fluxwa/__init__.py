"""
01flux WA Python SDK.

Official client library for the 01flux WA WhatsApp API Gateway.

Example usage::

    from fluxwa import FluxWaClient

    client = FluxWaClient(
        base_url="http://localhost:2785",
        api_key="flx_k1_…",
    )

    client.sessions.start("my-session")
    result = client.messages.send_text("my-session", {
        "chatId": "628123456789@c.us",
        "text": "Hello from the 01flux WA Python SDK!",
    })
    print(result["messageId"])
"""

from __future__ import annotations

from .client import FluxWaClient
from .errors import (
    FluxWaApiError,
    FluxWaAuthError,
    FluxWaConflictError,
    FluxWaError,
    FluxWaForbiddenError,
    FluxWaNotFoundError,
    FluxWaNotImplementedError,
    FluxWaServiceUnavailableError,
    FluxWaRateLimitError,
    FluxWaTimeoutError,
)

__all__ = [
    "FluxWaClient",
    "FluxWaError",
    "FluxWaApiError",
    "FluxWaAuthError",
    "FluxWaForbiddenError",
    "FluxWaNotFoundError",
    "FluxWaConflictError",
    "FluxWaRateLimitError",
    "FluxWaNotImplementedError",
    "FluxWaServiceUnavailableError",
    "FluxWaTimeoutError",
]
