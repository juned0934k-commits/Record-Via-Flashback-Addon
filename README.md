# Record Via Flashback (RVF)

By **VALLENN** and **JuneFizz**.

A separate Fabric mod (Minecraft 1.21.11) that adds these features to the Flashback replay mod.
It contains no Flashback code and does not include the Flashback jar.

Features (all recorded into the replay and shown while spectating the recorded player):
- Inventory / container screens with a ghost cursor (standard arrow, dark background, armor slot outlines)
- Camera perspective (F5), with an "Auto Perspective" option
- Potion effects
- Spyglass scope overlay and zoom
- What the player was typing in chat (login/register passwords are hidden)
- Totem of Undying pop animation for the spectated player

In the replay editor, the Visuals window has a "Record Via Flashback" section with checkboxes for what to show.
Recording switches are in `config/rvf.json` (created on first start; edit and restart the game).

## Install
Put the official Flashback jar, Fabric API and this mod's jar in the mods folder.

## Build
1. `powershell -ExecutionPolicy Bypass -File .\setup.ps1` (once; creates libs\flashback-slim.jar and the Gradle wrapper)
2. `.\gradlew.bat build` -> jar in build\libs (use the one without -sources)

`libs/` is git-ignored on purpose: Flashback's license forbids redistributing its files.
