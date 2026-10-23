const SkeletonMain = () => {
  return (
    <main className="mx-auth w-full max-w-7xl px-6 py-8">
      <div className="mb-8 h-10 w-1/3 animate-pulse rounded-md bg-gray-200"></div>

      <div className="space-y-4">
        <div className="h-4 w-full animate-pulse rounded-md bg-gray-200"></div>
        <div className="h-4 w-5/6 animate-pulse rounded-md bg-gray-200"></div>
        <div className="h-4 w-full animate-pulse rounded-md bg-gray-200"></div>
        <div className="mt-6 h-4 w-3/4 animate-pulse rounded-md bg-gray-200"></div>
      </div>
    </main>
  );
};

export default SkeletonMain;
