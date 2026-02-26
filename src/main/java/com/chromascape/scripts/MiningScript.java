package com.chromascape.scripts;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.MouseOver;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.actions.Inventory;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A mining script that automates basic mining behavior in the client.
 *
 * <p>The script demonstrates how to locate ore, idle until actions settle, and
 * deposit a full inventory at a bank before returning to mining.</p>
 *
 * <p>This implementation is built on top of {@link BaseScript}.</p>
 */
public class MiningScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(MiningScript.class);
  
  Duration idleRequired = Duration.ofSeconds(2);
  Duration maxAttempt = Duration.ofSeconds(100);
  private boolean bankFlag = false;
  private boolean walkedToBank = false;
  private static final Map<String, Point> MINING_RESET_TITLES = new HashMap<>() {
    {
      put("Start", new Point(1431, 3850));
      put("End", new Point(1437, 3833));
    }
  };

  /**
   * Executes one cycle of the script logic.
   *
   * <p>The method keeps mining until the inventory is full, at which point it
   * walks to the bank, waits for idle, opens the bank, and deposits the
   * entire inventory before returning to mining.</p>
   */
  @Override
  protected void cycle() {

    if (Inventory.isInventoryFull()) {
      logger.info("Inventory is full, depositing ore");
      if (!bankFlag) {
        logger.info("Not currently at bank, walking to bank");
        walkedToBank = walkToBank();
      }
      if (walkedToBank) {
        logger.info("Waiting until idle before interacting with bank");
        boolean isIdle = Idler.waitForContinuousIdle(idleRequired, maxAttempt);
        if (isIdle) {
          boolean isBankOpen = Inventory.openBank(this, controller());
          if (isBankOpen && !bankFlag) {
            logger.info("Bank is open, dropping ore");
            bankFlag = true;
            Idler.waitForContinuousIdle(idleRequired, maxAttempt);
            boolean deposited = Inventory.depositEntireInventoryOnBank(controller());
            if (!deposited) {
              logger.warn("Failed to deposit inventory; inventory may still be full");
            } else {
              logger.info("Successfully deposited the entire inventory");
              Inventory.closeBank(this, controller());
              bankFlag = false;
              walkedToBank = false;
              Idler.waitForContinuousIdle(idleRequired, maxAttempt);
            }
          } else if (!isBankOpen) {
            logger.warn("Bank is not open yet; will retry in next cycle");
          }
        }
      }
    }

    if (!bankFlag && !Inventory.isInventoryFull()) {
      walkedToBank = false;
      boolean walkedToStart = walkToStart();
      if (!walkedToStart) {
        logger.warn("Failed to walk to start location; attempting to click ore anyway");
      }
      boolean isIdle = Idler.waitForContinuousIdle(idleRequired, maxAttempt);
      if (isIdle) {
        clickOre("Coal");
      }
      Idler.waitForContinuousIdle(idleRequired, maxAttempt);
    }
  }

  /**
   * Attempts to locate and click on a matching ore rock in the game view.
   *
   * <p>If no suitable rock is found within the retry budget, the method exits
   * so the next cycle can retry.</p>
   *
   * @param oreName the base ore name used to filter mouse-over text (case
   *     insensitive, expected to match like "coal" for "coalrocks")
   */
  private void clickOre(String oreName) {
    try {
      final int maxRetries = 100;
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
        BufferedImage gameView = controller().zones().getGameView();
        Point clickLoc = PointSelector.getRandomPointInColourNoRepeat(gameView, "Cyan", 15, 12.0);
        if (clickLoc == null) {
          logger.info("No ore target found on this pass; will retry next cycle");
          return;
        }
        controller().mouse().moveTo(clickLoc, "medium");
        String mineRock = MouseOver.getText(this);
        if (mineRock != null && mineRock.toLowerCase().contains(oreName.toLowerCase() + "rocks")) {
          controller().mouse().leftClick();
          return;
        }
        logger.info(
            "MouseOver text '{}' is not a rock (attempt {}/{}), retrying",
            mineRock,
            attempt,
            maxRetries);
      }
      logger.warn("Could not find a rock target after {} retries; will retry in next cycle", maxRetries);
    } catch (Exception e) {
      logger.error("Unexpected error while attempting to click ore; continuing script", e);
      return;
    }
  }

  /**
   * Ensures the player returns to the mining start title before seeking ore.
   */
  private boolean walkToStart() {
    if (isOresVisible()) {
      return true;
    }
    try {
      logger.info("Walking to start title.");
      controller().walker().pathTo(MINING_RESET_TITLES.get("Start"), true);
      waitRandomMillis(2000, 4000);
    } catch (Exception e) {
      logger.error("Failed to walk to start title, stopping script", e);
    }
    return false;
  }

  /**
   * Walks toward the bank title in case the booth is not immediately visible.
   */
  private boolean walkToBank() {
    if (isBankVisible()) {
      return true;
    }
    try {
      logger.info("Walking to bank.");
      controller().walker().pathTo(MINING_RESET_TITLES.get("End"), true);
      waitRandomMillis(2000, 4000);
      return isBankVisible();
    } catch (Exception e) {
      logger.error("Failed to walk to bank, stopping script", e);
    }
    return false;
  }

  /**
   * Scans the game view for purple bank booth contours.
   */
  private boolean isBankVisible() {
    Point clickLocation = PointSelector.getRandomPointInColour(
        controller().zones().getGameView(), "Purple", 15, 12.0);
    logger.info("Checking for bank booth colour contours to determine if bank is visible");
    logger.info("Found {} contours matching bank booth colour", clickLocation != null ? 1 : 0);
    return clickLocation != null;
  }

  /**
   * Scans the game view for cyan ore rock contours.
   */
  private boolean isOresVisible() {
    Point clickLocation = PointSelector.getRandomPointInColour(
        controller().zones().getGameView(), "Cyan", 15, 12.0);
    logger.info("Checking for ore rock colour contours to determine if rocks are visible");
    logger.info("Found {} contours matching ore rock colour", clickLocation != null ? 1 : 0);
    return clickLocation != null;
  }
}
