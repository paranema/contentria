'use client';

import dynamic from 'next/dynamic';
import { forwardRef } from 'react';
import { type MDXEditorMethods, type MDXEditorProps } from '@mdxeditor/editor';

// 브라우저 전용 API를 사용하는 MDXEditor를 SSR 없이 동적으로 불러옵니다.
const Editor = dynamic(() => import('./InitializedMDXEditor'), {
  ssr: false,
});

/** 외부에서 에디터 메서드 ref를 전달받을 수 있도록 감싼 MDXEditor 컴포넌트입니다. */
export const ForwardRefEditor = forwardRef<MDXEditorMethods, MDXEditorProps>((props, ref) => (
  <Editor {...props} editorRef={ref} />
));

// React DevTools 식별을 위해 forwardRef 컴포넌트의 표시 이름을 지정합니다.
ForwardRefEditor.displayName = 'ForwardRefEditor';
