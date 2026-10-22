import { ArrowLeft } from 'lucide-react';

interface BackButtonProps {
  onClick: () => void;
  className?: string;
  ariaLabel?: string;
  disabled?: boolean;
}

export default function BackButton({
  onClick,
  className = 'absolute left-4 top-4 text-gray-500 hover:text-gray-700',
  ariaLabel = 'Go back',
  disabled = false,
}: BackButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`${className} ${disabled ? 'cursor-not-allowed opacity-50' : ''}`}
      aria-label={ariaLabel}
      disabled={disabled}
    >
      <ArrowLeft size={20} />
    </button>
  );
}
