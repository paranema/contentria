'use client';

import { useRef, useState } from 'react';
import { AlertCircle, Loader2, Upload, Video, X } from 'lucide-react';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { Progress } from '@/shared/ui/progress';
import { uploadVideoToR2 } from '@/lib/uploadVideo';

const ACCEPT = 'video/mp4,video/quicktime,video/webm,video/x-matroska';

interface VideoUploadProps {
  /** Current attached videoId (e.g. when editing an existing post). */
  value?: string | null;
  onChange: (videoId: string | null) => void;
}

type UploadState =
  | { phase: 'idle' }
  | { phase: 'uploading'; percent: number; fileName: string }
  | { phase: 'uploaded'; fileName: string }
  | { phase: 'error'; message: string };

/**
 * Out-of-band video slot shown above the markdown editor. One video per post: uploads the
 * source directly to R2 with a % progress bar and hands the `videoId` back to the editor.
 */
export default function VideoUpload({ value, onChange }: VideoUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<UploadState>(
    value ? { phase: 'uploaded', fileName: '첨부된 동영상' } : { phase: 'idle' }
  );

  const pickFile = () => inputRef.current?.click();

  const handleFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = ''; // allow re-selecting the same file
    if (!file) return;

    setState({ phase: 'uploading', percent: 0, fileName: file.name });
    try {
      const videoId = await uploadVideoToR2(file, (percent) =>
        setState({ phase: 'uploading', percent, fileName: file.name })
      );
      setState({ phase: 'uploaded', fileName: file.name });
      onChange(videoId);
    } catch (error) {
      setState({
        phase: 'error',
        message: error instanceof Error ? error.message : '동영상 업로드에 실패했습니다.',
      });
    }
  };

  const remove = () => {
    onChange(null);
    setState({ phase: 'idle' });
  };

  return (
    <Card className="mb-4 p-4">
      <input ref={inputRef} type="file" accept={ACCEPT} className="hidden" onChange={handleFile} />

      {state.phase === 'idle' && (
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Video className="h-4 w-4" />
            <span>게시글 상단에 표시할 동영상 (선택, 1개 · 최대 500MB)</span>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={pickFile}>
            <Upload className="h-4 w-4" />
            동영상 첨부
          </Button>
        </div>
      )}

      {state.phase === 'uploading' && (
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span className="truncate">{state.fileName}</span>
            <span className="ml-auto tabular-nums text-muted-foreground">{state.percent}%</span>
          </div>
          <Progress value={state.percent} />
        </div>
      )}

      {state.phase === 'uploaded' && (
        <div className="flex items-center gap-3">
          <Video className="h-4 w-4 shrink-0" />
          <span className="truncate text-sm">{state.fileName}</span>
          <Badge variant="secondary">처리 중</Badge>
          <Button type="button" variant="ghost" size="sm" className="ml-auto" onClick={pickFile}>
            교체
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={remove}
            aria-label="동영상 삭제"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {state.phase === 'error' && (
        <div className="flex items-center gap-3">
          <AlertCircle className="h-4 w-4 shrink-0 text-destructive" />
          <span className="truncate text-sm text-destructive">{state.message}</span>
          <Button type="button" variant="outline" size="sm" className="ml-auto" onClick={pickFile}>
            다시 시도
          </Button>
        </div>
      )}
    </Card>
  );
}
