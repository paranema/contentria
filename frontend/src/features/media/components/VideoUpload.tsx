'use client';

import { useRef, useState } from 'react';
import { AlertCircle, Loader2, Upload, Video, X } from 'lucide-react';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { Progress } from '@/shared/ui/progress';
import { uploadVideoToR2 } from '../upload/uploadVideo';

const ACCEPT = 'video/mp4,video/quicktime,video/webm,video/x-matroska';

interface VideoUploadProps {
  /** 기존 게시글 수정 시 현재 첨부되어 있는 동영상 ID입니다. */
  value?: string | null;
  onChange: (videoId: string | null) => void;
}

type UploadState =
  | { phase: 'idle' }
  | { phase: 'uploading'; percent: number; fileName: string }
  | { phase: 'uploaded'; fileName: string }
  | { phase: 'error'; message: string };

/**
 * 마크다운 에디터 상단에 배치되는 단일 동영상 첨부 슬롯입니다.
 * 원본 동영상을 진행률 표시와 함께 R2에 직접 업로드하고 생성된 videoId를 에디터로 전달합니다.
 */
export default function VideoUpload({ value, onChange }: VideoUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<UploadState>(
    value ? { phase: 'uploaded', fileName: '첨부된 동영상' } : { phase: 'idle' }
  );

  const pickFile = () => inputRef.current?.click();

  const handleFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // 동일한 파일을 다시 선택할 수 있도록 input 값을 초기화합니다.
    event.target.value = '';
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
