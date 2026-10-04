package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.codec.jackson2.Jackson2Codec;
import io.github.dfa1.typesafe.client.http.jdk.JdkHttpTransport;
import io.github.dfa1.typesafe.codec.JsonCodec;
import io.github.dfa1.typesafe.client.http.HttpTransport;

class JdkHttpClientWithJackson2AcceptanceTest extends AbstractTypeSafeClientAcceptanceTest {

    @Override
    protected HttpTransport httpTransport() {
        return new JdkHttpTransport();
    }

    @Override
    protected JsonCodec jsonCodec() {
        return new Jackson2Codec();
    }
}
