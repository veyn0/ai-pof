# Edge-Cases & Fixes

Diese Übersicht sammelt bekannte Edge-Cases im Runden-Lifecycle und verweist auf die eingeführten Fixes/Guards.

## Runde starten / Round.startRound
- **Kein aktiver Round-ID-Eintrag** → Guard + Log (`round-start aborted reason=missing-round-id`). Fix: `RoundLifecycleHandler.startRound`.  
- **Keine Teilnehmer beim Start** → Guard + Log (`round-start aborted reason=no-participants`). Fix: `RoundLifecycleHandler.startRound`.  
- **Welt nicht vorhanden** → Guard + Log (`round-start aborted reason=missing-world`). Fix: `RoundLifecycleHandler.startRound`.  
- **Start-Teleports ohne Zielpunkte** → Log (`round-start teleport-none`). Fix: `RoundLifecycleHandler.startRound`.

## Runden-Neustart / RoundManager.restartRound
- **Ungültige oder leere Base-ID** → Guard + Log (`round-restart aborted reason=missing-base-id`). Fix: `RoundLifecycleHandler.restartRound`.  
- **Keine wartenden Teilnehmer** → Log + Cleanup (`round-restart skipped reason=no-pending`). Fix: `RoundLifecycleHandler.restartRound`.  
- **Spieler offline bei Neustart** → Log (`round-restart removed-offline`). Fix: `RoundLifecycleHandler.restartRound`.

## Player-Move / RoundManager.onPlayerMove
- **Spieler bewegt sich, ist aber kein Teilnehmer** → Guard + Log (`player-move ignored reason=not-participant`). Fix: `RoundService.onPlayerMove`.  
- **Void-Fall in Rundenwelt** → Log (`player-move void-fall`) + Health=0. Fix: `RoundService.onPlayerMove`.

## World-Erzeugung & Cleanup
- **World-Erzeugung schlägt fehl** → Error-Log (`world-create failed`) + Exception. Fix: `WorldService.createWorld`.  
- **I/O Fehler beim Löschen von Weltordnern** → Warning/Severe-Logs (`world-cleanup delete-failed`/`scan-failed`). Fix: `WorldService.deleteWorldFolder`.

## Teleports (Hotspots)
- **Teleport ohne Spawn-Location** → Warning-Logs (`teleport skipped reason=missing-spawn|missing-spectator-spawn`). Fix: `RoundLifecycleHandler.buildWaitingBoxes`, `RoundLifecycleHandler.endRound`.  
- **Teleport beim Join/Leave** → strukturiertes Log mit Ergebnis (`teleport result=... reason=join-waiting-box|leave-world`). Fix: `RoundService.addPlayer`, `RoundService.removePlayer`.  
- **Teleport im Cleanup/Spectator** → strukturiertes Log (`teleport result=... reason=cleanup|spectator`). Fix: `RoundLifecycleHandler.endRound`.
