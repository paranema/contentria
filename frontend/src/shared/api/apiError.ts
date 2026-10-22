/**
 * The error body every context of the API answers with (backend: `ErrorResponse(code, message)`).
 * Codes are names such as `INVALID_REQUEST` or `POST_NOT_FOUND`, one set per context.
 */
export interface ApiErrorResponse {
  code: string;
  message: string;
  /** Member only: the sign-in methods the account has, when it must use one of them instead. */
  providers?: string[];
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly providers?: string[];

  constructor(status: number, code: string, message: string, providers?: string[]) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.providers = providers;
  }

  /** From a failed response's status and parsed body; a body of another shape gets a generic code. */
  static from(status: number, body: unknown): ApiError {
    if (isApiErrorResponse(body)) {
      return new ApiError(status, body.code, body.message, body.providers);
    }
    return new ApiError(status, UNKNOWN_ERROR, 'Server Error');
  }
}

/** The code of an error whose body the API did not shape, such as a gateway's or a crash's. */
export const UNKNOWN_ERROR = 'UNKNOWN_ERROR';

function isApiErrorResponse(body: unknown): body is ApiErrorResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as ApiErrorResponse).code === 'string' &&
    typeof (body as ApiErrorResponse).message === 'string'
  );
}
