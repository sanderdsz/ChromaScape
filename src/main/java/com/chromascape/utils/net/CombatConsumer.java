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

public final class CombatConsumer {
  private static final Logger logger = LogManager.getLogger(CombatConsumer.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  private static final String DEFAULT_BASE = "http://localhost:8081";

  private CombatConsumer() {}

  public static CombatPayload fetchCombat() throws IOException, InterruptedException {
    return fetchCombat(DEFAULT_BASE);
  }

  public static CombatPayload fetchCombat(String baseUrl) throws IOException, InterruptedException {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_BASE;
    }
    String url = baseUrl.endsWith("/") ? baseUrl + "combat" : baseUrl + "/combat";
    HttpRequest req =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      throw new IOException("Unexpected response code: " + resp.statusCode());
    }
    try {
      return MAPPER.readValue(resp.body(), CombatPayload.class);
    } catch (JsonProcessingException e) {
      throw new IOException("Failed to parse combat JSON", e);
    }
  }

  public static boolean verifyCombat() {
    return verifyCombat(DEFAULT_BASE);
  }

  public static boolean verifyCombat(String baseUrl) {
    try {
      CombatPayload p = fetchCombat(baseUrl);
      logger.info("Combat endpoint OK — inCombat: {}, npcName: {}", p.isInCombat(), p.getNpcName());
      return true;
    } catch (Exception e) {
      logger.error("Failed to verify combat endpoint: {}", e.getMessage());
      return false;
    }
  }
}
