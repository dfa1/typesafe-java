package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.codec.jackson2.Jackson2Codec;
import io.github.dfa1.typesafe.core.JsonCodec;
import io.github.dfa1.typesafe.client.http.okhttp.OkHttpTransport;
import io.github.dfa1.typesafe.client.http.HttpTransport;

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
