import { describe, expect, it } from 'vitest';
import { ApiError, UNKNOWN_ERROR } from './apiError';
import { toActionError } from './actionResult';

describe('ApiError.from', () => {
  it('takes the code and message of the API error body', () => {
    const error = ApiError.from(404, { code: 'POST_NOT_FOUND', message: 'No such post' });

    expect(error).toMatchObject({ status: 404, code: 'POST_NOT_FOUND', message: 'No such post' });
  });

  it('keeps the sign-in methods member sends with some errors', () => {
    const error = ApiError.from(409, {
      code: 'USE_ANOTHER_METHOD',
      message: 'm',
      providers: ['GOOGLE'],
    });

    expect(error.providers).toEqual(['GOOGLE']);
  });

  it('gives a generic code for a body the API did not shape', () => {
    expect(ApiError.from(502, '<html>Bad Gateway</html>').code).toBe(UNKNOWN_ERROR);
    expect(ApiError.from(500, undefined)).toMatchObject({ status: 500, code: UNKNOWN_ERROR });
  });
});

describe('toActionError', () => {
  it('turns an ApiError into a plain object', () => {
    const result = toActionError(new ApiError(400, 'INVALID_REQUEST', 'Bad'));

    expect(result).toEqual({
      success: false,
      error: {
        name: 'ApiError',
        message: 'Bad',
        status: 400,
        code: 'INVALID_REQUEST',
        providers: undefined,
      },
    });
  });

  it('turns anything else into a 500', () => {
    expect(toActionError(new Error('boom')).error).toMatchObject({
      status: 500,
      code: UNKNOWN_ERROR,
      message: 'boom',
    });
  });
});
