export function getHighResGoogleProfileImage(
  url: string | null | undefined,
  size: number = 256
): string | undefined {
  if (!url) {
    return undefined;
  }

  // URL에 '=s' 파라미터가 있는지 확인하고, 있다면 원하는 크기로 교체한다.
  // 정규식을 사용하여 '=s' 뒤에 숫자가 오는 모든 경우를 처리한다.
  return url.replace(/=s\d+(-c)?$/, `=s${size}-c`);
}
