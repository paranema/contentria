import { format, formatDistanceToNow, isToday, parseISO } from 'date-fns';
import { ko } from 'date-fns/locale';

export const formatPostDate = (dateString: string): string => {
  const date = parseISO(dateString);

  if (isToday(date)) {
    return formatDistanceToNow(date, {
      addSuffix: true,
      locale: ko,
    });
  } else {
    return format(date, 'yyyy년 MM월 dd일', { locale: ko });
  }
};
