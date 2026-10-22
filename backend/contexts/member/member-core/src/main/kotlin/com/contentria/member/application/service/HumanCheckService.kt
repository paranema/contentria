package com.contentria.member.application.service

import com.contentria.member.application.provided.CheckHumanUseCase
import com.contentria.member.application.provided.HumanAction
import com.contentria.member.application.provided.HumanProof
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.required.HumanVerifier
import com.contentria.member.application.required.HumanVerifierUnavailable
import org.springframework.stereotype.Service

/**
 * docs/contexts/member/01-rules.md, 사람 확인. The checkbox, when sent, decides alone: it is what
 * the browser shows after the invisible check fell short. Otherwise the invisible check must
 * score at least [HumanCheckPolicy.minimumScore] for the same action. Anything less asks for the
 * checkbox; a checkbox the provider rejects is a failure.
 */
@Service
class HumanCheckService(
    private val verifier: HumanVerifier,
    private val policy: HumanCheckPolicy,
) : CheckHumanUseCase {

    override fun check(proof: HumanProof, action: HumanAction) {
        if (!policy.required) return
        try {
            val checkbox = proof.v2Token?.takeIf { it.isNotBlank() }
            if (checkbox != null) {
                if (!verifier.acceptsCheckbox(checkbox)) throw MemberUseCaseException.HumanCheckFailed()
                return
            }
            val invisible = proof.v3Token?.takeIf { it.isNotBlank() } ?: throw MemberUseCaseException.HumanCheckRequired()
            val assessment = verifier.assessInvisible(invisible) ?: throw MemberUseCaseException.HumanCheckRequired()
            // A token made on another page, say the login page, does not open the sign-up.
            if (assessment.action != null && assessment.action != action.value) throw MemberUseCaseException.HumanCheckRequired()
            if (assessment.score < policy.minimumScore) throw MemberUseCaseException.HumanCheckRequired()
        } catch (e: HumanVerifierUnavailable) {
            throw MemberUseCaseException.HumanCheckUnavailable()
        }
    }
}

/** [required] false only for local development, where no provider keys exist. */
data class HumanCheckPolicy(val required: Boolean, val minimumScore: Double)
