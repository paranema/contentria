/** The API as the Next.js server reaches it, without `/api`; in a cluster, its in-cluster address. */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
