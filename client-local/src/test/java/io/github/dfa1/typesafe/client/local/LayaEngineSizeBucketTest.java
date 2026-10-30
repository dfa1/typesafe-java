package io.github.dfa1.typesafe.client.local;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Untagged: needs no model files, unlike {@link LayaEngineTest}. */
class LayaEngineSizeBucketTest {

    @ParameterizedTest
    @CsvSource({"2, 2", "3, 3-5", "5, 3-5", "6, 6-10", "10, 6-10", "11, 11+", "40, 11+"})
    void sizeBucketGroupsOptionCountsLikeRlAgentApi(int k, String expected) {
        // When
        String result = LayaEngine.sizeBucket(k);

        // Then
        assertThat(result).isEqualTo(expected);
    }
}
