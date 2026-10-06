package travelplanner;

import java.io.IOException;

/**
 * ABSTRACT CLASS: common code shared by every online service client.
 *
 * Subclasses (inheritance): WikipediaClient, WikidataClient, NominatimClient, OverpassClient,
 * OpenRouteServiceClient. Each one calls super("name") in its constructor (constructor chaining)
 * and must implement the abstract method getPurpose().
 *
 * What the subclasses inherit for free:
 *   - a service name for log messages
 *   - "reachable" tracking: after a network failure we stop calling that service (saves time)
 *   - fetchCached(): look in the cache first, otherwise download and cache the answer
 *
 * You cannot write "new ApiClient(...)" - an abstract class only exists to be extended.
 */
public abstract class ApiClient {

    private final String serviceName;
    // volatile: several threads (the description thread pool) read and write this flag
    private volatile boolean reachable = true;

    protected ApiClient(String serviceName) {
        this.serviceName = serviceName;
    }

    /** What this service is used for in the app. Every subclass MUST implement it. */
    public abstract String getPurpose();

    public String getServiceName() {
        return serviceName;
    }

    public boolean isReachable() {
        return reachable;
    }

    /**
     * Cache first, then the network. Only non-empty answers are cached.
     * Throws IOException if the service is unreachable or answers with an error.
     */
    protected String fetchCached(String cacheKey, String url, String authKey, int timeoutSeconds) throws IOException {
        String cached = CacheManager.get(cacheKey);
        if (cached != null) return cached;
        if (!reachable) throw new IOException(serviceName + " is not reachable right now");
        String response = HttpUtil.get(url, authKey, timeoutSeconds);
        if (shouldCache(response)) CacheManager.put(cacheKey, response);
        return response;
    }

    /** Hook method: subclasses may override it to decide which answers are worth caching. */
    protected boolean shouldCache(String response) {
        return response != null && !response.isBlank();
    }

    /**
     * Called by subclasses when a request fails. "HTTP 404" means the service works but had no
     * answer; anything else (timeout, no internet) means we stop calling it for this session.
     */
    protected void recordFailure(IOException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (!msg.startsWith("HTTP ")) {
            reachable = false;
            log("unreachable (" + msg + "), skipping it for now");
        }
    }

    protected void log(String message) {
        System.out.println("[" + serviceName + "] " + message);
    }

    @Override
    public String toString() {
        return serviceName + " - " + getPurpose();
    }
}
