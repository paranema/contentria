'use client';

import { recordVisitAction } from '../api/visits';
import { useEffect, useRef } from 'react';

interface VisitTrackerProps {
  blogId: string;
  postId?: string | null;
}

/** 블로그 홈이나 게시글을 열 때 방문을 1회 기록합니다. (analytics 01-rules 흐름 1) */
export default function VisitTracker({ blogId, postId }: VisitTrackerProps) {
  const isTracked = useRef(false);

  useEffect(() => {
    if (isTracked.current) {
      return;
    }
    isTracked.current = true;

    recordVisitAction({
      blogId,
      postId: postId ?? null,
      referrer: document.referrer || null,
    });
  }, [blogId, postId]);

  return null;
}
