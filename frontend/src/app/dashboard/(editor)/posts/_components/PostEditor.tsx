'use client';

import VideoUpload from '@/features/media/components/VideoUpload';
import { uploadImageToR2 } from '@/features/media/upload/uploadImage';
import { PostEditorClient } from '@/features/publishing/components/dashboard/editor/PostEditorClient';
import { CategoryView, PostView } from '@/features/publishing/types';

interface PostEditorProps {
  blogSlug: string;
  categories: CategoryView[];
  initialData?: PostView;
}

/**
 * publishing의 에디터와 media의 이미지·동영상 업로드 기능을 결합하는 컴포넌트입니다. (docs/architecture/01-module-structure.md, 화면 조합)
 * 두 피처가 서로 직접 의존하지 않도록 이곳에서 조합하며, 함수 prop을 전달하므로 클라이언트 컴포넌트로 구성합니다.
 */
export default function PostEditor(props: PostEditorProps) {
  return (
    <PostEditorClient
      {...props}
      uploadImage={uploadImageToR2}
      renderVideoField={({ value, onChange }) => <VideoUpload value={value} onChange={onChange} />}
    />
  );
}
