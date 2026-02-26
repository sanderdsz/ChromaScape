package com.chromascape.scripts;

import com.chromascape.utils.actions.MouseOver;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.runtime.ScriptStoppedException;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.chromascape.utils.actions.Combat;
import com.chromascape.utils.actions.Inventory;
import com.chromascape.utils.net.EventConsumer;
import com.chromascape.utils.net.EventPayload;
import com.chromascape.utils.net.InventoryConsumer;
import com.chromascape.utils.net.InventoryPayload;
import com.chromascape.utils.net.InventoryItem;
import java.awt.Point;
import java.util.Arrays;
import java.util.HashMap;
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
  public static Point lastMonsterLocation;
  public static String lastMonsterName;
  private static final int MAX_ATTEMPTS = 10000;
  private static final ColourObj yellowTag = new ColourObj("YellowTag", new Scalar(27, 245, 103, 0),
      new Scalar(42, 255, 195, 0));

  private static final Duration TITLE_IDLE_THRESHOLD = Duration.ofSeconds(45);
  private boolean nextTitleHandlerIsStart = true;

  private static final Map<String, Point> COMBAT_RESET_TITLES = new HashMap<>() {
    {
      put("Start", new Point(1734, 3468));
      put("End", new Point(1773, 3465));
    }
  };

  private static final Point START_TITLE = COMBAT_RESET_TITLES.get("Start");
  private static final Point END_TITLE = COMBAT_RESET_TITLES.get("End");

  public CombatScript() {
    super();
  }

  @Override
  protected void cycle() {
    logger.info("Combat script start!");
    while (true) {
      startCombat("Sand Crab");

      boolean inCombat = monitorCombat("Sand Crab");
      while (inCombat) {
        logger.info("Combat still active, waiting 1s before re-check");
        try {
          waitMillis(1000);
        } catch (ScriptStoppedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        inCombat = monitorCombat("Sand Crab");
      }

      int yellowCount = countItem(yellowTag, "Yellow");
      while (yellowCount > 0 && !Inventory.isInventoryFull()) {
        clickTag(yellowTag, "Yellow");
        waitRandomMillis(1500, 2000);
        yellowCount = countItem(yellowTag, "Yellow");
      }

      Combat.equipArrowsInInventory(this, logger, "Iron Arrow");

      long idleSeconds = secondsSinceIdleStarted();
      if (idleSeconds > TITLE_IDLE_THRESHOLD.getSeconds()) {
        logger.info("Idle {}s exceeded threshold {}s, performing periodic title check", idleSeconds,
            TITLE_IDLE_THRESHOLD.getSeconds());
        performPeriodicTitleCheck();
      }
    }
  }

  private void startCombat(String npcName) {
    Combat.startCombat(this, logger, npcName, true);
  }

  private boolean monitorCombat(String npcName) {
    return Combat.monitorCombat(this, logger, npcName, "Cooked Meat", false, 60);
  
  }

  private long secondsSinceLastCombatStarted() {
    return Combat.secondsSinceLastCombatStarted();
  }

  private long secondsSinceIdleStarted() {
    return Combat.secondsSinceIdleStarted();
  }

  /**
   * Attempts to locate and click the purple bank object within the game view. It
   * searches for
   * purple contours, then clicks a randomly distributed point inside the contour,
   * retrying up to a
   * maximum number of attempts. Logs failures and stops the script if unable to
   * click successfully.
   */
  private String clickMonster() {
    Point clickLocation = null;
    final int retryCycles = 3;

    for (int attempt = 1; attempt <= retryCycles; attempt++) {
      try {
        clickLocation = PointSelector.getRandomPointInColour(controller().zones().getGameView(), "Purple", MAX_ATTEMPTS,
            15.0);
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
        return null;
      }
    }
    if (clickLocation == null) {
      logger.warn("clickMonster: could not find any purple point after {} attempts", retryCycles);
      return null;
    }
    try {
      controller().mouse().moveTo(clickLocation, "fast");
      controller().mouse().leftClick();
      String monsterName = captureMonsterName();
      logger.info("Clicked on purple monster: {}", monsterName);
      lastMonsterLocation = clickLocation;
      lastMonsterName = monsterName;
      return monsterName;
    } catch (Exception e) {
      logger.error("Error while clicking monster: {}", e.getMessage(), e);
      return null;
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
      clickLocation = PointSelector.getRandomPointByColourObj(
          controller().zones().getGameView(), colour, MAX_ATTEMPTS, 15.0);
    } catch (Exception e) {
      logger.error("Failed while generating {} item click location: {}", colourName, String.valueOf(e));
      return;
    }

    if (clickLocation == null) {
      // here
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
      Duration maxAttempt = Duration.ofSeconds(100);
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
   * Blocks until the `Is idle` flag from the events endpoint remains true
   * continuously for the
   * requested `required` duration, or until `maxWait` elapses.
   *
   * @return true if continuous idle observed for `required`, false if timed out
   *         or interrupted
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

  private void scrollWheelUpDownForTwoSeconds() {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
    try {
      while (Instant.now().isBefore(deadline)) {
        int rotation = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        controller().mouse().scrollWheel(rotation);
        waitMillis(60);
      }
    } catch (ScriptStoppedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void performPeriodicTitleCheck() {
    boolean inCombat = true;
    try {
      EventPayload payload = EventConsumer.fetchEvent();
      if (payload == null) {
        logger.debug("Periodic authentication: event payload missing, assuming not in combat");
        inCombat = false;
      } else {
        inCombat = !payload.isIdle();
      }
    } catch (Exception e) {
      logger.error("Failed to fetch events for periodic title check: {}", e.getMessage());
      inCombat = true;
    }
    if (!inCombat) {
      if (nextTitleHandlerIsStart) {
        handleStartTitle();
      } else {
        handleEndTitle();
      }
      nextTitleHandlerIsStart = !nextTitleHandlerIsStart;
    }
  }

  private boolean handleStartTitle() {
    waitRandomMillis(600, 800);
    try {
      logger.info("Going back to start title.");
      controller().walker().pathTo(START_TITLE, true);
      waitRandomMillis(4000, 6000);
    } catch (Exception e) {
      logger.error("Walker error pathing to start title {}: {}", START_TITLE, e.getMessage(), e);
      stop();
    }
    return false;
  }

  private boolean handleEndTitle() {
    waitRandomMillis(600, 800);
    try {
      logger.info("Going back to end title.");
      controller().walker().pathTo(END_TITLE, true);
      waitRandomMillis(4000, 6000);
    } catch (Exception e) {
      logger.error("Walker error pathing to end title {}: {}", END_TITLE, e.getMessage(), e);
      stop();
    }
    return false;
  }
}
