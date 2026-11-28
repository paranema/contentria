import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { DailyTraffic } from '../types';

/** 차트 선과 대시보드 카드 헤더의 범례가 동일한 색상을 공유하도록 시리즈 정의를 일원화합니다. */
export const TRAFFIC_SERIES = [
  { key: 'views', name: '조회수', color: '#0ea5e9' },
  { key: 'visitors', name: '방문자수', color: '#4f46e5' },
] as const;

/** 툴팁 라벨에 표시할 ISO(yyyy-MM-dd) 날짜를 한국어 월·일 형식으로 변환합니다. */
const formatKoreanDate = (iso: string) => {
  const date = new Date(iso);
  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
};

interface DayTickProps {
  x?: number;
  y?: number;
  index?: number;
  payload?: { value: string };
}

/**
 * 30일치 날짜가 겹치지 않도록 평상시에는 일(day) 숫자만 표시하고,
 * 첫 번째 틱과 매월 1일에만 아래 줄에 월을 함께 표시합니다.
 */
const DayTick = ({ x, y, index, payload }: DayTickProps) => {
  if (!payload) return null;
  const date = new Date(payload.value);
  const day = date.getDate();
  const showMonth = index === 0 || day === 1;

  return (
    <g transform={`translate(${x},${y})`}>
      <text dy={12} textAnchor="middle" fontSize={11} fill="#6b7280">
        {day}
      </text>
      {showMonth && (
        <text dy={28} textAnchor="middle" fontSize={11} fontWeight={600} fill="#374151">
          {date.getMonth() + 1}월
        </text>
      )}
    </g>
  );
};

const TrafficChart = ({ data }: { data: DailyTraffic[] }) => {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 10, right: 10, left: 10, bottom: 20 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} />
        {/* 일(1~2자리) 단위로 모든 날짜 틱을 빠짐없이 표시합니다. */}
        <XAxis
          dataKey="date"
          interval={0}
          tick={<DayTick />}
          tickLine={false}
          axisLine={{ stroke: '#e5e7eb' }}
        />
        {/* 소수점 눈금 노이즈를 없애기 위해 세로축을 숨기고 호버 툴팁으로 수치를 제공합니다. */}
        <YAxis hide allowDecimals={false} />
        <Tooltip labelFormatter={(iso) => formatKoreanDate(String(iso))} />
        {/* 범례는 상위 Dashboard 카드 헤더에서 TRAFFIC_SERIES를 사용해 별도로 렌더링합니다. */}
        {TRAFFIC_SERIES.map((series) => (
          <Line
            key={series.key}
            type="monotone"
            dataKey={series.key}
            name={series.name}
            stroke={series.color}
            strokeWidth={2.5}
            dot={false}
            activeDot={{ r: 4 }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
};

export default TrafficChart;
