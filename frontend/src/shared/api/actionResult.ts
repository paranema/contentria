import { ApiError, UNKNOWN_ERROR } from './apiError';

/** Server Action에서 브라우저로 직렬화해 전달할 수 있도록 일반 객체 형태로 변환한 에러 구조입니다. */
export interface SerializedError {
  name: string;
  message: string;
  status: number;
  code: string;
  providers?: string[];
}

export type ActionResult<T> =
  { success: true; data: T } | { success: false; error: SerializedError };

/** Server Action에서 포착한 예외를 직렬화 가능한 실패 응답으로 변환합니다. */
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
