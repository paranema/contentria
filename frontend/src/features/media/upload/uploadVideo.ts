import { prepareVideoUploadAction } from '../api/uploads';
import { mediaErrorMessage } from '../errors';

const ALLOWED_TYPES = ['video/mp4', 'video/quicktime', 'video/webm', 'video/x-matroska'];
const MAX_FILE_SIZE = 500 * 1024 * 1024;

/**
 * 서명된 URL을 발급받아 원본 동영상을 진행률 콜백과 함께 R2에 직접 업로드하고 생성된 videoId를 반환합니다.
 * fetch는 업로드 진행률 이벤트를 지원하지 않으므로 XMLHttpRequest를 사용합니다.
 */
export async function uploadVideoToR2(
  file: File,
  onProgress?: (percent: number) => void
): Promise<string> {
  if (!ALLOWED_TYPES.includes(file.type)) {
    throw new Error('지원하지 않는 동영상 형식입니다. MP4, MOV, WebM, MKV만 허용됩니다.');
  }

  if (file.size > MAX_FILE_SIZE) {
    throw new Error('동영상 크기가 500MB 제한을 초과합니다.');
  }

  const prepared = await prepareVideoUploadAction({
    fileName: file.name,
    contentType: file.type,
    sizeBytes: file.size,
  });
  if (!prepared.success) throw new Error(mediaErrorMessage(prepared.error));

  // 원본이 스토리지에 저장되면 버킷 이벤트를 통해 워커가 트랜스코딩을 시작합니다.
  await uploadWithProgress(prepared.data.uploadUrl, file, onProgress);

  return prepared.data.videoId;
}

function uploadWithProgress(
  url: string,
  file: File,
  onProgress?: (percent: number) => void
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', file.type);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`동영상 업로드에 실패했습니다 (${xhr.status}).`));
      }
    };
    xhr.onerror = () => reject(new Error('동영상 업로드 중 네트워크 오류가 발생했습니다.'));

    xhr.send(file);
  });
}
