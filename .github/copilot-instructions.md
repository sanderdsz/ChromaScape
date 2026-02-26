# ChromaScape AI coding instructions

## Big picture architecture
- Spring Boot web host and bot runtime are in one app; entrypoint wires web sockets into core singletons in [src/main/java/com/chromascape/web/ChromaScapeApplication.java](src/main/java/com/chromascape/web/ChromaScapeApplication.java).
- Scripts live in [src/main/java/com/chromascape/scripts](src/main/java/com/chromascape/scripts) and extend `BaseScript`; the `cycle()` loop drives bot logic and uses `controller()` for input/zones (see [src/main/java/com/chromascape/base/BaseScript.java](src/main/java/com/chromascape/base/BaseScript.java)).
- `Controller` is the core runtime façade: initializes remote input (KInput), OCR fonts, zone mapping, and exposes mouse/keyboard/zones/walker utilities (see [src/main/java/com/chromascape/controller/Controller.java](src/main/java/com/chromascape/controller/Controller.java)).
- UI zones are discovered via template matching and masked out for game-view captures in [src/main/java/com/chromascape/utils/domain/zones/ZoneManager.java](src/main/java/com/chromascape/utils/domain/zones/ZoneManager.java).

## Web UI + realtime channels
- Pages are served by Thymeleaf templates in [src/main/resources/templates](src/main/resources/templates) with static assets in [src/main/resources/static](src/main/resources/static) (see [src/main/java/com/chromascape/web/ServePages.java](src/main/java/com/chromascape/web/ServePages.java)).
- Logs stream over WebSocket using a custom Log4j2 appender ([src/main/java/com/chromascape/web/logs/WebSocketLogAppender.java](src/main/java/com/chromascape/web/logs/WebSocketLogAppender.java)) configured in [src/main/resources/log4j2.xml](src/main/resources/log4j2.xml).
- Viewport frames and bot state are bridged from core singletons to WebSocket handlers via [src/main/java/com/chromascape/web/viewport/WebsocketViewport.java](src/main/java/com/chromascape/web/viewport/WebsocketViewport.java) and [src/main/java/com/chromascape/web/state/WebsocketBotStateListener.java](src/main/java/com/chromascape/web/state/WebsocketBotStateListener.java).

## External/native dependencies
- Remote input relies on native KInput DLLs copied to build/dist at build time (see `copyNativeLibraries` in [build.gradle.kts](build.gradle.kts)). DLLs must exist at third_party/KInput/KInput/KInput/bin/Release and third_party/KInput/KInput/KInputCtrl/bin/Release.
- If missing, build KInput/KInputCtrl using the guide in [third_party/DEV_README.md](third_party/DEV_README.md).

## Project-specific conventions
- Scripts should call `BaseScript.checkInterrupted()` inside loops and use `BaseScript.waitMillis()`/`waitRandomMillis()` for delays; stopping is cooperative.
- OCR and colour detection rely on resources in [src/main/resources/fonts](src/main/resources/fonts) and [colours/colours.json](colours/colours.json); ZoneManager expects UI template images in [src/main/resources/images/ui](src/main/resources/images/ui).
- Network telemetry consumers (e.g., localhost event feeds) live in [src/main/java/com/chromascape/utils/net](src/main/java/com/chromascape/utils/net) and are used by scripts like [src/main/java/com/chromascape/scripts/CombatScript.java](src/main/java/com/chromascape/scripts/CombatScript.java).

## Developer workflows (Windows-first)
- Build (runs Spotless + Checkstyle): ./gradlew.bat build (or ./gradlew build on *nix).
- Run web UI/bot host: ./gradlew.bat bootRun.
- Tests: ./gradlew.bat test.
- Cleanup: ./gradlew.bat cleanAll removes build artifacts plus the .chromascape folder.