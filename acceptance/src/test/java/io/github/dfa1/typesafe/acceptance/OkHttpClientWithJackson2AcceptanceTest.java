package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.jackson2.Jackson2Codec;
import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.okhttp.OkHttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransport;

class OkHttpClientWithJackson2AcceptanceTest extends AbstractTypeSafeClientAcceptanceTest {

    @Override
    protected HttpTransport httpTransport() {
        return new OkHttpTransport();
    }

    @Override
    protected JsonCodec jsonCodec() {
        return new Jackson2Codec();
    }
}
