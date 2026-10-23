const SkeletonHeader = () => {
  return (
    <header className="border-b shadow-sm">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
        <div className="w-24"></div>
        <div className="h-8 w-32 animate-pulse rounded-md bg-gray-200"></div>
        <div className="h-10 w-24 animate-pulse rounded-lg bg-gray-200"></div>
      </div>
    </header>
  );
};

export default SkeletonHeader;
