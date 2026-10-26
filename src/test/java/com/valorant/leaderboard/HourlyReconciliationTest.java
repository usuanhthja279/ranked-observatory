package com.valorant.leaderboard;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HourlyReconciliationTest {
 @Test void reconciliationBecomesDueOnTheIstHourAndSuccessfulMidHourRefreshSatisfiesIt(){
  var zone=ZoneId.of("Asia/Kolkata");var last=OffsetDateTime.parse("2026-10-20T20:26:00+05:30");
  assertFalse(GlobalLeaderboardService.regionalDueAt(last,Instant.parse("2026-10-20T15:10:00Z"),zone));
  assertTrue(GlobalLeaderboardService.regionalDueAt(last,Instant.parse("2026-10-20T15:30:00Z"),zone));
  assertTrue(GlobalLeaderboardService.regionalDueAt(null,Instant.parse("2026-10-20T15:10:00Z"),zone));
 }
}
