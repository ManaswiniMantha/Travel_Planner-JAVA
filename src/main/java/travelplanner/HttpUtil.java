package travelplanner;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Small helper so every API client sends HTTP requests the same way
 * (timeouts, User-Agent header, error handling).
 */
public class HttpUtil {

    public static final String USER_AGENT = "TravelPlannerCollegeProject/1.0 (student project)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** HTTP GET. authKey may be null when the API needs no key. */
    public static String get(String url, String authKey, int timeoutSeconds) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET();
        if (authKey != null) builder.header("Authorization", authKey);
        return send(builder.build());
    }

    /** HTTP POST with a text body (JSON or form data). */
    public static String post(String url, String body, String contentType, String authKey,
                              int timeoutSeconds) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authKey != null) builder.header("Authorization", authKey);
        return send(builder.build());
    }

    /** URL-encodes a query parameter value. */
    public static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                // message starts with "HTTP " so callers can tell "server said no" from "no internet"
                throw new IOException("HTTP " + status + " from " + request.uri().getHost());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted");
        } catch (IllegalArgumentException e) {
            throw new IOException("Bad request: " + e.getMessage());
        }
    }
}
