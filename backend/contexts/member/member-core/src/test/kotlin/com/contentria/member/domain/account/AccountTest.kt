package com.contentria.member.domain.account

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AccountTest {

    private val hasher = FakePasswordHasher()

    private fun emailAccount(
        email: String = "reader@contentria.com",
        rawPassword: String = "correct horse",
    ) = Account.registerWithPassword(email, rawPassword, hasher)

    private fun socialAccount(
        email: String = "reader@contentria.com",
        providerId: String = "google-subject-1",
    ) = Account.registerWithSocialProvider(email, AuthProvider.GOOGLE, providerId)

    @Nested
    @DisplayName("가입")
    inner class Registration {

        @Test
        @DisplayName("이메일 가입 계정은 인증 전이라 아직 로그인할 수 없다")
        fun `email sign-up starts unverified`() {
            val account = emailAccount()

            assertThat(account.status).isEqualTo(AccountStatus.UNVERIFIED)
            assertThat(account.status.canAuthenticate).isFalse()
        }

        @Test
        @DisplayName("소셜 가입 계정은 제공자가 이미 주소를 확인했으므로 즉시 활성이다")
        fun `social sign-up starts active`() {
            assertThat(socialAccount().status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("가입 시 USER 권한 하나가 부여된다")
        fun `sign-up grants the USER role`() {
            assertThat(emailAccount().roles).containsExactly(Role.USER)
            assertThat(socialAccount().roles).containsExactly(Role.USER)
        }

        @Test
        @DisplayName("가입 시점에 식별자가 이미 정해져 있다")
        fun `identity is assigned at construction`() {
            assertThat(emailAccount().id).isNotNull()
            assertThat(emailAccount().id).isNotEqualTo(emailAccount().id)
        }

        @Test
        @DisplayName("이메일 가입은 원시 비밀번호가 아니라 해시를 저장한다")
        fun `stores a hash, never the raw password`() {
            val account = emailAccount(rawPassword = "correct horse")
            val credential = account.credentials.single()

            assertThat(credential.matchesPassword("correct horse", hasher)).isTrue()
            assertThat(credential.matchesPassword("wrong horse", hasher)).isFalse()
        }

        @Test
        @DisplayName("소셜 가입 자격증명은 비밀번호 대신 provider 식별자를 가진다")
        fun `social credential carries a provider id`() {
            val credential = socialAccount(providerId = "google-subject-1").credentials.single()

            assertThat(credential.provider).isEqualTo(AuthProvider.GOOGLE)
            assertThat(credential.providerId).isEqualTo("google-subject-1")
            assertThat(credential.matchesPassword("anything", hasher)).isFalse()
        }

        @Test
        @DisplayName("EMAIL은 소셜 제공자가 아니므로 소셜 가입에 쓸 수 없다")
        fun `EMAIL is not a social provider`() {
            assertThatThrownBy {
                Account.registerWithSocialProvider("a@b.com", AuthProvider.EMAIL, "x")
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("빈 provider 식별자는 거부한다")
        fun `blank provider id is rejected`() {
            assertThatThrownBy {
                Account.registerWithSocialProvider("a@b.com", AuthProvider.GOOGLE, "  ")
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    @DisplayName("이메일 정규화와 검증")
    inner class EmailHandling {

        @Test
        @DisplayName("대소문자와 공백을 정규화해 저장한다. 그래야 UNIQUE 제약이 의미를 가진다")
        fun `normalizes case and surrounding whitespace`() {
            assertThat(emailAccount(email = "  Reader@Contentria.COM ").email)
                .isEqualTo("reader@contentria.com")
        }

        @Test
        @DisplayName("대소문자만 다른 주소는 같은 값으로 정규화된다")
        fun `case variants normalize to the same value`() {
            assertThat(emailAccount(email = "A@x.com").email)
                .isEqualTo(emailAccount(email = "a@X.com").email)
        }

        @Test
        @DisplayName("주소 형태가 아니면 가입을 거부한다")
        fun `rejects values that are not addresses`() {
            listOf("", "   ", "no-at-sign", "missing@tld", "two parts@x.com", "@x.com")
                .forEach { invalid ->
                    assertThatThrownBy { emailAccount(email = invalid) }
                        .describedAs("입력: '%s'", invalid)
                        .isInstanceOf(AccountException.InvalidEmail::class.java)
                }
        }

        @Test
        @DisplayName("컬럼 길이를 넘는 주소는 거부한다")
        fun `rejects an address longer than the column`() {
            val tooLong = "a".repeat(Account.EMAIL_MAX_LENGTH) + "@x.com"

            assertThatThrownBy { emailAccount(email = tooLong) }
                .isInstanceOf(AccountException.InvalidEmail::class.java)
        }

        @Test
        @DisplayName("주소 변경도 같은 정규화와 검증을 거친다")
        fun `changeEmail applies the same rules`() {
            val account = emailAccount()

            account.changeEmail("  NEW@Contentria.com ")
            assertThat(account.email).isEqualTo("new@contentria.com")

            assertThatThrownBy { account.changeEmail("broken") }
                .isInstanceOf(AccountException.InvalidEmail::class.java)
            assertThat(account.email).isEqualTo("new@contentria.com")
        }
    }

    @Nested
    @DisplayName("비밀번호 인증")
    inner class PasswordAuthentication {

        @Test
        @DisplayName("활성 계정이 올바른 비밀번호를 내면 통과한다")
        fun `active account with the right password passes`() {
            val account = emailAccount(rawPassword = "correct horse")
            account.verifyEmail()

            assertThatCode { account.authenticateWithPassword("correct horse", hasher) }
                .doesNotThrowAnyException()
        }

        @Test
        @DisplayName("비밀번호가 틀리면 실패한다")
        fun `wrong password fails`() {
            val account = emailAccount(rawPassword = "correct horse")
            account.verifyEmail()

            assertThatThrownBy { account.authenticateWithPassword("wrong horse", hasher) }
                .isInstanceOf(AccountException.InvalidCredentials::class.java)
        }

        @Test
        @DisplayName("이메일 인증 전에는 비밀번호가 맞아도 세션을 열 수 없다")
        fun `unverified account cannot start a session`() {
            val account = emailAccount(rawPassword = "correct horse")

            assertThatThrownBy { account.authenticateWithPassword("correct horse", hasher) }
                .isInstanceOf(AccountException.NotAuthenticatable::class.java)
        }

        @Test
        @DisplayName("정지된 계정은 비밀번호가 맞아도 세션을 열 수 없다")
        fun `suspended account cannot start a session`() {
            val account = emailAccount(rawPassword = "correct horse")
            account.verifyEmail()
            account.suspend()

            assertThatThrownBy { account.authenticateWithPassword("correct horse", hasher) }
                .isInstanceOf(AccountException.NotAuthenticatable::class.java)
        }

        @Test
        @DisplayName("소셜 전용 계정에 비밀번호 로그인을 시도하면 자격증명 오류로 끝난다")
        fun `social-only account has no password to match`() {
            val account = socialAccount()

            assertThatThrownBy { account.authenticateWithPassword("anything", hasher) }
                .isInstanceOf(AccountException.InvalidCredentials::class.java)
        }

        @Test
        @DisplayName("비밀번호 변경 후에는 새 비밀번호만 통과한다")
        fun `changePassword replaces the old one`() {
            val account = emailAccount(rawPassword = "old one")
            account.verifyEmail()

            account.changePassword("new one", hasher)

            assertThatCode { account.authenticateWithPassword("new one", hasher) }
                .doesNotThrowAnyException()
            assertThatThrownBy { account.authenticateWithPassword("old one", hasher) }
                .isInstanceOf(AccountException.InvalidCredentials::class.java)
        }

        @Test
        @DisplayName("이메일 자격증명이 없으면 비밀번호를 바꿀 수 없다")
        fun `changePassword needs an email credential`() {
            assertThatThrownBy { socialAccount().changePassword("x", hasher) }
                .isInstanceOf(AccountException.ProviderNotLinked::class.java)
        }
    }

    @Nested
    @DisplayName("소셜 인증")
    inner class SocialAuthentication {

        @Test
        @DisplayName("연결된 provider 식별자가 일치하면 통과한다")
        fun `matching provider id passes`() {
            val account = socialAccount(providerId = "google-subject-1")

            assertThatCode {
                account.authenticateWithSocialProvider(AuthProvider.GOOGLE, "google-subject-1")
            }.doesNotThrowAnyException()
        }

        @Test
        @DisplayName("provider 식별자가 다르면 다른 사람이므로 거부한다")
        fun `a different provider id is a different person`() {
            val account = socialAccount(providerId = "google-subject-1")

            assertThatThrownBy {
                account.authenticateWithSocialProvider(AuthProvider.GOOGLE, "google-subject-2")
            }.isInstanceOf(AccountException.InvalidCredentials::class.java)
        }

        @Test
        @DisplayName("연결되지 않은 provider로는 로그인할 수 없다")
        fun `an unlinked provider cannot be used`() {
            val account = emailAccount()
            account.verifyEmail()

            assertThatThrownBy {
                account.authenticateWithSocialProvider(AuthProvider.GOOGLE, "google-subject-1")
            }.isInstanceOf(AccountException.ProviderNotLinked::class.java)
        }

        @Test
        @DisplayName("정지된 계정은 소셜 로그인도 막힌다")
        fun `suspended account cannot use social login`() {
            val account = socialAccount(providerId = "google-subject-1")
            account.suspend()

            assertThatThrownBy {
                account.authenticateWithSocialProvider(AuthProvider.GOOGLE, "google-subject-1")
            }.isInstanceOf(AccountException.NotAuthenticatable::class.java)
        }
    }

    @Nested
    @DisplayName("자격증명 연결과 해제")
    inner class CredentialLinking {

        @Test
        @DisplayName("이메일 계정에 소셜 제공자를 추가로 연결할 수 있다")
        fun `links a social provider to an email account`() {
            val account = emailAccount()

            account.linkSocialProvider(AuthProvider.GOOGLE, "google-subject-1")

            assertThat(account.credentials).hasSize(2)
            assertThat(account.hasCredentialFor(AuthProvider.EMAIL)).isTrue()
            assertThat(account.hasCredentialFor(AuthProvider.GOOGLE)).isTrue()
        }

        @Test
        @DisplayName("같은 제공자를 두 번 연결할 수 없다. 계정당 provider별 자격증명은 하나다")
        fun `a provider cannot be linked twice`() {
            val account = socialAccount(providerId = "google-subject-1")

            assertThatThrownBy {
                account.linkSocialProvider(AuthProvider.GOOGLE, "google-subject-2")
            }.isInstanceOf(AccountException.ProviderAlreadyLinked::class.java)

            assertThat(account.credentials).hasSize(1)
        }

        @Test
        @DisplayName("EMAIL은 소셜 제공자가 아니므로 연결 대상이 아니다")
        fun `EMAIL cannot be linked as a social provider`() {
            assertThatThrownBy {
                socialAccount().linkSocialProvider(AuthProvider.EMAIL, "x")
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("자격증명이 둘 이상이면 하나를 해제할 수 있다")
        fun `unlinks one of several credentials`() {
            val account = emailAccount()
            account.linkSocialProvider(AuthProvider.GOOGLE, "google-subject-1")

            account.unlinkProvider(AuthProvider.GOOGLE)

            assertThat(account.credentials).hasSize(1)
            assertThat(account.hasCredentialFor(AuthProvider.GOOGLE)).isFalse()
        }

        @Test
        @DisplayName("마지막 자격증명은 해제할 수 없다. 계정에 들어갈 방법이 사라진다")
        fun `the last credential cannot be removed`() {
            val account = emailAccount()

            assertThatThrownBy { account.unlinkProvider(AuthProvider.EMAIL) }
                .isInstanceOf(AccountException.LastCredentialRemoval::class.java)

            assertThat(account.credentials).hasSize(1)
        }

        @Test
        @DisplayName("연결되지 않은 제공자는 해제할 수 없다")
        fun `an unlinked provider cannot be unlinked`() {
            assertThatThrownBy { emailAccount().unlinkProvider(AuthProvider.GOOGLE) }
                .isInstanceOf(AccountException.ProviderNotLinked::class.java)
        }

        @Test
        @DisplayName("credentials는 살아 있는 뷰가 아니라 복사본이다. 나중의 변경이 앞서 읽은 목록에 반영되지 않는다")
        fun `the credentials list is a snapshot, not a live view`() {
            val account = emailAccount()
            val snapshot = account.credentials

            account.linkSocialProvider(AuthProvider.GOOGLE, "google-subject-1")

            assertThat(snapshot).hasSize(1)
            assertThat(account.credentials).hasSize(2)
        }
    }

    @Nested
    @DisplayName("계정 생명주기")
    inner class Lifecycle {

        @Test
        @DisplayName("이메일 인증에 성공하면 활성이 된다")
        fun `verifyEmail activates an unverified account`() {
            val account = emailAccount()

            account.verifyEmail()

            assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("이미 인증된 계정을 다시 인증해도 오류가 아니다")
        fun `verifyEmail is idempotent on an active account`() {
            val account = emailAccount()
            account.verifyEmail()

            assertThatCode { account.verifyEmail() }.doesNotThrowAnyException()
            assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("이메일 인증은 정지를 푸는 우회로가 될 수 없다")
        fun `verifyEmail is not a way to lift a suspension`() {
            val account = emailAccount()
            account.verifyEmail()
            account.suspend()

            assertThatThrownBy { account.verifyEmail() }
                .isInstanceOf(AccountException.StatusTransitionNotAllowed::class.java)

            assertThat(account.status).isEqualTo(AccountStatus.SUSPENDED)
        }

        @Test
        @DisplayName("정지한 계정은 복구할 수 있다")
        fun `a suspended account can be reinstated`() {
            val account = socialAccount()

            account.suspend()
            account.reinstate()

            assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("삭제는 되돌릴 수 없다")
        fun `closing is terminal`() {
            val account = socialAccount()
            account.close()

            assertThat(account.status).isEqualTo(AccountStatus.DELETED)
            assertThatThrownBy { account.reinstate() }
                .isInstanceOf(AccountException.StatusTransitionNotAllowed::class.java)
        }
    }

    @Nested
    @DisplayName("권한")
    inner class Authorities {

        @Test
        @DisplayName("권한을 부여할 수 있고 중복 부여는 누적되지 않는다")
        fun `granting is idempotent`() {
            val account = emailAccount()

            account.grant(Role.ADMIN)
            account.grant(Role.ADMIN)

            assertThat(account.roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
            assertThat(account.hasRole(Role.ADMIN)).isTrue()
        }

        @Test
        @DisplayName("권한이 둘 이상이면 하나를 회수할 수 있다")
        fun `revokes one of several roles`() {
            val account = emailAccount()
            account.grant(Role.ADMIN)

            account.revoke(Role.ADMIN)

            assertThat(account.roles).containsExactly(Role.USER)
        }

        @Test
        @DisplayName("마지막 권한은 회수할 수 없다")
        fun `the last role cannot be revoked`() {
            val account = emailAccount()

            assertThatThrownBy { account.revoke(Role.USER) }
                .isInstanceOf(AccountException.LastRoleRemoval::class.java)

            assertThat(account.roles).containsExactly(Role.USER)
        }

        @Test
        @DisplayName("가지고 있지 않은 권한 회수는 아무 일도 하지 않는다")
        fun `revoking a role that was never granted is a no-op`() {
            val account = emailAccount()

            assertThatCode { account.revoke(Role.ADMIN) }.doesNotThrowAnyException()
            assertThat(account.roles).containsExactly(Role.USER)
        }

        @Test
        @DisplayName("roles는 살아 있는 뷰가 아니라 복사본이다. 나중의 변경이 앞서 읽은 집합에 반영되지 않는다")
        fun `the roles set is a snapshot, not a live view`() {
            val account = emailAccount()
            val snapshot = account.roles

            account.grant(Role.ADMIN)

            assertThat(snapshot).containsExactly(Role.USER)
            assertThat(account.roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
        }
    }
}
