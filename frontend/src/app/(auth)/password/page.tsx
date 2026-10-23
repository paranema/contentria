import PasswordLinkForm from '@/features/member/components/password/PasswordLinkForm';

/** Where a mailed password link lands: `/password#token=…` (MEMBER_PASSWORD_LINK_URL). */
export default function PasswordLinkPage() {
  return <PasswordLinkForm />;
}
