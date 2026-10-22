'use client';

import { FormEvent, useEffect, useState } from 'react';
import {
  closeImageDialog$,
  imageDialogState$,
  saveImage$,
  useCellValue,
  usePublisher,
} from '@mdxeditor/editor';

export default function CustomImageDialog() {
  const state = useCellValue(imageDialogState$);
  const saveImage = usePublisher(saveImage$);
  const closeImageDialog = usePublisher(closeImageDialog$);

  const [altText, setAltText] = useState('');
  const [title, setTitle] = useState('');

  useEffect(() => {
    if (state.type === 'editing') {
      setAltText(state.initialValues.altText ?? '');
      setTitle(state.initialValues.title ?? '');
    }
  }, [state]);

  useEffect(() => {
    if (state.type !== 'editing') return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeImageDialog();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [state, closeImageDialog]);

  if (state.type !== 'editing') return null;

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    e.stopPropagation();
    saveImage({
      src: state.initialValues.src ?? '',
      altText,
      title,
    });
  };

  const handleClose = () => closeImageDialog();

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={handleClose}
    >
      <div
        className="w-full max-w-md rounded-lg bg-white p-6 shadow-lg"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="image-dialog-title"
      >
        <h2 id="image-dialog-title" className="mb-4 text-lg font-semibold text-gray-900">
          이미지 정보 편집
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="image-alt" className="mb-1 block text-sm font-medium text-gray-700">
              대체 텍스트 (Alt)
            </label>
            <input
              id="image-alt"
              type="text"
              value={altText}
              onChange={(e) => setAltText(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              placeholder="이미지 설명"
              autoFocus
            />
          </div>
          <div>
            <label htmlFor="image-title" className="mb-1 block text-sm font-medium text-gray-700">
              제목 (Title)
            </label>
            <input
              id="image-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              placeholder="마우스 호버 시 표시될 제목"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              취소
            </button>
            <button
              type="submit"
              className="rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
            >
              저장
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
