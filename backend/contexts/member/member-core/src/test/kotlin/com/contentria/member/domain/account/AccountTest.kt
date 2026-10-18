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
    ) = Account.registerWithPassword(email, hasher.hash(rawPassword))

    private fun socialAccount(
        email: String = "reader@contentria.com",
        providerId: String = "google-subject-1",
    ) = Account.registerWithSocialProvider(email, AuthProvider.GOOGLE, providerId, emailVerifiedByProvider = true)

    @Nested
    @DisplayName("가입")
    inner class Registration {

        @Test
        @DisplayName("이메일 가입은 코드 확인을 마친 뒤에 일어나므로 계정은 즉시 활성이다")
        fun `email sign-up starts active`() {
            val account = emailAccount()

            assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("소셜 가입 계정은 제공자가 이미 주소를 확인했으므로 즉시 활성이다")
        fun `social sign-up starts active`() {
            assertThat(socialAccount().status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("제공자가 이메일을 확인하지 않았으면 소셜 가입을 거부한다")
        fun `social sign-up needs an address the provider verified`() {
            assertThatThrownBy {
                Account.registerWithSocialProvider(
                    "reader@contentria.com", AuthProvider.GOOGLE, "google-subject-1", emailVerifiedByProvider = false,
                )
            }.isInstanceOf(AccountException.EmailNotVerifiedByProvider::class.java)
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
        @DisplayName("이메일 가입은 해시를 받아 저장한다. 원시 비밀번호는 계정에 들어오지 않는다")
        fun `stores the given hash`() {
            val account = emailAccount(rawPassword = "correct horse")
            val credential = account.credentials.single()

            assertThat(credential.matchesPassword("correct horse", hasher)).isTrue()
            assertThat(credential.matchesPassword("wrong horse", hasher)).isFalse()
        }

        @Test
        @DisplayName("빈 해시는 거부한다")
        fun `blank hash is rejected`() {
            assertThatThrownBy { Account.registerWithPassword("reader@contentria.com", "  ") }
                .isInstanceOf(IllegalArgumentException::class.java)
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
                Account.registerWithSocialProvider("a@b.com", AuthProvider.EMAIL, "x", emailVerifiedByProvider = true)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("빈 provider 식별자는 거부한다")
        fun `blank provider id is rejected`() {
            assertThatThrownBy {
                Account.registerWithSocialProvider("a@b.com", AuthProvider.GOOGLE, "  ", emailVerifiedByProvider = true)
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
        @DisplayName("점이나 + 이후는 지우지 않는다")
        fun `keeps dots and plus suffixes`() {
            assertThat(emailAccount(email = "first.last+blog@x.com").email)
                .isEqualTo("first.last+blog@x.com")
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
        @DisplayName("확인된 새 주소로 바꿀 때도 같은 정규화와 검증을 거친다")
        fun `changeToVerifiedEmail applies the same rules`() {
            val account = emailAccount()

            account.changeToVerifiedEmail("  NEW@Contentria.com ")
            assertThat(account.email).isEqualTo("new@contentria.com")

            assertThatThrownBy { account.changeToVerifiedEmail("broken") }
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

            assertThatCode { account.authenticateWithPassword("correct horse", hasher) }
                .doesNotThrowAnyException()
        }

        @Test
        @DisplayName("비밀번호가 틀리면 실패한다")
        fun `wrong password fails`() {
            val account = emailAccount(rawPassword = "correct horse")

            assertThatThrownBy { account.authenticateWithPassword("wrong horse", hasher) }
                .isInstanceOf(AccountException.InvalidCredentials::class.java)
        }

        @Test
        @DisplayName("정지된 계정은 비밀번호가 맞아도 세션을 열 수 없다")
        fun `suspended account cannot start a session`() {
            val account = emailAccount(rawPassword = "correct horse")
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
    }

    @Nested
    @DisplayName("비밀번호 설정")
    inner class PasswordSetting {

        @Test
        @DisplayName("비밀번호를 교체하면 새 비밀번호만 통과한다")
        fun `setPassword replaces the existing one`() {
            val account = emailAccount(rawPassword = "old one")

            account.setPassword(hasher.hash("new one"))

            assertThatCode { account.authenticateWithPassword("new one", hasher) }
                .doesNotThrowAnyException()
            assertThatThrownBy { account.authenticateWithPassword("old one", hasher) }
                .isInstanceOf(AccountException.InvalidCredentials::class.java)
            assertThat(account.credentials).hasSize(1)
        }

        @Test
        @DisplayName("소셜 전용 계정에 비밀번호를 처음 설정하면 이메일 자격증명이 생긴다")
        fun `setPassword creates the email credential when missing`() {
            val account = socialAccount()

            account.setPassword(hasher.hash("first one"))

            assertThat(account.hasCredentialFor(AuthProvider.EMAIL)).isTrue()
            assertThat(account.credentials).hasSize(2)
            assertThatCode { account.authenticateWithPassword("first one", hasher) }
                .doesNotThrowAnyException()
        }

        @Test
        @DisplayName("빈 해시는 거부한다")
        fun `blank hash is rejected`() {
            assertThatThrownBy { emailAccount().setPassword("") }
                .isInstanceOf(IllegalArgumentException::class.java)
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
    @DisplayName("소셜 자동 연동")
    inner class AutomaticLinking {

        @Test
        @DisplayName("같은 주소의 활성 계정에, 제공자가 확인한 주소라면 소셜 자격증명을 붙인다")
        fun `links a verified social identity to the account with the same address`() {
            val account = emailAccount(email = "reader@contentria.com")

            account.autoLinkSocialProvider(
                AuthProvider.GOOGLE, "google-subject-1", providerEmail = "Reader@Contentria.com", emailVerifiedByProvider = true,
            )

            assertThat(account.hasCredentialFor(AuthProvider.GOOGLE)).isTrue()
            assertThatCode { account.authenticateWithSocialProvider(AuthProvider.GOOGLE, "google-subject-1") }
                .doesNotThrowAnyException()
        }

        @Test
        @DisplayName("제공자가 주소를 확인하지 않았으면 자동 연동하지 않는다")
        fun `refuses an address the provider did not verify`() {
            val account = emailAccount(email = "reader@contentria.com")

            assertThatThrownBy {
                account.autoLinkSocialProvider(
                    AuthProvider.GOOGLE, "google-subject-1", providerEmail = "reader@contentria.com", emailVerifiedByProvider = false,
                )
            }.isInstanceOf(AccountException.EmailNotVerifiedByProvider::class.java)
            assertThat(account.hasCredentialFor(AuthProvider.GOOGLE)).isFalse()
        }

        @Test
        @DisplayName("활성이 아닌 계정에는 자동 연동하지 않는다")
        fun `refuses an account that is not active`() {
            val account = emailAccount(email = "reader@contentria.com")
            account.suspend()

            assertThatThrownBy {
                account.autoLinkSocialProvider(
                    AuthProvider.GOOGLE, "google-subject-1", providerEmail = "reader@contentria.com", emailVerifiedByProvider = true,
                )
            }.isInstanceOf(AccountException.NotAuthenticatable::class.java)
            assertThat(account.hasCredentialFor(AuthProvider.GOOGLE)).isFalse()
        }

        @Test
        @DisplayName("주소가 다른 계정에는 자동 연동할 수 없다")
        fun `refuses an account with a different address`() {
            val account = emailAccount(email = "reader@contentria.com")

            assertThatThrownBy {
                account.autoLinkSocialProvider(
                    AuthProvider.GOOGLE, "google-subject-1", providerEmail = "other@contentria.com", emailVerifiedByProvider = true,
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("이미 그 제공자가 연결된 계정에는 다시 연동하지 않는다")
        fun `refuses a provider that is already linked`() {
            val account = socialAccount(email = "reader@contentria.com", providerId = "google-subject-1")

            assertThatThrownBy {
                account.autoLinkSocialProvider(
                    AuthProvider.GOOGLE, "google-subject-2", providerEmail = "reader@contentria.com", emailVerifiedByProvider = true,
                )
            }.isInstanceOf(AccountException.ProviderAlreadyLinked::class.java)
        }
    }

    @Nested
    @DisplayName("계정 생명주기")
    inner class Lifecycle {

        @Test
        @DisplayName("정지한 계정은 복구할 수 있다")
        fun `a suspended account can be reinstated`() {
            val account = socialAccount()

            account.suspend()
            account.reinstate()

            assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        }

        @Test
        @DisplayName("탈퇴는 되돌릴 수 없다")
        fun `closing is terminal`() {
            val account = socialAccount()
            account.close()

            assertThat(account.status).isEqualTo(AccountStatus.DELETED)
            assertThatThrownBy { account.reinstate() }
                .isInstanceOf(AccountException.StatusTransitionNotAllowed::class.java)
            assertThatThrownBy { account.close() }
                .isInstanceOf(AccountException.StatusTransitionNotAllowed::class.java)
        }

        @Test
        @DisplayName("탈퇴하면 자격증명을 모두 지운다. 소셜 신원을 다른 계정이 다시 쓸 수 있다")
        fun `closing releases every credential`() {
            val account = emailAccount()
            account.linkSocialProvider(AuthProvider.GOOGLE, "google-subject-1")

            account.close()

            assertThat(account.credentials).isEmpty()
        }

        @Test
        @DisplayName("탈퇴하면 이메일을 익명화한다. 같은 주소로 다시 가입할 수 있다")
        fun `closing anonymises the email address`() {
            val account = emailAccount(email = "reader@contentria.com")

            account.close()

            assertThat(account.email)
                .isNotEqualTo("reader@contentria.com")
                .contains(account.id.toString())
                .endsWith(".invalid")
            assertThat(account.email.length).isLessThanOrEqualTo(Account.EMAIL_MAX_LENGTH)
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
