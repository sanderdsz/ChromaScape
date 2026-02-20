package com.chromascape.scripts;

import com.chromascape.utils.actions.MouseOver;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.runtime.ScriptStoppedException;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import java.util.List;
import java.util.ArrayList;
import com.chromascape.utils.net.EventConsumer;
import com.chromascape.utils.net.EventPayload;
import com.chromascape.utils.net.InventoryConsumer;
import com.chromascape.utils.net.InventoryPayload;
import com.chromascape.utils.net.InventoryItem;
import com.chromascape.utils.net.SkillConsumer;
import java.awt.Point;
import java.util.Arrays;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import org.bytedeco.opencv.opencv_core.Scalar;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CombatScript extends com.chromascape.base.BaseScript {

  private final Logger logger = LogManager.getLogger(CombatScript.class);
  private OptionalInt currentHitpoints = OptionalInt.empty();
  public static Point lastMonsterLocation;
  private static final int MAX_ATTEMPTS = 10000;
  private static final ColourObj yellowTag = new ColourObj("YellowTag", new Scalar(27, 245, 103, 0), new Scalar(42, 255, 195, 0));

  public CombatScript() {
    super();
  }

  @Override
  protected void cycle() {
    logger.info("Combat start!");
    clickMonster();
    waitUntilStopCombat(25);

    currentHitpoints = SkillConsumer.fetchCurrentHitpoints();
    currentHitpoints.ifPresent(hp -> logger.info("Current hitpoints: {}", hp));

    if (currentHitpoints.isPresent() && currentHitpoints.getAsInt() < 10) {
      logger.error("Critical hitpoints {} detected; stopping script", currentHitpoints.getAsInt());
      stop();
      return;
    }

    /* 
    if (currentHitpoints.isPresent() && currentHitpoints.getAsInt() < 60) {
      logger.info("Current hitpoints {} below 60, waiting for idle and before picking up items", currentHitpoints.getAsInt());
      int[] cookedMeatPositions = findItemPositions("Cooked Meat");
      if (cookedMeatPositions.length == 0) {
        logger.warn("Cooked Meat not found while low on hitpoints; skipping heal and continuing");
      } else {
        clickOnInventoryPosition(cookedMeatPositions[0]);
        waitRandomMillis(1000, 1500);
      }
    }
    */
    
    pressRandomArrowForTwoSeconds();
    int yellowCount = countItem(yellowTag, "Yellow");

    while (!isInventoryFull()) {
      yellowCount = countItem(yellowTag, "Yellow");
      if (yellowCount <= 0) {
        break;
      }
      clickTag(yellowTag, "Yellow");
      waitRandomMillis(1500, 2000);
    }

    while (findItemPositions("Big Bones").length > 0) {
      clickOnInventoryPosition(findItemPositions("Big Bones")[0]);
      waitRandomMillis(1000, 1500);
      findItemPositions("Big Bones");
    }

    while (findItemPositions("Bones").length > 0) {
      clickOnInventoryPosition(findItemPositions("Bones")[0]);
      waitRandomMillis(1000, 1500);
      findItemPositions("Bones");
    }

    while (findItemPositions("Iron Arrow").length > 0) {
      clickOnInventoryPosition(findItemPositions("Iron Arrow")[0]);
      waitRandomMillis(1000, 1500);
      findItemPositions("Iron Arrow");
    }
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
      String monsterName = captureMonsterName();
      logger.info("Clicked on purple monster: {}", monsterName);
      lastMonsterLocation = clickLocation;
    } catch (Exception e) {
      logger.error("Error while clicking monster: {}", e.getMessage(), e);
    }
  }

  private String captureMonsterName() {
    try {
      return sanitizeMonsterName(MouseOver.getText(this));
    } catch (IOException e) {
      logger.debug("MouseOver name read failed: {}", e.getMessage());
      return null;
    }
  }

  private String sanitizeMonsterName(String raw) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.replace("\u00A0", " ");
    String trimmed = cleaned.trim();
    if (!trimmed.toLowerCase().startsWith("attack")) {
      return null;
    }
    cleaned = cleaned.replaceAll("(?i)attack", " ");
    int parenIndex = cleaned.indexOf('(');
    if (parenIndex >= 0) {
      cleaned = cleaned.substring(0, parenIndex);
    }
    cleaned = cleaned.replaceAll("[^\\p{L}\\s]", " ");
    cleaned = cleaned.replaceAll("\\s+", " ").trim();
    return cleaned.isEmpty() ? null : cleaned;
  }

  private void clickTag(ColourObj colour, String colourName) {
    Point clickLocation = new Point();
    try {
      clickLocation =
          PointSelector.getRandomPointByColourObj(
              controller().zones().getGameView(), colour, MAX_ATTEMPTS, 15.0);
    } catch (Exception e) {
      logger.error("Failed while generating {} item click location: {}", colourName, String.valueOf(e));
      return;
    }

    if (clickLocation == null) {
      logger.error("click {} Tag click location is null", colourName);
      return;
    }

    try {
      controller().mouse().moveTo(clickLocation, "fast");
      controller().mouse().leftClick();
      logger.info("Clicked on {} item", colourName);
    } catch (Exception e) {
      logger.error("Error while clicking {} item: {}", colourName, e.getMessage(), e);
      return;
    }
  }

  private int countItem(ColourObj colour, String colourName) {
    try {
      BufferedImage gameView = controller().zones().getGameView();
      List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
      int count = objs == null ? 0 : objs.size();
      logger.info("Detected {} {} item(s) on screen", count, colourName);
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
      logger.error("Failed to count {} items: {}", colourName, String.valueOf(e));
      return 0;
    }
  }

  private void waitUntilStopCombat(int timeoutSeconds) {
    logger.info("Waiting until stopped combat...");
    checkInterrupted();
    try {
      Instant start = Instant.now();
      Instant deadline = start.plus(Duration.ofSeconds(timeoutSeconds));
      Duration idleRequired = Duration.ofSeconds(3);
      Duration maxAttempt = Duration.ofSeconds(10);
      while (Instant.now().isBefore(deadline)) {
        try {
          Duration remaining = Duration.between(Instant.now(), deadline);
          if (remaining.isNegative() || remaining.isZero()) {
            logger.info("waitUntilStopCombat: timed out after waiting {} seconds", timeoutSeconds);
            break;
          }
          Duration attemptWindow = remaining.compareTo(maxAttempt) > 0 ? maxAttempt : remaining;
          if (waitForStraightIdle(idleRequired, attemptWindow)) {
            logger.info("Player is idle according to events payload (confirmed 3s)");
            return;
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
    long lastLoggedSecond = 0;
    while (Instant.now().isBefore(deadline)) {
      checkInterrupted();
      try {
        EventPayload payload = EventConsumer.fetchEvent();
        if (payload != null && payload.isIdle()) {
          if (idleStart == null) {
            idleStart = Instant.now();
            lastLoggedSecond = 0;
          } else {
            long elapsedSeconds = Duration.between(idleStart, Instant.now()).getSeconds();
            if (elapsedSeconds > lastLoggedSecond && elapsedSeconds <= required.getSeconds()) {
              lastLoggedSecond = elapsedSeconds;
              if (elapsedSeconds > 0) {
                logger.info("Idle for {} second(s)...", elapsedSeconds);
              }
            }
            if (Duration.between(idleStart, Instant.now()).compareTo(required) >= 0) {
              logger.info("Detected continuous idle for {} seconds", required.getSeconds());
              return true;
            }
          }
        } else {
          // reset when not idle
          idleStart = null;
          lastLoggedSecond = 0;
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

  private void clickOnInventoryPosition(int pos) {
    try {
      Rectangle invSlot = controller().zones().getInventorySlots().get(pos);
      Point click = new Point(invSlot.x + invSlot.width / 2, invSlot.y + invSlot.height / 2);
      controller().mouse().moveTo(click, "fast");
      controller().mouse().leftClick();
      logger.info("Clicked inventory slot {}", pos);
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

  private void pressRandomArrowForTwoSeconds() {
    String direction = ThreadLocalRandom.current().nextBoolean() ? "left" : "right";
    Instant deadline = Instant.now().plus(Duration.ofSeconds(1));
    try {
      controller().keyboard().sendArrowKey(401, direction);
      while (Instant.now().isBefore(deadline)) {
        waitMillis(40);
      }
    } catch (ScriptStoppedException e) {
      Thread.currentThread().interrupt();
    } finally {
      try {
        controller().keyboard().sendArrowKey(402, direction);
      } catch (Exception e) {
        logger.debug("Failed to release {} arrow: {}", direction, e.getMessage());
      }
    }
  }
}
