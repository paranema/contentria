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
  isVisible,
}: PasswordPolicyTooltipProps) => {
  if (!isVisible) {
    return null;
  }

  return (
    <div
      role="tooltip"
      className="absolute bottom-full right-0 z-20 mb-2 w-60 rounded-lg border border-gray-200 bg-white p-4 shadow-xl md:bottom-auto md:left-1/2 md:top-full md:mt-2 md:-translate-x-1/2"
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
