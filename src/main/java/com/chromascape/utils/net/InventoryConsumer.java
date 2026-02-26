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

public final class InventoryConsumer {
  private static final Logger logger = LogManager.getLogger(InventoryConsumer.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private static final String DEFAULT_BASE = "http://localhost:8081";

  private InventoryConsumer() {}

  public static InventoryPayload fetchInventory() throws IOException, InterruptedException {
    return fetchInventory(DEFAULT_BASE);
  }

  public static InventoryPayload fetchInventory(String baseUrl)
      throws IOException, InterruptedException {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_BASE;
    }
    String url = baseUrl.endsWith("/") ? baseUrl + "inventory" : baseUrl + "/inventory";
    HttpRequest req =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      throw new IOException("Unexpected response code: " + resp.statusCode());
    }
    try {
      return MAPPER.readValue(resp.body(), InventoryPayload.class);
    } catch (JsonProcessingException e) {
      throw new IOException("Failed to parse inventory JSON", e);
    }
  }

  public static boolean verifyInventory() {
    return verifyInventory(DEFAULT_BASE);
  }

  public static boolean verifyInventory(String baseUrl) {
    try {
      InventoryPayload p = fetchInventory(baseUrl);
      logger.info(
          "Inventory endpoint OK — items: {}",
          p.getInventory() == null ? 0 : p.getInventory().size());
      return true;
    } catch (Exception e) {
      logger.error("Failed to verify inventory endpoint: {}", e.getMessage());
      return false;
    }
  }
}
