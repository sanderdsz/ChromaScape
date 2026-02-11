package com.chromascape.scripts;

import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.runtime.ScriptStoppedException;
import com.chromascape.utils.core.screen.colour.ColourInstances;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.domain.ocr.Ocr;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import java.util.List;
import java.util.ArrayList;
import com.chromascape.utils.net.EventConsumer;
import com.chromascape.utils.net.EventPayload;
import com.chromascape.utils.net.InventoryConsumer;
import com.chromascape.utils.net.InventoryPayload;
import com.chromascape.utils.net.InventoryItem;
import com.chromascape.utils.actions.MovingObject;
import java.awt.*;
import java.awt.Point;
import java.util.Arrays;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.bytedeco.opencv.opencv_core.Scalar;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.chromascape.utils.core.screen.topology.TemplateMatching;

public class CombatScript extends com.chromascape.base.BaseScript {

  private final Logger logger = LogManager.getLogger(CombatScript.class);
  private static final int MAX_ATTEMPTS = 10000;
  private static String lastMessage;
  public static Point lastMonsterLocation;
  private static final String bones = "/images/user/Bones.png";

  private static final ColourObj whiteTag = new ColourObj("WhiteTag", new Scalar(0, 0, 69, 0), new Scalar(1, 1, 148, 0));
  private static final ColourObj greenTag = new ColourObj("GreenTag", new Scalar(59, 89, 91, 0), new Scalar(60, 255, 188, 0));
  private static final ColourObj redTag = new ColourObj("RedTag", new Scalar(0, 51, 110, 0), new Scalar(1, 173, 255, 0));
  private static final ColourObj blueTag = new ColourObj("BlueTag", new Scalar(117, 102, 116, 0), new Scalar(125, 234, 220, 0));

  public CombatScript() {
    super();
  }

  @Override
  protected void cycle() {
    logger.info("Combat start!");
    clickMonster();
    waitUntilStopCombat(10);
    int whiteCount = countItem(whiteTag);
    int greenCount = countItem(greenTag);
    int redCount = countItem(redTag);
    int blueCount = countItem(blueTag);
    while (blueCount > 0) {
      clickTag(blueTag);
      waitRandomMillis(1500, 2000);
      blueCount = countItem(blueTag);
    }
    /* 
    while (!isInventoryFull()) {
      whiteCount = countItem(whiteTag);
      if (whiteCount <= 0) {
        break;
      }
      clickTag(whiteTag);
      waitRandomMillis(1500, 2000);
    }*/
    isInventoryFullOfBones();
    while (findItemPositions("Bones").length > 0) {
      clickOnInventoryPosition(findItemPositions("Bones")[0]);
      waitRandomMillis(1000, 1500);
      findItemPositions("Bones" );
    }
    /* 
    while (redCount > 0) {
      clickTag(redTag);
      waitRandomMillis(2000, 3000);
      redCount = countItem(redTag);
    }
    while (greenCount > 0) {
      clickTag(greenTag);
      waitRandomMillis(2000, 3000);
      greenCount = countItem(greenTag);
    }
      */
  }

  /**
   * Attempts to locate and click the purple bank object within the game view. It searches for
   * purple contours, then clicks a randomly distributed point inside the contour, retrying up to a
   * maximum number of attempts. Logs failures and stops the script if unable to click successfully.
   */
  private void clickMonster() {
    Point clickLocation = null;
    final int retryCycles = 3;

    for (int attempt = 1; attempt <= retryCycles; attempt++) {
      try {
        clickLocation = PointSelector.getRandomPointInColour(controller().zones().getGameView(), "Purple", MAX_ATTEMPTS, 15.0);
      } catch (Exception e) {
        logger.error("Failed while generating monster click location: {}", String.valueOf(e));
      }
      if (clickLocation != null) {
        break;
      }
      logger.warn("clickMonster: no valid Purple point found (attempt {}/{})", attempt, retryCycles);
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    try {
      controller().mouse().moveTo(clickLocation, "fast");
      controller().mouse().leftClick();
      logger.info("Clicked on purple monster object at {}", clickLocation);
      lastMonsterLocation = clickLocation;
    } catch (Exception e) {
      logger.error("Error while clicking monster: {}", e.getMessage(), e);
    }
  }

  private void clickGreenTag() {
    Point clickLocation = new Point();
    try {
      clickLocation =
          PointSelector.getRandomPointByColourObj(
              controller().zones().getGameView(), greenTag, MAX_ATTEMPTS, 15.0);
    } catch (Exception e) {
      logger.error("Failed while generating green item click location: {}", String.valueOf(e));
      return;
    }

    if (clickLocation == null) {
      logger.error("clickGreenTag click location is null");
      return;
    }

    try {
      controller().mouse().moveTo(clickLocation, "fast");
      controller().mouse().leftClick();
      logger.info("Clicked on green item at {}", clickLocation);
    } catch (Exception e) {
      logger.error(e.getMessage());
      return;
    }
  }

  private void clickTag(ColourObj colour) {
    Point clickLocation = new Point();
    try {
      clickLocation =
          PointSelector.getRandomPointByColourObj(
              controller().zones().getGameView(), colour, MAX_ATTEMPTS, 15.0);
    } catch (Exception e) {
      logger.error("Failed while generating {} item click location: {}", colour, String.valueOf(e));
      return;
    }

    if (clickLocation == null) {
      logger.error("click {} Tag click location is null", colour);
      return;
    }

    try {
      controller().mouse().moveTo(clickLocation, "fast");
      controller().mouse().leftClick();
      logger.info("Clicked on {} item at {}", colour, clickLocation);
    } catch (Exception e) {
      logger.error(e.getMessage());
      return;
    }
  }

  private int countItem(ColourObj colour) {
    try {
      //controller().keyboard().sendArrowKey(39, "right");
      //waitRandomMillis(80, 100);
      BufferedImage gameView = controller().zones().getGameView();
      List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
      int count = objs == null ? 0 : objs.size();
      logger.info("Detected {} {} item(s) on screen", count, colour);
      // release native mats
      if (objs != null) {
        for (ChromaObj obj : objs) {
          try {
            obj.release();
          } catch (Exception ignore) {
          }
        }
      }
      return count;
    } catch (Exception e) {
      logger.error("Failed to count {} items: {}", colour, String.valueOf(e));
      return 0;
    }
  }

  private void waitUntilStopCombat(int timeoutSeconds) {
    logger.info("Waiting until stopped combat...");
    checkInterrupted();
    try {
      Instant start = Instant.now();
      Instant deadline = start.plus(Duration.ofSeconds(timeoutSeconds));
      while (Instant.now().isBefore(deadline)) {
        try {
          Duration remaining = Duration.between(Instant.now(), deadline);
          if (!remaining.isNegative() && !remaining.isZero()) {
            if (waitForStraightIdle(Duration.ofSeconds(3), remaining)) {
              logger.info("Player is idle according to events payload (confirmed 3s)");
              return;
            }
          }
        } catch (Exception e) {
          logger.error("Failed while waiting for straight idle: {}", e.getMessage());
        }
      }
    } catch (Exception e) {
      logger.error("Error while waiting for idle", e);
    }
  }

  /**
   * Blocks until the `Is idle` flag from the events endpoint remains true continuously for the
   * requested `required` duration, or until `maxWait` elapses.
   *
   * @return true if continuous idle observed for `required`, false if timed out or interrupted
   */
  private boolean waitForStraightIdle(Duration required, Duration maxWait) {
    Instant start = Instant.now();
    Instant deadline = start.plus(maxWait);
    Instant idleStart = null;
    while (Instant.now().isBefore(deadline)) {
      checkInterrupted();
      try {
        EventPayload payload = EventConsumer.fetchEvent();
        if (payload != null && payload.isIdle()) {
          if (idleStart == null) {
            idleStart = Instant.now();
          } else {
            if (Duration.between(idleStart, Instant.now()).compareTo(required) >= 0) {
              logger.info("Detected continuous idle for {} seconds", required.getSeconds());
              return true;
            }
          }
        } else {
          // reset when not idle
          idleStart = null;
        }
      } catch (Exception e) {
        logger.debug("Failed to fetch events while waiting for straight idle: {}", e.getMessage());
      }

      try {
        waitMillis(100);
      } catch (ScriptStoppedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    logger.info("waitForStraightIdle: timed out waiting for continuous idle of {} seconds", required.getSeconds());
    return false;
  }

  /**
   * Checks the latest chat message for the word "Ironman" (case-insensitive). If present, calling
   * code should avoid picking up items.
   */
  private boolean isIronmanPresent() {
    try {
      Rectangle latestMessage = controller().zones().getChatTabs().get("Latest Message");
      ColourObj red = ColourInstances.getByName("ChatRed");
      ColourObj black = ColourInstances.getByName("Black");
      String textRed = Ocr.extractText(latestMessage, "Plain 12", red, true);
      String textBlack = Ocr.extractText(latestMessage, "Plain 12", black, true);
      String combined =
          (textRed == null ? "" : textRed) + " " + (textBlack == null ? "" : textBlack);
      return combined.toLowerCase().contains("ironman");
    } catch (Exception e) {
      logger.error("Error checking for Ironman in chat: {}", e.getMessage());
      return false;
    }
  }

  private boolean checkChatPopup(String phrase) {
    Rectangle chat = controller().zones().getChatTabs().get("Chat");
    ColourObj black = ColourInstances.getByName("Black");
    String extraction = null;
    try {
      extraction = Ocr.extractText(chat, "Quill 8", black, true);
    } catch (IOException e) {
      logger.error("OCR failed while loading font: {}", String.valueOf(e));
    }
    return extraction != null && extraction.contains(phrase);
  }

  private void clickOnInventoryPosition(int pos) {
    try {
      Rectangle invSlot = controller().zones().getInventorySlots().get(pos);
      Point click = new Point(invSlot.x + invSlot.width / 2, invSlot.y + invSlot.height / 2);
      controller().mouse().moveTo(click, "fast");
      controller().mouse().leftClick();
      logger.info("Clicked inventory slot {} at {}", pos, click);
    } catch (Exception e) {
      logger.error("Failed to click inventory slot {}: {}", pos, e.getMessage());
    }
  }

  private boolean isInventoryFull() {
    try {
      InventoryPayload payload = InventoryConsumer.fetchInventory();
      if (payload == null || payload.getInventory() == null) {
        logger.debug("Inventory payload empty or null");
        return false;
      }
      int validCount = 0;
      for (InventoryItem it : payload.getInventory()) {
        if (it != null && it.getName() != "null" && !it.getName().isBlank() && it.getQuantity() > 0) {
          validCount += 1;
        }
      }
      logger.info("Inventory contains {} valid items (raw size {})", validCount, payload.getInventory().size());
      return validCount >= 28;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      logger.error("Inventory fetch interrupted: {}", ie.getMessage());
      return false;
    } catch (Exception e) {
      logger.error("Failed to fetch inventory: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Finds all inventory positions (0-27) that contain the given item name.
   * Comparison is case-insensitive. Returns an empty array if none found or on error.
   */
  private int[] findItemPositions(String itemName) {
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
        InventoryItem it = inv.get(i);
        if (it != null && it.getName() != null && it.getName().equalsIgnoreCase(itemName)) {
          positions.add(i);
        }
      }
      int[] out = new int[positions.size()];
      for (int i = 0; i < positions.size(); i++) {
        out[i] = positions.get(i);
      }
      logger.info("Found item positions for {}: {}", itemName, Arrays.toString(out));
      return out;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return new int[0];
    } catch (Exception e) {
      logger.error("Failed to find item positions for {}: {}", itemName, e.getMessage());
      return new int[0];
    }
  }

  private boolean isInventoryFullOfBones() {
   try {
      Rectangle invSlot = controller().zones().getInventorySlots().get(27);
      BufferedImage invSlotImg = ScreenManager.captureZone(invSlot);
      Rectangle match = TemplateMatching.match(bones, invSlotImg, 0.05, false);
      logger.info("Inventory slot 27 {} bones template", match != null ? "matches" : "does not match");
      if (match != null) {
        try {
          Point click = new Point(invSlot.x + invSlot.width / 2, invSlot.y + invSlot.height / 2);
          controller().mouse().moveTo(click, "fast");
          controller().mouse().leftClick();
          waitRandomMillis(100, 200);
          logger.info("Clicked inventory slot 27 at {}", click);
        } catch (Exception e) {
          logger.error("Failed to click inventory slot 27: {}", e.getMessage());
        }
        return true;
      }
    } catch (Exception e) {
      logger.error(e);
    }
    return false;
  }
}
