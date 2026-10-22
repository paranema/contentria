package com.contentria.member.application.service

import com.contentria.member.application.provided.HumanAction
import com.contentria.member.application.provided.HumanProof
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.required.HumanVerifier
import com.contentria.member.application.required.HumanVerifierUnavailable
import com.contentria.member.application.required.InvisibleAssessment
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HumanCheckServiceTest {

    /** Invisible tokens look up [invisible]; a checkbox token passes when it is in [checkboxes]. */
    private val invisible = mutableMapOf<String, InvisibleAssessment>()
    private val checkboxes = mutableSetOf<String>()
    private var unavailable = false

    private val verifier = object : HumanVerifier {
        override fun assessInvisible(token: String): InvisibleAssessment? {
            if (unavailable) throw HumanVerifierUnavailable()
            return invisible[token]
        }

        override fun acceptsCheckbox(token: String): Boolean {
            if (unavailable) throw HumanVerifierUnavailable()
            return token in checkboxes
        }
    }

    private val service = HumanCheckService(verifier, HumanCheckPolicy(required = true, minimumScore = 0.5))

    @Test
    @DisplayName("보이지 않는 확인의 점수가 기준 이상이고 같은 동작이면 통과한다")
    fun `a good invisible score passes`() {
        invisible["t"] = InvisibleAssessment(0.5, "login")

        assertThatCode { service.check(HumanProof("t", null), HumanAction.LOGIN) }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("점수가 낮거나, 다른 동작의 토큰이거나, 받아들여지지 않는 토큰이거나, 토큰이 없으면 체크박스를 요구한다")
    fun `anything short of that asks for the checkbox`() {
        invisible["low"] = InvisibleAssessment(0.49, "login")
        invisible["elsewhere"] = InvisibleAssessment(0.9, "sign_up")

        listOf(HumanProof("low", null), HumanProof("elsewhere", null), HumanProof("unknown", null), HumanProof(null, null), HumanProof(" ", ""))
            .forEach { proof ->
                assertThatThrownBy { service.check(proof, HumanAction.LOGIN) }
                    .describedAs(proof.toString())
                    .isInstanceOf(MemberUseCaseException.HumanCheckRequired::class.java)
            }
    }

    @Test
    @DisplayName("체크박스 토큰이 오면 그것만 본다. 받아들여지면 통과, 아니면 실패다")
    fun `the checkbox decides alone`() {
        checkboxes += "ticked"
        invisible["low"] = InvisibleAssessment(0.1, "login")

        assertThatCode { service.check(HumanProof("low", "ticked"), HumanAction.LOGIN) }.doesNotThrowAnyException()
        assertThatThrownBy { service.check(HumanProof(null, "forged"), HumanAction.LOGIN) }
            .isInstanceOf(MemberUseCaseException.HumanCheckFailed::class.java)
    }

    @Test
    @DisplayName("공급자에게 물을 수 없으면 통과시키지 않는다")
    fun `no provider, no pass`() {
        unavailable = true

        assertThatThrownBy { service.check(HumanProof("t", null), HumanAction.SIGN_UP) }
            .isInstanceOf(MemberUseCaseException.HumanCheckUnavailable::class.java)
    }

    @Test
    @DisplayName("확인을 끄면(로컬 개발) 토큰 없이 통과한다")
    fun `off for local development`() {
        val off = HumanCheckService(verifier, HumanCheckPolicy(required = false, minimumScore = 0.5))

        assertThatCode { off.check(HumanProof(null, null), HumanAction.SIGN_UP) }.doesNotThrowAnyException()
    }
}
