/**
 * 01flux WA JavaScript/TypeScript SDK.
 *
 * Official client library for the 01flux WA WhatsApp API Gateway.
 *
 * @example
 * ```typescript
 * import { FluxWaClient, FluxWaApiError } from '@rmyndharis/01flux-wa';
 *
 * const client = new FluxWaClient({
 *   baseUrl: 'http://localhost:2785',
 *   apiKey: 'flx_k1_…',
 * });
 *
 * await client.sessions.start('my-session');
 * const result = await client.messages.sendText('my-session', {
 *   chatId: '628123456789@c.us',
 *   text: 'Hello from the 01flux WA SDK!',
 * });
 * console.log(result.messageId);
 * ```
 *
 * @packageDocumentation
 */

export { FluxWaClient } from './client.js';
export { default } from './client.js';
export type { FluxWaClientOptions } from './client.js';
export * from './errors.js';
export type * from './types.js';
export type { BinaryResponse, ClientConfig, FetchLike, HttpMethod, RequestOptions } from './http.js';
export { buildUrl, warnIfInsecureHttpUrl } from './http.js';
