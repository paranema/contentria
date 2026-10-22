'use client';

import { recordVisitAction } from '../api/visits';
import { useEffect, useRef } from 'react';

interface VisitTrackerProps {
  blogId: string;
  postId?: string | null;
}

/** Records one visit when a blog's home or a post is opened (analytics 01-rules 흐름 1). */
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
