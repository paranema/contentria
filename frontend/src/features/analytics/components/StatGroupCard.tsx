interface StatGroupCardProps {
  title: string;
  items: { label: string; value: number }[];
}

/**
 * 하나의 지표(조회수·방문자)를 오늘·어제·누적 3열 카드로 묶어 표시합니다.
 */
export default function StatGroupCard({ title, items }: StatGroupCardProps) {
  return (
    <div className="rounded-lg bg-white p-5 shadow-sm">
      <h2 className="mb-4 text-lg font-semibold">{title}</h2>
      <div className="grid grid-cols-3 divide-x divide-gray-100">
        {items.map((item) => (
          <div key={item.label} className="px-2 text-center">
            <p className="text-sm text-gray-500">{item.label}</p>
            <p className="mt-1 truncate text-2xl font-bold text-gray-900">
              {item.value.toLocaleString()}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
