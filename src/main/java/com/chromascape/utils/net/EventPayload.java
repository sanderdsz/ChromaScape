package com.chromascape.utils.net;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** POJO model for the /events JSON payload. */
public class EventPayload {
  @JsonProperty("Animation ID")
  private int animationId;

  @JsonProperty("Animation pose ID")
  private int animationPoseId;

  @JsonProperty("Is idle")
  private boolean isIdle;

  @JsonProperty("Last chat message")
  private String lastChatMessage;

  @JsonProperty("Last 5 chat messages")
  private List<String> last5ChatMessages;

  @JsonProperty("Current run energy")
  private int currentRunEnergy;

  @JsonProperty("Current special attack energy")
  private int currentSpecialAttackEnergy;

  @JsonProperty("World location")
  private String worldLocation;

  // Getters
  public int getAnimationId() {
    return animationId;
  }

  public int getAnimationPoseId() {
    return animationPoseId;
  }

  public boolean isIdle() {
    return isIdle;
  }

  public String getLastChatMessage() {
    return lastChatMessage;
  }

  public List<String> getLast5ChatMessages() {
    return last5ChatMessages;
  }

  public int getCurrentRunEnergy() {
    return currentRunEnergy;
  }

  public int getCurrentSpecialAttackEnergy() {
    return currentSpecialAttackEnergy;
  }

  public String getWorldLocation() {
    return worldLocation;
  }

  @Override
  public String toString() {
    return "EventPayload{"
        + "animationId="
        + animationId
        + ", animationPoseId="
        + animationPoseId
        + ", isIdle="
        + isIdle
        + ", lastChatMessage='"
        + lastChatMessage
        + '\''
        + ", last5ChatMessages="
        + last5ChatMessages
        + ", currentRunEnergy="
        + currentRunEnergy
        + ", currentSpecialAttackEnergy="
        + currentSpecialAttackEnergy
        + ", worldLocation='"
        + worldLocation
        + '\''
        + '}';
  }
}
