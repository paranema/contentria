package com.contentria.member.application.provided

interface LoginWithPasswordUseCase {
    fun login(email: String, rawPassword: String): IssuedSession
}
