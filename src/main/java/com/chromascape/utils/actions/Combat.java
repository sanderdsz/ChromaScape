package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.runtime.ScriptStoppedException;
import com.chromascape.utils.net.CombatConsumer;
import com.chromascape.utils.net.CombatPayload;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Utilities for initiating and monitoring in-game combat.
 *
 * <p>This class provides helper operations used by scripts to reliably start an attack on a
 * detected NPC and then monitor the player's combat state until combat has been seen and then
 * finished, or a timeout occurs. It bridges UI interactions (locating and clicking on the
 * purple monster marker and reading the mouse-over name) with remote telemetry (the
 * CombatConsumer payload) so script logic can make robust decisions about when to proceed.
 *
 * <p>Usage notes:
 * - Call `startCombat(...)` to locate and click a target; it will attempt several retries and
 *   return once a click attempt has been performed (or aborts on interruption).
 * - Call `monitorCombat(...)` to poll the combat telemetry until combat is detected and then
 *   completed, returning whether the player is still in combat when the method exits.
 *
 * <p>Threading & interruption: methods may block briefly while waiting; they honor
 * `BaseScript.checkInterrupted()` and convert interruptions into cooperative cancellation.
 *
 * <p>Logging: methods emit informative messages about progress and fallbacks; callers may
 * supply a logger or rely on the class logger for context.
 */
public final class Combat {

  private static final Logger logger = LogManager.getLogger(Combat.class);

  private static volatile Instant lastCombatStart;
  private static volatile Instant lastIdleStart;
  private static final Duration ACTION_DELAY = Duration.ofSeconds(1);
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(2);
  private static final int LOCATE_RETRY_CYCLES = 5;
  private static final int MAX_ATTEMPTS = 100;

  private Combat() {}

  /**
   * Clicks the purple NPC marker to initiate an attack on the requested monster.
   *
   * <p>Uses a random point inside the detected purple contour and fires a fast left click so
   * that the game's combat UI receives the interaction quickly.
   *
   * @param script owning script (provides controller utilities)
   * @param logger logger to include contextual messages about the click
   * @param npcName friendly NPC name for logging clarity
   * @param bypassMonsterCheck whether to bypass the monster name check
   */
  public static void startCombat(BaseScript script, Logger logger, String npcName, boolean bypassMonsterCheck) {
    if (bypassMonsterCheck) {
      Point target = null;
      for (int attempt = 1; attempt <= LOCATE_RETRY_CYCLES; attempt++) {
        try {
          target = PointSelector.getRandomPointInColour(
              script.controller().zones().getGameView(), "Purple", MAX_ATTEMPTS, 15.0);
        } catch (Exception e) {
          logger.error("startCombat: failed to select purple target point: {}", e.getMessage(), e);
        }
        if (target != null) {
          break;
        }
        logger.warn(
            "startCombat: no purple target point found (attempt {}/{}), retrying...",
            attempt,
            LOCATE_RETRY_CYCLES);
        if (attempt < LOCATE_RETRY_CYCLES) {
          BaseScript.waitMillis((int) Math.min(ACTION_DELAY.toMillis(), Integer.MAX_VALUE));
        }
      }

      if (target == null) {
        logger.warn("startCombat: unable to initiate combat because no purple target point was found");
        return;
      }

      try {
        script.controller().mouse().moveTo(target, "fast");
        script.controller().mouse().leftClick();
      } catch (Exception e) {
        logger.error("Error while clicking monster: {}", e.getMessage(), e);
      }
    } else {
      String expectedName = normalizeName(npcName);
      MonsterCandidate candidate = findMatchingTarget(script, logger, expectedName, npcName);
      if (candidate == null) {
        logger.warn("startCombat: failed to locate monster to attack, aborting combat initiation");
        return;
      }
      logger.info(
        "startCombat: clicking on {}",
        describeTarget(candidate.name, npcName));
      BaseScript.waitMillis((int) Math.min(ACTION_DELAY.toMillis(), Integer.MAX_VALUE));
      boolean successfulClick = clickMonster(script, candidate.location, logger);
      if (!successfulClick) {
        logger.warn("startCombat: failed to click on monster, aborting combat initiation");
      }
      logger.info("startCombat: clicking on target with color marker");
    }
  }

  /**
   * Polls the combat telemetry until the player enters and then leaves combat.
   *
   * <p>The loop runs for up to {@code TIMEOUT} seconds, waiting {@code CHECK_INTERVAL} between
   * polls. Once combat is detected it will keep checking until the payload reports idle, then
   * waits one extra tick to confirm a new fight didn’t immediately begin before returning.
   *
   * @param logger the logger for emitting progress and fallback information
   * @param npcName the expected NPC name for logging/contextual messaging
   * @return {@code true} if the player is still in combat when the method exits, {@code false}
   *     when idle is confirmed
   */
  public static boolean monitorCombat(Logger logger, String npcName) {
    // Track this monitor pass lifecycle.
    Instant start = Instant.now();
    // seenCombat: we have observed at least one in-combat payload in this pass.
    boolean seenCombat = false;
    // lastInCombat: latest payload status, returned if interrupted/timed out.
    boolean lastInCombat = false;

    // Reset/initialize shared timestamps for other helpers.
    lastCombatStart = start;
    Instant nextCheck = start;
    // If no combat is ever seen, this marks when idle timing likely began.
    Instant idleCandidateStart = null;
    String normalizedNpcName = normalizeName(npcName);
    String targetForLogs = describeTarget(normalizedNpcName, null);

    // Poll until timeout, throttled by CHECK_INTERVAL.
    while (Duration.between(start, Instant.now()).compareTo(TIMEOUT) < 0) {
      logger.debug("monitorCombat: waiting for combat... (seenCombat={}, lastInCombat={})", seenCombat, lastInCombat);
      BaseScript.checkInterrupted();
      Instant now = Instant.now();

      // Sleep until the next poll tick to avoid hot-looping.
      if (now.isBefore(nextCheck)) {
        try {
          Duration waitTime = Duration.between(now, nextCheck);
          if (!waitTime.isNegative() && !waitTime.isZero()) {
            BaseScript.waitMillis((int) Math.min(waitTime.toMillis(), Integer.MAX_VALUE));
          }
        } catch (ScriptStoppedException e) {
          Thread.currentThread().interrupt();
          return lastInCombat;
        }
        continue;
      }
      nextCheck = now.plus(CHECK_INTERVAL);

      // Fetch combat telemetry and update state machine.
      try {
        CombatPayload payload = CombatConsumer.fetchCombat();
        if (payload == null) {
          logger.info("monitorCombat: no combat payload, retrying...");
        } else {
          lastInCombat = payload.isInCombat();

          // Entered/continuing combat.
          if (lastInCombat) {
            if (!seenCombat) {
              lastCombatStart = Instant.now();
              lastIdleStart = null;
              idleCandidateStart = null;
              String detectionTarget =
                  describeDetectionTarget(normalizedNpcName, payload.getNpcName());
              logger.info("monitorCombat: combat detected against {}", detectionTarget);
            }
            seenCombat = true;

          // Combat has ended after being seen at least once; we can proceed.
          } else if (seenCombat) {
            // Confirm end-of-combat after one game tick (1s) to avoid false idle
            // when auto-combat re-engages immediately after a kill.
            try {
              BaseScript.waitMillis((int) Math.min(CHECK_INTERVAL.toMillis(), Integer.MAX_VALUE));
            } catch (ScriptStoppedException e) {
              Thread.currentThread().interrupt();
              return lastInCombat;
            }

            try {
              CombatPayload confirmPayload = CombatConsumer.fetchCombat();
              if (confirmPayload != null && confirmPayload.isInCombat()) {
                lastInCombat = true;
                logger.info("monitorCombat: combat resumed within 1 tick, continuing monitor");
                continue;
              }
              if (confirmPayload == null) {
                logger.info("monitorCombat: confirmation payload missing after 1 tick, keeping monitor active");
                continue;
              }
            } catch (Exception e) {
              logger.info("monitorCombat: failed to confirm end-of-combat after 1 tick: {}", e.getMessage());
              continue;
            }

            lastIdleStart = Instant.now();
            logger.info("monitorCombat: combat finished, player idle (confirmed after 1 tick)");
            return false;
          }

          // We have not seen combat yet; treat this window as idle candidate time.
          if (!seenCombat && idleCandidateStart == null) {
            idleCandidateStart = Instant.now();
            lastIdleStart = idleCandidateStart;
          }
        }
      } catch (Exception e) {
        logger.info("monitorCombat: failed to fetch combat payload: {}", e.getMessage());
      }
    }

    // Timeout handling: return latest combat status and keep idle timestamp coherent.
    if (!seenCombat) {
      lastIdleStart = idleCandidateStart != null ? idleCandidateStart : start;
      logger.warn("monitorCombat: timed out without seeing combat against {} lastInCombat={}", targetForLogs, lastInCombat);
      return lastInCombat;
    } else {
      lastIdleStart = Instant.now();
      logger.warn("monitorCombat: timed out waiting for combat to finish against {} lastInCombat={}", targetForLogs, lastInCombat);
    }
    logger.debug("monitorCombat: exiting with lastInCombat={}", lastInCombat);
    return lastInCombat;
  }

  /**
   * Reports how many seconds have elapsed since the last combat start was recorded.
   *
   * @return seconds since {@link #lastCombatStart} or {@code -1} if combat has not been seen yet
   */
  public static long secondsSinceLastCombatStarted() {
    Instant last = lastCombatStart;
    if (last == null) {
      return -1;
    }
    logger.debug("Seconds last combat: start={}, now={}", last, Instant.now());
    return Duration.between(last, Instant.now()).getSeconds();
  }

  /**
   * Reports how many seconds the player has been idle according to the combat monitor.
   *
   * @return seconds since {@link #lastIdleStart} or {@code -1} if no idle period is tracked
   */
  public static long secondsSinceIdleStarted() {
    Instant last = lastIdleStart;
    if (last == null) {
      return -1;
    }
    logger.debug("Seconds idle: start={}, now={}", last, Instant.now());
    return Duration.between(last, Instant.now()).getSeconds();
  }

  /**
   * Attempts to locate a monster that matches the expected name within a few retries.
   *
   * <p>The method continues to scan for the purple marker until either a valid match is
   * confirmed or {@link #LOCATE_RETRY_CYCLES} is exhausted. Each failure pauses briefly before
   * the next attempt.
   *
   * @param script script owning the controller
   * @param logger logger used to emit warnings during retries
   * @param expectedName normalized name we want to attack
   * @param npcName raw NPC name used for wording in logs
   * @return a {@link MonsterCandidate} that passed validation or {@code null} when none were found
   */
  private static MonsterCandidate findMatchingTarget(
    BaseScript script, 
    Logger logger, 
    String expectedName, 
    String npcName) {
    for (int attempt = 1; attempt <= LOCATE_RETRY_CYCLES; attempt++) {
      MonsterCandidate candidate = locateMonster(script, logger);
      if (candidate == null) {
        logger.warn(
            "startCombat: locate attempt {}/{} failed, retrying...",
            attempt,
            LOCATE_RETRY_CYCLES);
        if (attempt < LOCATE_RETRY_CYCLES) {
          BaseScript.waitMillis((int) Math.min(ACTION_DELAY.toMillis(), Integer.MAX_VALUE));
          continue;
        }
        return null;
      }
      if (!isExpectedTarget(expectedName, candidate.name)) {
        logger.warn(
            "startCombat: located {} but expected {} (attempt {}/{})",
            describeTarget(candidate.name, null),
            describeTarget(expectedName, npcName),
            attempt,
            LOCATE_RETRY_CYCLES);
        if (attempt < LOCATE_RETRY_CYCLES) {
          BaseScript.waitMillis((int) Math.min(ACTION_DELAY.toMillis(), Integer.MAX_VALUE));
          continue;
        }
        return null;
      }
      return candidate;
    }
    return null;
  }

  /**
   * Scans the current game view for a purple marker and captures its name from the tooltip.
   *
   * @param script script that provides screen utilities
   * @param logger logger used for situational info/warning
   * @return the detected {@link MonsterCandidate} or {@code null} if nothing matches
   */
  private static MonsterCandidate locateMonster(BaseScript script, Logger logger) {
    try {
      BufferedImage gameView = script.controller().zones().getGameView();
      Point clickLocation = PointSelector.getRandomPointInColour(gameView, "Purple", 10, 15.0);
      if (clickLocation != null) {
        script.controller().mouse().moveTo(clickLocation, "fast");
        String monsterName = captureMonsterName(script);
        logger.info("Located purple monster: {}", monsterName);
        return new MonsterCandidate(clickLocation, monsterName);
      } else {
        logger.warn("locateMonster: could not find any purple point to click on");
      }
    } catch (Exception e) {
      logger.error("Error while trying to locate and click monster: {}", e.getMessage(), e);
    }
    return null;
  }

  /**
   * Attempts to locate and click the purple monster object within the game view. It searches for
   * purple contours, then clicks a randomly distributed point inside the contour, retrying up to a
   * maximum number of attempts. Logs failures and stops the script if unable to click successfully.
   *
   * @param script script for controller access
   * @param clickLocation optional existing point to reuse
   * @param logger logger for error/debug output
   * @return {@code true} if the click was performed, {@code false} otherwise
   */
  private static boolean clickMonster(BaseScript script, Point clickLocation, Logger logger) {
    final int retryCycles = 3;
    Point target = clickLocation;

    for (int attempt = 1; attempt <= retryCycles; attempt++) {
      // reuse provided clickLocation before sampling a new one to save time
      if (target == null) {
        try {
          target = PointSelector.getRandomPointInColour(
              script.controller().zones().getGameView(), "Purple", MAX_ATTEMPTS, 15.0);
        } catch (Exception e) {
          logger.error("Failed while generating monster click location: {}", String.valueOf(e));
        }
      }
      // skip to the next iteration if we still have no valid point
      if (target != null) {
        break;
      }
      logger.warn("clickMonster: no valid Purple point found (attempt {}/{})", attempt, retryCycles);
      try {
        // brief pause before retrying to avoid busy looping
        BaseScript.waitMillis((int) Math.min(ACTION_DELAY.toMillis(), Integer.MAX_VALUE));
      } catch (ScriptStoppedException ie) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    if (target == null) {
      logger.warn("clickMonster: could not find any purple point after {} attempts", retryCycles);
      return false;
    }
    try {
      // move the mouse and click the resolved point to trigger combat
      script.controller().mouse().moveTo(target, "fast");
      script.controller().mouse().leftClick();
      String monsterName = captureMonsterName(script);
      logger.info("Clicked on purple monster: {}", monsterName);
    } catch (Exception e) {
      logger.error("Error while clicking monster: {}", e.getMessage(), e);
      return false;
    }
    return true;
  }

  /**
   * Attempts to read the monster name shown when the mouse hovers over the pointer.
   *
   * @param script script providing access to mouse-over utilities
   * @return sanitized dragon name or {@code null} when unavailable
   */
  private static String captureMonsterName(BaseScript script) {
    try {
      return sanitizeMonsterName(MouseOver.getText(script));
    } catch (IOException e) {
      logger.debug("MouseOver name read failed: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Returns {@code true} when the detected name matches the expected one (ignoring case/spacing).
   *
   * @param expected normalized expected name
   * @param detected raw name observed from mouse-over
   */
  private static boolean isExpectedTarget(String expected, String detected) {
    if (detected == null) {
      return false;
    }
    String normalizedDetected = compactName(detected);
    if (normalizedDetected == null) {
      return false;
    }
    if (expected == null) {
      return true;
    }
    String normalizedExpected = compactName(expected);
    if (normalizedExpected == null) {
      return true;
    }
    return normalizedExpected.equalsIgnoreCase(normalizedDetected);
  }

  /**
   * Removes UI noise and validation words from the raw mouse-over text.
   *
   * @param raw raw string read from the mouse-over tooltip
   * @return cleaned monster name or {@code null} when the text does not look like an attack entry
   */
  private static String sanitizeMonsterName(String raw) {
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

  /**
   * Normalizes a name then strips whitespace so comparisons ignore spacing.
   *
   * @param name name to compact
   * @return whitespace-removed normalized name
   */
  private static String compactName(String name) {
    String normalized = normalizeName(name);
    if (normalized == null) {
      return null;
    }
    return normalized.replaceAll("\\s+", "");
  }

  /**
   * Trims and null-checks a name, storing {@code null} for empty inputs.
   *
   * @param name name to normalize
   * @return normalized name or {@code null}
   */
  private static String normalizeName(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Chooses the best display name for logging: primary if present, otherwise the secondary fallback.
   */
  private static String describeTarget(String primary, String secondary) {
    if (primary != null) {
      return primary;
    }
    if (secondary != null && !secondary.isBlank()) {
      return secondary;
    }
    return "unknown NPC";
  }

  /**
   * Adds payload context when the reported NPC differs from the expected name.
   */
  private static String describeDetectionTarget(String expected, String payloadName) {
    if (expected != null
        && payloadName != null
        && !payloadName.isBlank()
        && !expected.equalsIgnoreCase(payloadName)) {
      return expected + " (reported " + payloadName + ")";
    }
    return describeTarget(expected, payloadName);
  }

  private static final class MonsterCandidate {
    private final Point location;
    private final String name;

    private MonsterCandidate(Point location, String name) {
      this.location = location;
      this.name = name;
    }
  }
}
