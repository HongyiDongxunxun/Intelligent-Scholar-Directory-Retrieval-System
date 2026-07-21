package org.scholar.directory.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Component
public class ExposureGuard implements ApplicationRunner {
    private final String serverAddress;
    private final String apiToken;

    public ExposureGuard(@Value("${server.address:127.0.0.1}") String serverAddress,
                         @Value("${app.security.api-token:}") String apiToken) {
        this.serverAddress = serverAddress;
        this.apiToken = apiToken;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isLoopback(serverAddress) && apiToken.isBlank()) {
            throw new IllegalStateException(
                    "Refusing non-loopback API binding without API_ACCESS_TOKEN. "
                            + "Use SERVER_ADDRESS=127.0.0.1 for local review or set a strong token.");
        }
    }

    static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
