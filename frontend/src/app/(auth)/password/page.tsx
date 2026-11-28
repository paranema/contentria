import PasswordLinkForm from '@/features/member/components/password/PasswordLinkForm';

/** 이메일로 발송된 비밀번호 재설정 링크(`/password#token=…`, MEMBER_PASSWORD_LINK_URL)의 도착 페이지입니다. */
export default function PasswordLinkPage() {
  return <PasswordLinkForm />;
}
