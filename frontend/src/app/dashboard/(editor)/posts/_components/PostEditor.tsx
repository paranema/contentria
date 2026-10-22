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
 * The post editor with media's uploads put in: publishing's editor and media's image upload and
 * video field meet here, so neither feature knows the other (docs/architecture.md, 조합). A client
 * component, since what is handed over are functions.
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
