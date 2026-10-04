package io.github.dfa1.typesafe.client.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class OnnxTest {

    @Test
    void findsTheOnlyModelInTheOnnxSubdirectoryWhateverItsName(@TempDir Path dir) throws IOException {
        // Given
        Files.createDirectories(dir.resolve("onnx"));
        Files.createFile(dir.resolve("onnx/model_q4.onnx"));
        Files.createFile(dir.resolve("onnx/model_q4.onnx_data"));

        // When
        Path result = Onnx.model(dir);

        // Then
        assertThat(result).isEqualTo(dir.resolve("onnx/model_q4.onnx"));
    }

    @Test
    void findsAModelDirectlyInTheDirectory(@TempDir Path dir) throws IOException {
        // Given
        Files.createFile(dir.resolve("model.onnx"));

        // When
        Path result = Onnx.model(dir);

        // Then
        assertThat(result).isEqualTo(dir.resolve("model.onnx"));
    }

    @Test
    void prefersModelOnnxAmongSeveral(@TempDir Path dir) throws IOException {
        // Given
        Files.createDirectories(dir.resolve("onnx"));
        Files.createFile(dir.resolve("onnx/model.onnx"));
        Files.createFile(dir.resolve("onnx/model_fp16.onnx"));

        // When
        Path result = Onnx.model(dir);

        // Then
        assertThat(result).isEqualTo(dir.resolve("onnx/model.onnx"));
    }

    @Test
    void rejectsAnAmbiguousDirectory(@TempDir Path dir) throws IOException {
        // Given
        Files.createDirectories(dir.resolve("onnx"));
        Files.createFile(dir.resolve("onnx/model_q4.onnx"));
        Files.createFile(dir.resolve("onnx/model_fp16.onnx"));

        // When
        Throwable result = catchThrowable(() -> Onnx.model(dir));

        // Then
        assertThat(result).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("several .onnx files");
    }

    @Test
    void rejectsADirectoryWithoutAModel(@TempDir Path dir) {
        // When
        Throwable result = catchThrowable(() -> Onnx.model(dir));

        // Then
        assertThat(result).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hf download");
    }
}
