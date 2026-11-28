export function getHighResGoogleProfileImage(
  url: string | null | undefined,
  size: number = 256
): string | undefined {
  if (!url) {
    return undefined;
  }

  // Google 프로필 이미지 URL 끝의 크기 파라미터(`=s숫자-c`)를 원하는 해상도로 교체합니다.
  return url.replace(/=s\d+(-c)?$/, `=s${size}-c`);
}
