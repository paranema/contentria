package com.contentria.member.application.required

/** Asks the check's provider what a browser's token is worth. Whether that is enough is not its call. */
interface HumanVerifier {
    /** The invisible check's verdict on [token], or null when the provider does not accept the token. */
    fun assessInvisible(token: String): InvisibleAssessment?

    /** Whether the provider accepts [token] from the checkbox. */
    fun acceptsCheckbox(token: String): Boolean
}

/** [score] from 0.0 (a script) to 1.0 (a person). [action] as the page named it, when the provider says. */
data class InvisibleAssessment(val score: Double, val action: String?)

/** The provider could not be asked. Nothing is let through meanwhile. */
class HumanVerifierUnavailable(cause: Throwable? = null) : RuntimeException("The human check provider could not be reached", cause)
