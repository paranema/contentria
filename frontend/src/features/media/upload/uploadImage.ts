import imageCompression from 'browser-image-compression';
import { completeImageUploadAction, prepareImageUploadAction } from '../api/uploads';
import { mediaErrorMessage } from '../errors';

const COMPRESSION_OPTIONS = {
  maxSizeMB: 1,
  maxWidthOrHeight: 1920,
  initialQuality: 0.8,
  useWebWorker: true,
};

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

const MAGIC_NUMBERS: Record<string, { offset: number; bytes: number[] }[]> = {
  'image/jpeg': [{ offset: 0, bytes: [0xff, 0xd8, 0xff] }],
  'image/png': [{ offset: 0, bytes: [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] }],
  'image/gif': [{ offset: 0, bytes: [0x47, 0x49, 0x46, 0x38] }],
  'image/webp': [
    { offset: 0, bytes: [0x52, 0x49, 0x46, 0x46] }, // RIFF
    { offset: 8, bytes: [0x57, 0x45, 0x42, 0x50] }, // WEBP
  ],
};

async function validateMagicNumber(file: File): Promise<boolean> {
  const signatures = MAGIC_NUMBERS[file.type];
  if (!signatures) return false;

  const header = new Uint8Array(await file.slice(0, 12).arrayBuffer());

  return signatures.every(({ offset, bytes }) => bytes.every((b, i) => header[offset + i] === b));
}

/**
 * Media 01-rules 흐름 1, 2: checks and compresses the image in the browser, asks for an upload
 * address, puts the file to the storage itself, then has the API check what was stored. Returns
 * the image's public address for the post body. Throws an Error with a message to show.
 */
export async function uploadImageToR2(file: File): Promise<string> {
  if (!ALLOWED_TYPES.includes(file.type)) {
    throw new Error('지원하지 않는 파일 형식입니다. JPEG, PNG, WebP, GIF만 허용됩니다.');
  }

  if (file.size > MAX_FILE_SIZE) {
    throw new Error('파일 크기가 10MB 제한을 초과합니다.');
  }

  if (!(await validateMagicNumber(file))) {
    throw new Error(
      '파일 내용이 선언된 형식과 일치하지 않습니다. 유효한 이미지 파일을 업로드해주세요.'
    );
  }

  // Compress the image (skip GIFs to preserve animation)
  const compressedFile =
    file.type === 'image/gif' ? file : await imageCompression(file, COMPRESSION_OPTIONS);

  const prepared = await prepareImageUploadAction({
    fileName: file.name,
    contentType: compressedFile.type,
    sizeBytes: compressedFile.size,
  });
  if (!prepared.success) throw new Error(mediaErrorMessage(prepared.error));

  // The address is signed for exactly this type and size.
  const uploadResponse = await fetch(prepared.data.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': compressedFile.type },
    body: compressedFile,
  });
  if (!uploadResponse.ok) {
    throw new Error(`이미지 업로드에 실패했습니다 (${uploadResponse.status}).`);
  }

  const completed = await completeImageUploadAction(prepared.data.mediaId);
  if (!completed.success) throw new Error(mediaErrorMessage(completed.error));
  return completed.data.imageUrl;
}
