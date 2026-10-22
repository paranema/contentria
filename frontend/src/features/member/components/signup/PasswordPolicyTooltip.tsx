import { CheckCircle2, XCircle } from 'lucide-react';

interface PolicyItem {
  id: string;
  text: string;
  isValid: (password: string) => boolean;
}

interface PasswordPolicyTooltipProps {
  policies: PolicyItem[];
  currentPasswordValue: string;
  isVisible: boolean;
}

const PasswordPolicyTooltip = ({
  policies,
  currentPasswordValue,
  isVisible, // isVisible prop을 받아서 조건부 렌더링은 부모에서 처리
}: PasswordPolicyTooltipProps) => {
  if (!isVisible) {
    return null; // isVisible이 false면 아무것도 렌더링하지 않음
  }

  return (
    <div
      role="tooltip"
      className="absolute bottom-full right-0 z-20 mb-2 w-60 rounded-lg border border-gray-200 bg-white p-4 shadow-xl md:bottom-auto md:left-1/2 md:top-full md:mt-2 md:-translate-x-1/2"
      // 스타일 변경: 흰색 배경, 그림자 강화, 패딩 및 테두리 조정
    >
      <p className="mb-2 text-sm font-semibold text-indigo-700">
        비밀번호는 다음을 충족해야 합니다:
      </p>
      <ul className="space-y-0.5">
        {policies.map((policy) => {
          const isMet = policy.isValid(currentPasswordValue || '');
          return (
            <li
              key={policy.id}
              className={`flex items-center text-xs ${isMet ? 'text-green-400' : 'text-gray-400'}`}
            >
              {isMet ? (
                <CheckCircle2 size={14} className="mr-1.5 flex-shrink-0 text-indigo-500" />
              ) : (
                <XCircle size={14} className="mr-1.5 flex-shrink-0 text-gray-400" />
              )}
              {policy.text}
            </li>
          );
        })}
      </ul>
    </div>
  );
};

export default PasswordPolicyTooltip;
