import Link from 'next/link';

const SidebarMenuItem = ({
  path,
  label,
  icon,
  isActive,
  disabled,
  onClick,
}: {
  path: string;
  label: string;
  icon: React.ReactNode;
  isActive: boolean;
  disabled: boolean;
  onClick?: () => void;
}) => {
  const commonClasses = 'flex items-center rounded-lg px-3 py-2';

  if (disabled) {
    return (
      <span className={`${commonClasses} cursor-not-allowed text-gray-400`}>
        <span className="mr-3">{icon}</span>
        <span>{label}</span>
      </span>
    );
  }

  const activeClasses = isActive
    ? 'bg-indigo-50 font-medium text-indigo-600'
    : 'text-gray-600 hover:bg-gray-100';

  return (
    <Link href={path} onClick={onClick} className={`${commonClasses} ${activeClasses}`}>
      <span className="mr-3">{icon}</span>
      <span>{label}</span>
    </Link>
  );
};

export default SidebarMenuItem;
