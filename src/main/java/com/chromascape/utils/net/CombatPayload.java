package com.chromascape.utils.net;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POJO for /combat endpoint payload. */
public class CombatPayload {
  @JsonProperty("In combat")
  private boolean inCombat;

  @JsonProperty("NPC name")
  private String npcName;

  public boolean isInCombat() {
    return inCombat;
  }

  public String getNpcName() {
    return npcName;
  }

  @Override
  public String toString() {
    return "CombatPayload{" + "inCombat=" + inCombat + ", npcName='" + npcName + '\'' + '}';
  }
}
