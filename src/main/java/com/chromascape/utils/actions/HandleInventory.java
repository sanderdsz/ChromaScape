package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.controller.Controller;
import com.chromascape.utils.net.InventoryConsumer;
import com.chromascape.utils.net.InventoryItem;
import com.chromascape.utils.net.InventoryPayload;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lightweight utilities for interrogating player inventory data without duplicating the
 * telemetry plumbing found in multiple scripts.
 */
public final class HandleInventory {

  private static final int KEY_PRESS = 401;
  private static final int KEY_RELEASE = 402;

  private static final Logger logger = LogManager.getLogger(HandleInventory.class);
  private static final int FULL_INVENTORY_COUNT = 28;

  private HandleInventory() {}

  /**
   * Safely clicks the centre point of the provided inventory slot.
   *
   * @param controller the controller that exposes mouse and zone helpers
   * @param slotIndex the inventory slot index to click (0-27)
   */
  public static void leftClickOnInventoryPosition(Controller controller, int slotIndex) {
    if (controller == null) {
      logger.warn("leftClickOnInventoryPosition: controller was null");
      return;
    }
    try {
      List<Rectangle> slots = controller.zones().getInventorySlots();
      if (slotIndex < 0 || slotIndex >= slots.size()) {
        logger.warn("leftClickOnInventoryPosition: slotIndex {} out of range", slotIndex);
        return;
      }
      Rectangle invSlot = slots.get(slotIndex);
      Point click = new Point(invSlot.x + invSlot.width / 2, invSlot.y + invSlot.height / 2);
      controller.mouse().moveTo(click, "fast");
      controller.mouse().leftClick();
      logger.info("Left-clicked inventory slot {}", slotIndex);
    } catch (Exception e) {
      logger.error("Failed to left-click inventory slot {}: {}", slotIndex, e.getMessage());
    }
  }

  /**
   * Determines whether the inventory contains {@link #FULL_INVENTORY_COUNT} valid items.
   *
   * @return {@code true} when the telemetry snapshot reports a full inventory
   */
  public static boolean isInventoryFull() {
    try {
      InventoryPayload payload = InventoryConsumer.fetchInventory();
      if (payload == null || payload.getInventory() == null) {
        logger.debug("Inventory payload empty or null");
        return false;
      }
      List<InventoryItem> inventory = payload.getInventory();
      int validItems = countValidItems(inventory);
      logger.info("Inventory contains {} valid items (raw size {})", validItems, inventory.size());
      return validItems >= FULL_INVENTORY_COUNT;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      logger.error("Inventory fetch interrupted: {}", ie.getMessage());
    } catch (Exception e) {
      logger.error("Failed to fetch inventory: {}", e.getMessage());
    }
    return false;
  }

  /**
   * Finds every inventory slot index containing the requested item name.
   *
   * @param itemName the item to search for; comparisons ignore case
   * @return an ordered list of slot indexes that contain the item
   */
  public static int[] findItemPositions(String itemName) {
    if (itemName == null || itemName.isBlank()) {
      return new int[0];
    }
    try {
      InventoryPayload payload = InventoryConsumer.fetchInventory();
      if (payload == null || payload.getInventory() == null) {
        return new int[0];
      }
      List<InventoryItem> inv = payload.getInventory();
      ArrayList<Integer> positions = new ArrayList<>();
      for (int i = 0; i < inv.size(); i++) {
        InventoryItem item = inv.get(i);
        if (item != null && item.getName() != null && item.getName().equalsIgnoreCase(itemName)) {
          positions.add(i);
        }
      }
      int[] result = toIntArray(positions);
      logger.info("Found item positions for {}: {}", itemName, Arrays.toString(result));
      return result;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return new int[0];
    } catch (Exception e) {
      logger.error("Failed to find item positions for {}: {}", itemName, e.getMessage());
      return new int[0];
    }
  }
  
  /**
   * Shift-drops every inventory slot whose name matches {@code itemName} exactly.
   *
   * @param controller the controller that can move the mouse and issue keyboard events
   * @param itemName the exact name to drop; comparisons ignore case
   * @return {@code true} when at least one matching slot was processed without interruption
   */
  public static boolean dropItemByName(Controller controller, String itemName) {
    // Start Shift-Drop so each click drops instead of picking the item back up
    controller.keyboard().sendModifierKey(KEY_PRESS, "shift");
    BaseScript.waitRandomMillis(100, 250);

    int[] positions = findItemPositions(itemName);
    if (positions.length == 0) {
      logger.info("No positions found for item '{}', cannot drop", itemName);
      return false;
    }
    logger.info("Dropping item '{}' from positions {}", itemName, Arrays.toString(positions));
    for (int pos : positions) {
      leftClickOnInventoryPosition(controller, pos);
      // Add delay if necessary to ensure the click registers before the next action
      try {
        Thread.sleep(100); // Adjust delay as needed
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        logger.error("Interrupted while dropping item '{}': {}", itemName, ie.getMessage());
        return false;
      }
    }
    // Release Shift key after dropping items
    controller.keyboard().sendModifierKey(KEY_RELEASE, "shift");
    return true;
  }

  /**
   * Converts the list of slot indexes into the primitive array expected by callers.
   *
   * @param positions the positions gathered during the search
   * @return a sequential array of the supplied indexes
   */
  private static int[] toIntArray(List<Integer> positions) {
    int[] out = new int[positions.size()];
    for (int i = 0; i < positions.size(); i++) {
      out[i] = positions.get(i);
    }
    return out;
  }

  /**
   * Counts inventory slots that represent real, positive-quantity items.
   *
   * @param inventory the payload's inventory list
   * @return how many slots contain valid items
   */
  private static int countValidItems(List<InventoryItem> inventory) {
    int validCount = 0;
    for (InventoryItem item : inventory) {
      if (isValidItem(item)) {
        validCount++;
      }
    }
    return validCount;
  }

  /**
   * Determines whether an {@link InventoryItem} entry represents a real game item.
   *
   * @param item the inventory entry to evaluate
   * @return {@code true} when the entry contains a non-blank name and a positive quantity
   */
  private static boolean isValidItem(InventoryItem item) {
    if (item == null) {
      return false;
    }
    String name = item.getName();
    if (name == null || name.isBlank() || "null".equalsIgnoreCase(name)) {
      return false;
    }
    return item.getQuantity() > 0;
  }
}
