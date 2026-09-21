package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.jackson3.Jackson3Codec;
import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.okhttp.OkHttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransport;

class OkHttpClientWithJackson3AcceptanceTest extends AbstractTypeSafeClientAcceptanceTest {

    @Override
    protected HttpTransport httpTransport() {
        return new OkHttpTransport();
    }

    @Override
    protected JsonCodec jsonCodec() {
        return new Jackson3Codec();
    }
}
