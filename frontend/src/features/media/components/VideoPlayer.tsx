'use client';

import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { AlertCircle, Loader2 } from 'lucide-react';
import { PostVideo } from '../types';

interface QualityLevel {
  index: number;
  height: number;
}

/**
 * 게시글에 첨부된 HLS 동영상을 재생합니다.
 * Safari(iOS 포함)는 네이티브 HLS로 재생하고, 그 외 브라우저(Chrome·Firefox·Edge)는
 * hls.js와 MSE를 통해 재생하며 수동 화질 선택(기본값 자동 ABR)을 지원합니다.
 */
export default function VideoPlayer({ video }: { video: PostVideo }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [levels, setLevels] = useState<QualityLevel[]>([]);
  // -1은 자동 화질(ABR)을 의미합니다.
  const [currentLevel, setCurrentLevel] = useState(-1);

  const isPlayable = video.state === 'COMPLETED' && !!video.masterUrl;

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !isPlayable || !video.masterUrl) return;

    // Safari와 iOS 환경은 네이티브 HLS 재생을 지원하므로 hls.js 없이 바로 연결합니다.
    if (el.canPlayType('application/vnd.apple.mpegurl')) {
      el.src = video.masterUrl;
      return;
    }

    if (!Hls.isSupported()) return;

    const hls = new Hls();
    hlsRef.current = hls;
    hls.loadSource(video.masterUrl);
    hls.attachMedia(el);
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      setLevels(hls.levels.map((level, index) => ({ index, height: level.height })));
    });

    return () => {
      hls.destroy();
      hlsRef.current = null;
      setLevels([]);
    };
  }, [isPlayable, video.masterUrl]);

  const handleQualityChange = (index: number) => {
    setCurrentLevel(index);
    if (hlsRef.current) {
      hlsRef.current.currentLevel = index;
    }
  };

  if (video.state === 'FAILED') {
    return (
      <Placeholder>
        <AlertCircle className="h-6 w-6" />
        <span>동영상 처리에 실패했습니다.</span>
      </Placeholder>
    );
  }

  if (!isPlayable) {
    return (
      <Placeholder>
        <Loader2 className="h-6 w-6 animate-spin" />
        <span>동영상을 처리 중입니다. 잠시 후 새로고침해주세요.</span>
      </Placeholder>
    );
  }

  return (
    <div className="mb-8 lg:mb-10">
      <video
        ref={videoRef}
        controls
        playsInline
        poster={video.posterUrl ?? undefined}
        className="aspect-video w-full rounded-lg bg-black"
      />
      {levels.length > 1 && (
        <div className="mt-2 flex items-center justify-end gap-2 text-sm text-gray-500">
          <label htmlFor="video-quality">화질</label>
          <select
            id="video-quality"
            value={currentLevel}
            onChange={(e) => handleQualityChange(Number(e.target.value))}
            className="rounded-md border border-gray-300 bg-white px-2 py-1 text-sm focus:border-indigo-500 focus:outline-none"
          >
            <option value={-1}>자동</option>
            {levels.map((level) => (
              <option key={level.index} value={level.index}>
                {level.height}p
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
}

function Placeholder({ children }: { children: React.ReactNode }) {
  return (
    <div className="mb-8 flex aspect-video w-full items-center justify-center gap-2 rounded-lg bg-gray-100 text-sm text-gray-500 lg:mb-10">
      {children}
    </div>
  );
}
