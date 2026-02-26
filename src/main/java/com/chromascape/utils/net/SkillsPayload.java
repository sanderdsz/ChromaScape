package com.chromascape.utils.net;

import java.util.Collections;
import java.util.List;

public class SkillsPayload {
  private final List<SkillItem> skills;

  public SkillsPayload(List<SkillItem> skills) {
    this.skills = skills == null ? Collections.emptyList() : Collections.unmodifiableList(skills);
  }

  public List<SkillItem> getSkills() {
    return skills;
  }

  @Override
  public String toString() {
    return "SkillsPayload{" + "skills=" + skills + '}';
  }
}
