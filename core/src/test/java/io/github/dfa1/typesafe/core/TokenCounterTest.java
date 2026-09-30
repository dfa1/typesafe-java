package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenCounterTest {

    private static final EvaluateRequest REQUEST = EvaluateRequest.of(Content.text("hi"), Map.of());

    @Mock
    private TypeSafeClient delegate;

    private final TokenCounter sut = new TokenCounter();

    @Test
    void evaluateAndEvaluateAsyncAddTheirUsage() {
        // Given
        TypeSafeClient client = sut.decorate(delegate);
        given(delegate.evaluate(REQUEST)).willReturn(responseWith(new Usage(10, 2)));
        given(delegate.evaluateAsync(REQUEST)).willReturn(CompletableFuture.completedFuture(responseWith(new Usage(5, 1))));

        // When
        client.evaluate(REQUEST);
        client.evaluateAsync(REQUEST).join();

        // Then
        assertThat(sut.inputTokens()).isEqualTo(15);
        assertThat(sut.outputTokens()).isEqualTo(3);
        assertThat(sut.totalTokens()).isEqualTo(18);
    }

    @Test
    void oneCounterSumsAcrossEveryClientItDecorates() {
        // Given
        TypeSafeClient first = sut.decorate(delegate);
        TypeSafeClient second = sut.decorate(delegate);
        given(delegate.evaluate(REQUEST)).willReturn(responseWith(new Usage(1, 1)));

        // When
        first.evaluate(REQUEST);
        second.evaluate(REQUEST);

        // Then
        assertThat(sut.totalTokens()).isEqualTo(4);
    }

    @Test
    void aFailedCallOrAResponseWithoutUsageAddsNothing() {
        // Given
        TypeSafeClient client = sut.decorate(delegate);
        given(delegate.evaluate(REQUEST))
                .willThrow(new TypeSafeException.BadRequest("nope"))
                .willReturn(responseWith(null));

        // When
        assertThatThrownBy(() -> client.evaluate(REQUEST)).isInstanceOf(TypeSafeException.BadRequest.class);
        client.evaluate(REQUEST);

        // Then
        assertThat(sut.totalTokens()).isZero();
    }

    @Test
    void listModelsAndClosePassThrough() {
        // Given
        TypeSafeClient client = sut.decorate(delegate);
        List<ModelDetails> models = List.of();
        given(delegate.listModels()).willReturn(models);

        // When
        List<ModelDetails> result = client.listModels();
        client.close();

        // Then
        assertThat(result).isSameAs(models);
        then(delegate).should().close();
    }

    private static EvaluateResponse responseWith(Usage usage) {
        return new EvaluateResponse(Model.LATEST, Map.of(), usage, null);
    }
}
