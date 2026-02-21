package com.chromascape.utils.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SkillConsumer {
  private static final Logger logger = LogManager.getLogger(SkillConsumer.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private static final String DEFAULT_BASE = "http://localhost:8081";
  private static final String SKILLS_PATH = "skills";

  private SkillConsumer() {}

  public static SkillsPayload fetchSkills() throws IOException, InterruptedException {
    return fetchSkills(DEFAULT_BASE);
  }

  public static SkillsPayload fetchSkills(String baseUrl) throws IOException, InterruptedException {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_BASE;
    }
    String url = baseUrl.endsWith("/") ? baseUrl + SKILLS_PATH : baseUrl + "/" + SKILLS_PATH;
    HttpRequest req =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      throw new IOException("Unexpected response code: " + resp.statusCode());
    }
    try {
      List<SkillItem> skills =
          MAPPER.readValue(resp.body(), new TypeReference<List<SkillItem>>() {});
      return new SkillsPayload(skills);
    } catch (JsonProcessingException e) {
      throw new IOException("Failed to parse skills JSON", e);
    }
  }

  public static boolean verifySkills() {
    return verifySkills(DEFAULT_BASE);
  }

  public static boolean verifySkills(String baseUrl) {
    try {
      SkillsPayload payload = fetchSkills(baseUrl);
      logger.info(
          "Skills endpoint OK — skills: {}",
          payload.getSkills() == null ? 0 : payload.getSkills().size());
      return true;
    } catch (Exception e) {
      logger.error("Failed to verify skills endpoint: {}", e.getMessage());
      return false;
    }
  }

  public static OptionalInt fetchCurrentHitpoints() {
    return fetchCurrentHitpoints(DEFAULT_BASE);
  }

  public static OptionalInt fetchCurrentHitpoints(String baseUrl) {
    try {
      SkillsPayload payload = fetchSkills(baseUrl);
      return findBoostedLevel(payload, "Hitpoints");
    } catch (Exception e) {
      logger.error("Failed to fetch hitpoints: {}", e.getMessage());
      return OptionalInt.empty();
    }
  }

  private static OptionalInt findBoostedLevel(SkillsPayload payload, String skillName) {
    if (payload == null || payload.getSkills() == null) {
      return OptionalInt.empty();
    }
    for (SkillItem skill : payload.getSkills()) {
      if (skill != null && skillName.equalsIgnoreCase(skill.getSkillName())) {
        return OptionalInt.of(skill.getBoostedLevel());
      }
    }
    return OptionalInt.empty();
  }
}
