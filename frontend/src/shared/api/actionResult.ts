import { ApiError, UNKNOWN_ERROR } from './apiError';

/** An error as a Server Action can hand it to the browser: a plain object, not an Error. */
export interface SerializedError {
  name: string;
  message: string;
  status: number;
  code: string;
  providers?: string[];
}

export type ActionResult<T> =
  { success: true; data: T } | { success: false; error: SerializedError };

/** The failed result for whatever a Server Action caught. */
export function toActionError(error: unknown): { success: false; error: SerializedError } {
  if (error instanceof ApiError) {
    return {
      success: false,
      error: {
        name: error.name,
        message: error.message,
        status: error.status,
        code: error.code,
        providers: error.providers,
      },
    };
  }
  return {
    success: false,
    error: {
      name: error instanceof Error ? error.name : 'UnknownError',
      message: error instanceof Error ? error.message : 'Unknown error',
      status: 500,
      code: UNKNOWN_ERROR,
    },
  };
}
