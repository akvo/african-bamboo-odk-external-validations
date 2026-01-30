package org.akvo.afribamodkvalidator.data.network

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class BasicAuthInterceptor @Inject constructor(
    private val authCredentials: AuthCredentials
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        if (authCredentials.isSet) {
            val credential = Credentials.basic(
                authCredentials.username,
                authCredentials.password
            )
            requestBuilder.header("Authorization", credential)
        }

        return chain.proceed(requestBuilder.build())
    }
}
