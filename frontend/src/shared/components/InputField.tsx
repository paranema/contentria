import React, { forwardRef } from 'react'; // forwardRef 추가

type InputFieldProps = {
  id: string;
  type: string;
  placeholder: string;
  autoComplete?: string;
  isRounded?: 'top' | 'bottom' | 'both' | 'none';
  label?: string;
  disabled?: boolean;
  errorMessage?: string;
  className?: string;
} & Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  'type' | 'id' | 'placeholder' | 'autoComplete' | 'disabled'
>;

export const InputField = forwardRef<HTMLInputElement, InputFieldProps>(
  (
    {
      id,
      type,
      placeholder,
      autoComplete,
      isRounded = 'both',
      label,
      disabled = false,
      errorMessage,
      className = '',
      ...inputProps
    },
    ref
  ) => {
    // 나머지 코드는 동일
    const getRoundedClass = () => {
      switch (isRounded) {
        case 'top':
          return 'rounded-t-md';
        case 'bottom':
          return 'rounded-b-md';
        case 'both':
          return 'rounded-md';
        default:
          return '';
      }
    };

    const baseClasses = `relative block w-full appearance-none border px-3 py-2 text-gray-900 placeholder-gray-500 focus:z-10 focus:outline-none focus:ring-1 sm:text-sm ${getRoundedClass()}`;

    const stateClasses = errorMessage
      ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
      : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500';

    const disabledClasses = disabled ? 'cursor-not-allowed bg-gray-50 text-gray-500' : '';

    return (
      <div>
        {label && (
          <label htmlFor={id} className="mb-1 block text-sm font-medium text-gray-700">
            {label}
          </label>
        )}
        <div className="relative">
          <input
            ref={ref}
            id={id}
            type={type}
            disabled={disabled}
            placeholder={placeholder}
            autoComplete={autoComplete}
            className={`${baseClasses} ${stateClasses} ${disabledClasses} ${className}`}
            {...inputProps}
          />
        </div>
        {errorMessage && <p className="mt-1 text-sm text-red-600">{errorMessage}</p>}
      </div>
    );
  }
);

InputField.displayName = 'InputField';

export default InputField;
