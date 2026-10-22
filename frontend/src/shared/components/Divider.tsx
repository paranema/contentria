import React from 'react';

type DividerProps = {
  text: string;
};

export const Divider: React.FC<DividerProps> = ({ text }) => (
  <div className="relative">
    <div className="absolute inset-0 flex items-center">
      <div className="w-full border-t border-gray-300"></div>
    </div>
    <div className="relative flex justify-center text-sm">
      <span className="bg-gray-50 px-2 text-gray-500">{text}</span>
    </div>
  </div>
);

export default Divider;
