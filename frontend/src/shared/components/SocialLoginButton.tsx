'use client';

import React from 'react';

interface SocialLoginButtonProps {
  icon: React.ReactNode;
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
}

export default function SocialLoginButton({
  icon,
  children,
  onClick,
  disabled,
}: SocialLoginButtonProps) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      type="button"
      className="font-mediun inline-flex w-full items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm text-gray-700 shadow-sm transition-colors duration-150 ease-in-out hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {icon}
      <span>{children}</span>
    </button>
  );
}
