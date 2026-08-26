/**
 * Typed error hierarchy for the 01flux WA SDK.
 *
 * The 01flux WA API returns NestJS-default errors of the shape:
 *   `{ statusCode: number, message: string | string[], error?: string }`
 * `error` is absent whenever the exception carried no explicit message, so it is never required to
 * recognise the envelope. This module maps that to a typed, ergonomic error tree so callers can
 * `instanceof`-check or branch on `.status`.
 *
 * @packageDocumentation
 */

/** Base class for every error thrown by the SDK. */
export class FluxWaError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'FluxWaError';
  }
}

/**
 * Thrown when the API responds with a non-2xx status. Carries the HTTP status
 * code and the parsed error body (or the raw text if the body was not JSON).
 *
 * Use the static {@link FluxWaApiError.fromResponse} factory in most cases.
 */
export class FluxWaApiError extends FluxWaError {
  /** HTTP status code (e.g. 400, 404, 409, 429, 501). */
  readonly status: number;
  /** Parsed JSON body if available, otherwise the raw response text. */
  readonly body: unknown;
  /** Value of the `error` field in the NestJS error envelope, if present. */
  readonly errorKind?: string;

  constructor(message: string, status: number, body: unknown, errorKind?: string) {
    super(message);
    this.name = 'FluxWaApiError';
    this.status = status;
    this.body = body;
    this.errorKind = errorKind;
  }

  /** Build an {@link FluxWaApiError} from a fetch Response, awaiting its body. */
  static async fromResponse(res: Response, context: string): Promise<FluxWaApiError> {
    // An opaque unfollowed redirect (we set `redirect: 'manual'`) surfaces as status 0, not a 3xx.
    // Give it a clear message instead of "01flux WA API 0": the redirect was deliberately not followed
    // so the API key is never re-sent to the redirect target.
    if (res.status === 0) {
      return new FluxWaApiError(
        `Unexpected redirect (not followed; the API key is never re-sent to a redirect target) — ${context}`,
        0,
        undefined,
      );
    }
    let body: unknown = undefined;
    const text = await res.text().catch(() => '');
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }
    const env = isNestEnvelope(body) ? body : undefined;
    const messageText = describeMessage(env?.message ?? body ?? res.statusText);
    const message = `01flux WA API ${res.status} ${res.statusText} — ${context}: ${messageText}`;
    return new FluxWaApiError(message, res.status, body, env?.error);
  }
}

/** 401 Unauthorized — missing or invalid API key. */
export class FluxWaAuthError extends FluxWaApiError {}
/** 403 Forbidden — the API key's role is insufficient for this endpoint. */
export class FluxWaForbiddenError extends FluxWaApiError {}
/** 404 Not Found. */
export class FluxWaNotFoundError extends FluxWaApiError {}
/** 409 Conflict — typically an {@link EngineNotReadyError} from the backend. */
export class FluxWaConflictError extends FluxWaApiError {}
/** 429 Too Many Requests — rate limited. */
export class FluxWaRateLimitError extends FluxWaApiError {}
/** 501 Not Implemented — the active engine does not support this operation. */
export class FluxWaNotImplementedError extends FluxWaApiError {}

/**
 * 503 Service Unavailable — a transport failure, not a refusal. The gateway answers this when the
 * engine did not confirm the operation in time: WhatsApp never replied, the socket was down, or the
 * request budget ran out. **Retryable**, unlike every other typed error here.
 *
 * Not every 503 is safe to repeat blindly: the non-idempotent sends (group create, channel create,
 * media send) are deliberately left unbounded by the gateway precisely so they never answer one.
 */
export class FluxWaServiceUnavailableError extends FluxWaApiError {}

/** Thrown when a request exceeds the configured timeout. */
export class FluxWaTimeoutError extends FluxWaError {
  constructor(timeoutMs: number) {
    super(`Request timed out after ${timeoutMs}ms`);
    this.name = 'FluxWaTimeoutError';
  }
}

/**
 * Construct the most specific {@link FluxWaApiError} subclass for a status code.
 * Falls back to the generic {@link FluxWaApiError} for unmapped statuses.
 */
export function classifyApiError(status: number, message: string, body: unknown, errorKind?: string): FluxWaApiError {
  switch (status) {
    case 401:
      return new FluxWaAuthError(message, status, body, errorKind);
    case 403:
      return new FluxWaForbiddenError(message, status, body, errorKind);
    case 404:
      return new FluxWaNotFoundError(message, status, body, errorKind);
    case 409:
      return new FluxWaConflictError(message, status, body, errorKind);
    case 429:
      return new FluxWaRateLimitError(message, status, body, errorKind);
    case 501:
      return new FluxWaNotImplementedError(message, status, body, errorKind);
    case 503:
      return new FluxWaServiceUnavailableError(message, status, body, errorKind);
    default:
      return new FluxWaApiError(message, status, body, errorKind);
  }
}

/**
 * Narrow the NestJS error envelope shape: `{ statusCode, message, error }`.
 *
 * `error` is optional. NestJS omits it whenever the exception was constructed without an explicit
 * message — which is what the global ValidationPipe does under `disableErrorMessages`, the default
 * when `NODE_ENV=production` and `VALIDATION_ERROR_DETAIL` is unset. Every rejected request in a
 * stock production deployment therefore answers `{ statusCode, message }` and nothing else.
 */
interface NestErrorEnvelope {
  statusCode: number;
  message: string | string[];
  error?: string;
}

function isNestEnvelope(body: unknown): body is NestErrorEnvelope {
  return typeof body === 'object' && body !== null && 'statusCode' in body && 'message' in body;
}

function describeMessage(message: string | string[] | unknown): string {
  if (Array.isArray(message)) return message.join(', ');
  if (typeof message === 'string') return message;
  return String(message);
}
