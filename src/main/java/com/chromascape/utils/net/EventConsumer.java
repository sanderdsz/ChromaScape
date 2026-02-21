package com.chromascape.utils.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Simple HTTP consumer to GET JSON from /events and parse it.
 *
 * <p>Default base is `http://localhost:8081`. Callers may pass a custom base, or use the
 * no-arg overloads which use the default.
 */
public final class EventConsumer {
  private static final Logger logger = LogManager.getLogger(EventConsumer.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  private static final String DEFAULT_BASE = "http://localhost:8081";

  private EventConsumer() {}

  public static EventPayload fetchEvent() throws IOException, InterruptedException {
    return fetchEvent(DEFAULT_BASE);
  }

  public static EventPayload fetchEvent(String baseUrl) throws IOException, InterruptedException {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_BASE;
    }
    String url = baseUrl.endsWith("/") ? baseUrl + "events" : baseUrl + "/events";
    HttpRequest req =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();

    logger.debug("Requesting events from {}", url);
    HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

    if (resp.statusCode() != 200) {
      throw new IOException("Unexpected response code: " + resp.statusCode());
    }

    try {
      return MAPPER.readValue(resp.body(), EventPayload.class);
    } catch (JsonProcessingException e) {
      throw new IOException("Failed to parse JSON from events endpoint", e);
    }
  }

  public static boolean verifyEndpoint() {
    return verifyEndpoint(DEFAULT_BASE);
  }

  public static boolean verifyEndpoint(String baseUrl) {
    try {
      EventPayload p = fetchEvent(baseUrl);
      logger.info("Events endpoint OK — Last chat message: {}", p.getLastChatMessage());
      return true;
    } catch (Exception e) {
      logger.error("Failed to verify events endpoint: {}", e.getMessage());
      return false;
    }
  }

  // EventPayload is now a separate class: com.chromascape.utils.net.EventPayload
}
