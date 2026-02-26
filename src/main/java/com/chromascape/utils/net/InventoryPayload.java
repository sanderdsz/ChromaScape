package com.chromascape.utils.net;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class InventoryPayload {
  @JsonProperty("Inventory")
  private List<InventoryItem> inventory;

  public List<InventoryItem> getInventory() {
    return inventory;
  }

  @Override
  public String toString() {
    return "InventoryPayload{" + "inventory=" + inventory + '}';
  }
}
