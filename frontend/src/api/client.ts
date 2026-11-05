import axios from 'axios';

export const API_URL: string =
  import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

export const api = axios.create({
  baseURL: API_URL,
  timeout: 20_000,
  headers: { 'Content-Type': 'application/json' },
});

/** Extract a readable message from an axios error for error states. */
export function errMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { message?: string; error?: string } | undefined;
    return (
      data?.message ??
      data?.error ??
      e.message ??
      'Request failed'
    );
  }
  return e instanceof Error ? e.message : 'Unexpected error';
}

/**
 * List endpoints are inconsistent by design in the contract: pageable ones return
 * `{ content, total, page, size }` while others return plain arrays. Normalise.
 */
export function intoList<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object' && Array.isArray((data as { content?: unknown }).content)) {
    return (data as { content: T[] }).content;
  }
  return [];
}
