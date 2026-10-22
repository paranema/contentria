import React from 'react';
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/shared/ui/input-otp';
import { Loader2 } from 'lucide-react';

interface VerificationStepProps {
  email: string;
  verificationCode: string;
  isLoading: boolean;
  error: string | null;
  onCodeChange: (code: string) => void;
  onResendCode: () => void;
}

export default function VerificationStep({
  email,
  verificationCode,
  isLoading,
  error,
  onCodeChange,
}: VerificationStepProps) {
  return (
    <>
      <div className="mt-4 text-center text-sm text-muted-foreground">
        <p>Enter the code sent to</p>
        <span className="font-medium text-gray-900">{email}</span>
      </div>

      <div className="mt-8 flex flex-col items-center space-y-6">
        {/* <OtpInput
          value={verificationCode}
          onChange={onCodeChange}
          numInputs={6}
          shouldAutoFocus={true}
          containerStyle="flex justify-between w-full"
          renderInput={({ style, ...props }) => {
            if (style) {
              delete (style as React.CSSProperties).width;
            }
            return (
              <input
                style={style}
                {...props}
                disabled={isLoading}
                className={`${props.className || ''} h-12 w-12 flex-shrink-0 rounded-md border border-gray-300 text-center text-xl text-gray-900 transition focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-100 disabled:text-gray-400`}
              />
            );
          }}
        /> */}
        <InputOTP
          maxLength={6}
          value={verificationCode}
          onChange={onCodeChange}
          disabled={isLoading}
          autoFocus
        >
          <InputOTPGroup className="gap-2">
            {/* 총 6자리의 입력 슬롯을 만듭니다. */}
            {Array.from({ length: 6 }).map((_, index) => (
              <InputOTPSlot
                key={index}
                index={index}
                className={`h-12 w-12 rounded-md border text-lg sm:h-14 sm:w-14 sm:text-xl ${
                  error ? 'border-destructive' : 'border-input'
                }`}
              />
            ))}
          </InputOTPGroup>
        </InputOTP>

        <div className="relative h-6 w-full">
          {/* 로딩 상태 (필요 시 표시) */}
          <div
            className={`absolute inset-0 flex justify-center transition-all duration-300 ${isLoading ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-1 opacity-0'}`}
          >
            <div className="flex items-center space-x-2 text-indigo-600">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span className="text-sm font-medium">Verifying code...</span>
            </div>
          </div>

          {/* 에러 상태 */}
          <div
            className={`absolute inset-0 flex justify-center transition-all duration-300 ${error && !isLoading ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-1 opacity-0'}`}
          >
            <p className="text-sm font-medium text-destructive">{error}</p>
          </div>
        </div>

        {/* <div className="mt-4 text-center">
          <Button
            type="button"
            variant="ghost"
            onClick={onResendCode}
            disabled={isLoading}
            className="text-sm text-indigo-600 hover:text-indigo-500 hover:bg-indigo-50 disabled:opacity-50"
          >
            {isLoading ? 'Sending...' : "Didn't receive a code? Resend"}
          </Button>
        </div> */}
      </div>
    </>
  );
}
