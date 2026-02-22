package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.runtime.ScriptStoppedException;
import com.chromascape.utils.net.CombatConsumer;
import com.chromascape.utils.net.CombatPayload;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.Logger;

/** Utility that ensures the bot has entered and exited combat before continuing. */
public final class HandleCombat {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(1);

  private HandleCombat() {}

  public static boolean monitorCombat(Logger logger) {
    Instant start = Instant.now();
    boolean seenCombat = false;
    boolean lastInCombat = false;
    Instant nextCheck = start;
    logger.debug("monitorCombat: checking combat status...");
    while (Duration.between(start, Instant.now()).compareTo(TIMEOUT) < 0) {
      logger.debug("monitorCombat: waiting for combat... (seenCombat={}, lastInCombat={})", seenCombat, lastInCombat);
      BaseScript.checkInterrupted();
      Instant now = Instant.now();
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
      try {
        CombatPayload payload = CombatConsumer.fetchCombat();
        if (payload == null) {
          logger.debug("monitorCombat: no combat payload, retrying...");
        } else {
          lastInCombat = payload.isInCombat();
          if (lastInCombat) {
            if (!seenCombat) {
              logger.info("monitorCombat: combat detected against {}", payload.getNpcName());
            }
            seenCombat = true;
          } else if (seenCombat) {
            logger.info("monitorCombat: combat finished, player idle");
            return false;
          }
        }
      } catch (Exception e) {
        logger.debug("monitorCombat: failed to fetch combat payload: {}", e.getMessage());
      }
    }
    if (!seenCombat) {
      logger.warn("monitorCombat: timed out without seeing combat");
    } else {
      logger.warn("monitorCombat: timed out waiting for combat to finish");
    }
    return lastInCombat;
  }
}
