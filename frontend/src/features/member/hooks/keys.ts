// TanStack Query keys of the member feature, so that a sign-in or a rename can refresh what shows it.
export const memberKeys = {
  all: ['member'] as const,
  profile: () => [...memberKeys.all, 'profile'] as const,
};
