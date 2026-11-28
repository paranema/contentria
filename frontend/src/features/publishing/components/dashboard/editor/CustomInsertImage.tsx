'use client';

import { ChangeEvent, useRef, useState } from 'react';
import { ButtonWithTooltip, insertImage$, usePublisher } from '@mdxeditor/editor';
import { Image as ImageIcon, Loader2 } from 'lucide-react';

const ACCEPTED_MIME = 'image/png,image/jpeg,image/webp,image/gif';

function deriveAltText(fileName: string): string {
  return fileName.replace(/\.[^/.]+$/, '');
}

/** 이미지를 업로드하고 공개 URL을 반환하는 함수 타입입니다. */
export type ImageUploader = (file: File) => Promise<string>;

export default function CustomInsertImage({ upload }: { upload: ImageUploader }) {
  const insertImage = usePublisher(insertImage$);
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const handleSelected = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    setUploading(true);
    try {
      const src = await upload(file);
      insertImage({ src, altText: deriveAltText(file.name) });
    } catch (err) {
      const message = err instanceof Error ? err.message : '이미지 업로드에 실패했습니다.';
      alert(message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <>
      <ButtonWithTooltip
        title={uploading ? '업로드 중...' : '이미지 삽입'}
        onClick={() => inputRef.current?.click()}
        disabled={uploading}
      >
        {uploading ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : (
          <ImageIcon className="h-4 w-4" />
        )}
      </ButtonWithTooltip>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPTED_MIME}
        className="hidden"
        onChange={handleSelected}
      />
    </>
  );
}
