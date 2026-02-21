package com.chromascape.utils.net;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InventoryItem {
  @JsonProperty("Item ID")
  private int itemId;

  @JsonProperty("Name")
  private String name;

  @JsonProperty("Quantity")
  private int quantity;

  public int getItemId() {
    return itemId;
  }

  public String getName() {
    return name;
  }

  public int getQuantity() {
    return quantity;
  }

  @Override
  public String toString() {
    return "InventoryItem{"
        + "itemId="
        + itemId
        + ", name='"
        + name
        + '\''
        + ", quantity="
        + quantity
        + '}';
  }
}
