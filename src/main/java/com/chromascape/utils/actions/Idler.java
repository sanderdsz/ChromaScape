package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.runtime.ScriptStoppedException;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.domain.ocr.Ocr;
import com.chromascape.utils.net.EventConsumer;
import com.chromascape.utils.net.EventPayload;

import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Utility class for handling idle behavior in scripts.
 *
 * <p>This class provides functionality to pause execution for a given amount of time, or until the
 * game client indicates the player has become idle again through a chat message.
 */
public class Idler {

  private static final Logger logger = LogManager.getLogger(Idler.class);
  private static volatile String lastMessage = "";

  private static final ColourObj black =
      new ColourObj("black", new Scalar(0, 0, 0, 0), new Scalar(0, 0, 0, 0));
  private static final ColourObj chatRed =
      new ColourObj("chatRed", new Scalar(177, 229, 239, 0), new Scalar(179, 240, 240, 0));

  /**
   * Waits until either the specified timeout has elapsed or until the client chatbox reports that
   * the player is idle.
   *
   * <p>Specifically, this method monitors the "Latest Message" zone in the chatbox for a red
   * message containing the substring {@code "idle"} or {@code "moving"}, which typically appears
   * when using the Idle Notifier plugin
   *
   * @param base the active {@link BaseScript} instance, usually passed as {@code this}
   * @param timeoutSeconds the maximum number of seconds to remain idle before continuing
   */
  public static void waitUntilIdle(BaseScript base, int timeoutSeconds) {
    // Initial wait to prevent race condition to previous idle message.
    BaseScript.waitMillis(600);
    BaseScript.checkInterrupted();
    try {
      Instant start = Instant.now();
      Instant deadline = start.plus(Duration.ofSeconds(timeoutSeconds));
      while (Instant.now().isBefore(deadline)) {
        // Throttle wait to reduce lag, this is enough.
        BaseScript.waitMillis(300);
        Rectangle latestMessage = base.controller().zones().getChatTabs().get("Latest Message");
        String idleText = Ocr.extractText(latestMessage, "Plain 12", chatRed, true);
        String timeStamp = Ocr.extractText(latestMessage, "Plain 12", black, true);
        if ((idleText.contains("moving") || idleText.contains("idle"))
            && !timeStamp.equals(lastMessage)) {
          lastMessage = timeStamp;
          logger.info("Idle message detected: '{}', timestamp: '{}'", idleText, timeStamp);
          return;
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
  public static boolean waitForContinuousIdle(Duration required, Duration maxWait) {
    Instant start = Instant.now();
    Instant deadline = start.plus(maxWait);
    Instant idleStart = null;
    long lastLoggedSecond = 0;
    while (Instant.now().isBefore(deadline)) {
      BaseScript.checkInterrupted();
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
        logger.debug("Failed to fetch events while waiting for continuous idle: {}", e.getMessage());
      }
      try {
        BaseScript.waitMillis(100);
      } catch (ScriptStoppedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    logger.info("waitForContinuousIdle: timed out waiting for continuous idle of {} seconds", required.getSeconds());
    return false;
  }
}
