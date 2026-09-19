package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.jackson3.Jackson3Codec;
import io.github.dfa1.typesafe.jdk.JdkHttpTransport;
import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;

class JdkHttpClientWithJackson3AcceptanceTest extends AbstractTypesafeClientAcceptanceTest {

    @Override
    protected HttpTransport httpTransport() {
        return new JdkHttpTransport();
    }

    @Override
    protected JsonCodec jsonCodec() {
        return new Jackson3Codec();
    }
}
