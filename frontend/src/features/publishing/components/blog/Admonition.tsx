import { ReactNode } from 'react';
import { AlertCircle, AlertOctagon, AlertTriangle, Info, Lightbulb } from 'lucide-react';

export type AdmonitionType = 'note' | 'tip' | 'info' | 'caution' | 'warning' | 'danger';

const CONFIG: Record<
  AdmonitionType,
  {
    label: string;
    container: string;
    accent: string;
    icon: ReactNode;
  }
> = {
  note: {
    label: 'NOTE',
    container: 'border-l-blue-500 bg-blue-50/70',
    accent: 'text-blue-700',
    icon: <Info className="h-5 w-5" aria-hidden />,
  },
  tip: {
    label: 'TIP',
    container: 'border-l-emerald-500 bg-emerald-50/70',
    accent: 'text-emerald-700',
    icon: <Lightbulb className="h-5 w-5" aria-hidden />,
  },
  info: {
    label: 'INFO',
    container: 'border-l-sky-500 bg-sky-50/70',
    accent: 'text-sky-700',
    icon: <Info className="h-5 w-5" aria-hidden />,
  },
  caution: {
    label: 'CAUTION',
    container: 'border-l-amber-500 bg-amber-50/70',
    accent: 'text-amber-700',
    icon: <AlertTriangle className="h-5 w-5" aria-hidden />,
  },
  warning: {
    label: 'WARNING',
    container: 'border-l-orange-500 bg-orange-50/70',
    accent: 'text-orange-700',
    icon: <AlertCircle className="h-5 w-5" aria-hidden />,
  },
  danger: {
    label: 'DANGER',
    container: 'border-l-red-500 bg-red-50/70',
    accent: 'text-red-700',
    icon: <AlertOctagon className="h-5 w-5" aria-hidden />,
  },
};

export function isAdmonitionType(value: string): value is AdmonitionType {
  return value in CONFIG;
}

export default function Admonition({
  type,
  children,
}: {
  type: AdmonitionType;
  children: ReactNode;
}) {
  const config = CONFIG[type];
  return (
    <aside
      role="note"
      className={`my-6 rounded-md border-l-4 px-5 py-4 [&>p:first-of-type]:mt-0 [&>p:last-of-type]:mb-0 ${config.container}`}
    >
      <div className={`mb-2 flex items-center gap-2 text-sm font-bold ${config.accent}`}>
        {config.icon}
        <span>{config.label}</span>
      </div>
      {children}
    </aside>
  );
}
