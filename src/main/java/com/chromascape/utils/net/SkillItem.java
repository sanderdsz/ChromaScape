package com.chromascape.utils.net;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SkillItem {
  @JsonProperty("Skill name")
  private String skillName;

  @JsonProperty("Level")
  private int level;

  @JsonProperty("Boosted level")
  private int boostedLevel;

  @JsonProperty("Boosted amount")
  private int boostedAmount;

  @JsonProperty("Current XP")
  private long currentXp;

  public String getSkillName() {
    return skillName;
  }

  public int getLevel() {
    return level;
  }

  public int getBoostedLevel() {
    return boostedLevel;
  }

  public int getBoostedAmount() {
    return boostedAmount;
  }

  public long getCurrentXp() {
    return currentXp;
  }

  @Override
  public String toString() {
    return "SkillItem{"
        + "skillName='"
        + skillName
        + '\''
        + ", level="
        + level
        + ", boostedLevel="
        + boostedLevel
        + ", boostedAmount="
        + boostedAmount
        + ", currentXp="
        + currentXp
        + '}';
  }
}
