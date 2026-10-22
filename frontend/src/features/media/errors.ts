import { SerializedError } from '@/shared/api/actionResult';

const MESSAGES: Record<string, string> = {
  UNSUPPORTED_IMAGE_FORMAT: '지원하지 않는 파일 형식입니다. JPEG, PNG, WebP, GIF만 허용됩니다.',
  IMAGE_TOO_LARGE: '이미지는 10MB까지 올릴 수 있습니다.',
  IMAGE_CONTENT_MISMATCH:
    '파일 내용이 선언된 형식과 일치하지 않습니다. 유효한 이미지 파일을 올려주세요.',
  UPLOAD_NOT_FINISHED: '업로드가 끝나지 않았습니다. 다시 시도해주세요.',
  DAILY_UPLOAD_LIMIT: '오늘 올릴 수 있는 이미지 용량(100MB)을 모두 썼습니다.',
  INVALID_FILE_NAME: '파일 이름을 확인해주세요.',
  UNSUPPORTED_VIDEO_FORMAT: '지원하지 않는 동영상 형식입니다. MP4, MOV, WebM, MKV만 허용됩니다.',
  VIDEO_TOO_LARGE: '동영상은 500MB까지 올릴 수 있습니다.',
  INVALID_REQUEST: '요청을 확인해주세요.',
};

/** What to tell the person about a failed media request. */
export function mediaErrorMessage(error: Pick<SerializedError, 'code'>): string {
  return MESSAGES[error.code] ?? '업로드에 실패했습니다. 다시 시도해주세요.';
}
