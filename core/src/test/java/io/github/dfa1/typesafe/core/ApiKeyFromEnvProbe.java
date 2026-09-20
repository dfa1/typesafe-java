package io.github.dfa1.typesafe.core;

/** Run in a child process by {@link ApiKeyTest} to test {@link ApiKey#fromEnv()}'s success path
 *  without mutating this JVM's real environment. */
class ApiKeyFromEnvProbe {

    private ApiKeyFromEnvProbe() {
    }

    public static void main(String[] args) {
        System.out.print(ApiKey.fromEnv().value());
    }
}
