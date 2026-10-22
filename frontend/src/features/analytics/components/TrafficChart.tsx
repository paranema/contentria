import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { TrafficChartResponse } from '@/types/api/dashboard';

/** 시리즈 정의 단일 소스 — 차트 선과 카드 헤더의 범례가 같은 색을 공유한다 */
export const TRAFFIC_SERIES = [
  { key: 'views', name: '조회수', color: '#0ea5e9' },
  { key: 'visitors', name: '방문자수', color: '#4f46e5' },
] as const;

/** 툴팁/월 라벨용 — ISO(yyyy-MM-dd) 날짜를 "6월 24일" 형태로 */
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
 * X축 커스텀 틱: "06/24" 식 표기는 30일치가 빡빡해서 일(day) 숫자만 표기하고,
 * 첫 틱과 매월 1일 틱 아래 줄에만 월을 표기한다.
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

const TrafficChart = ({ data }: { data: TrafficChartResponse[] }) => {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 10, right: 10, left: 10, bottom: 20 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} />
        {/* 1일 단위 틱 — 좁은 화면에서는 겹칠 수 있으나 일 숫자(1~2자리)라 감수 */}
        <XAxis
          dataKey="date"
          interval={0}
          tick={<DayTick />}
          tickLine={false}
          axisLine={{ stroke: '#e5e7eb' }}
        />
        {/* 세로축은 숨긴다(소수점 틱 노이즈 제거) — 수치는 호버 툴팁으로 확인 */}
        <YAxis hide allowDecimals={false} />
        <Tooltip labelFormatter={(iso) => formatKoreanDate(String(iso))} />
        {/* 범례는 카드 헤더 우측(DashboardContent)에서 TRAFFIC_SERIES로 렌더링한다 */}
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
