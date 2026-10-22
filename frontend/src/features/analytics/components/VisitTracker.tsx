'use client';

import { trackVisitAction } from '@/actions/analytics';
import { useEffect, useRef } from 'react';

interface AnalyticsTrackerProps {
  blogId: string;
  postId?: string | null;
}

export default function AnalyticsTracker({ blogId, postId }: AnalyticsTrackerProps) {
  const isTracked = useRef(false);

  useEffect(() => {
    if (isTracked.current) {
      return;
    }
    isTracked.current = true;

    const referer = document.referrer;

    trackVisitAction({
      blogId,
      postId: postId ?? null,
      referer,
    });
  }, [blogId, postId]);

  return null;
}
