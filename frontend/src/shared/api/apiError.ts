/**
 * 백엔드 모든 컨텍스트가 공통으로 반환하는 에러 응답 본문 형식입니다(백엔드 `ErrorResponse(code, message)` 대응).
 * 에러 코드는 컨텍스트별로 정의된 `INVALID_REQUEST`, `POST_NOT_FOUND` 등의 문자열 상수를 사용합니다.
 */
export interface ApiErrorResponse {
  code: string;
  message: string;
  /** 회원(member) 컨텍스트 전용 필드로, 다른 로그인 수단을 사용해야 할 때 해당 계정에 연결된 제공자 목록을 담습니다. */
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

  /** 실패 응답의 상태 코드와 본문으로부터 `ApiError`를 생성하며, 표준 에러 형식이 아니면 공통 에러 코드를 부여합니다. */
  static from(status: number, body: unknown): ApiError {
    if (isApiErrorResponse(body)) {
      return new ApiError(status, body.code, body.message, body.providers);
    }
    return new ApiError(status, UNKNOWN_ERROR, 'Server Error');
  }
}

/** 게이트웨이 오류나 비정상 종료처럼 백엔드 표준 에러 형식을 따르지 않는 응답에 부여하는 기본 에러 코드입니다. */
export const UNKNOWN_ERROR = 'UNKNOWN_ERROR';

function isApiErrorResponse(body: unknown): body is ApiErrorResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as ApiErrorResponse).code === 'string' &&
    typeof (body as ApiErrorResponse).message === 'string'
  );
}
