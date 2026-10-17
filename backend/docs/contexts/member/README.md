# account 컨텍스트

계정, 자격증명, 권한을 담당한다.
애그리거트 루트는 `Account`이고 `Credential`과 `Role`이 멤버다.

## 문서 목록

| 파일 | 내용 | 읽는 사람 |
|---|---|---|
| [01-rules.md](01-rules.md) | 가입, 로그인, 연동, 탈퇴 규칙 | 누구나 |
| [02-implementation.md](02-implementation.md) | 각 규칙을 어느 계층에 두는지 | 구현하는 사람 |

01은 아키텍처를 모른다. 제품이 어떻게 동작하는지만 적는다.
02는 01을 전제로 배치를 적는다. 아키텍처가 바뀌면 02만 바뀐다.

## 다른 문서와의 관계

| 문서 | 다루는 것 |
|---|---|
| [strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md) | account와 profile을 나눈 이유 |
| [conventions/security.md](../../conventions/security.md) | 토큰, 쿠키, 비밀번호 해싱, 응답 규약 |

비밀번호 저장 방식이나 토큰 수명 같은 횡단 관심사는 `conventions/security.md`에 있다.
이 디렉터리에는 account 컨텍스트에만 해당하는 규칙을 둔다.
