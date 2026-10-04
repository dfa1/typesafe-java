package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.codec.jackson3.Jackson3Codec;
import io.github.dfa1.typesafe.client.http.jdk.JdkHttpTransport;
import io.github.dfa1.typesafe.core.JsonCodec;
import io.github.dfa1.typesafe.client.http.HttpTransport;

class JdkHttpClientWithJackson3AcceptanceTest extends AbstractTypeSafeClientAcceptanceTest {

    @Override
    protected HttpTransport httpTransport() {
        return new JdkHttpTransport();
    }

    @Override
    protected JsonCodec jsonCodec() {
        return new Jackson3Codec();
    }
}
