package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.jackson2.Jackson2Codec;
import io.github.dfa1.typesafe.jdk.JdkHttpTransport;
import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;

class JdkHttpClientWithJackson2AcceptanceTest extends AbstractTypesafeClientAcceptanceTest {

    @Override
    protected HttpTransport httpTransport() {
        return new JdkHttpTransport();
    }

    @Override
    protected JsonCodec jsonCodec() {
        return new Jackson2Codec();
    }
}
