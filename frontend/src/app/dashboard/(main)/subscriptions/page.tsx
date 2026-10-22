import { getMySubscriptions } from '@/features/interaction/api/interactions';
import SubscriptionList from '@/features/interaction/components/SubscriptionList';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: '구독 중인 블로그 | 대시보드',
};

export default async function SubscriptionsPage() {
  const subscriptions = await getMySubscriptions();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-800">구독 중인 블로그</h1>
        <p className="mt-1 text-sm text-gray-500">
          구독한 블로그에 새 글이 올라오면 알림함으로 알려드립니다.
        </p>
      </div>
      <SubscriptionList subscriptions={subscriptions} />
    </div>
  );
}
