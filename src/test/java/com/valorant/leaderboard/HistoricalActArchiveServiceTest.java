package com.valorant.leaderboard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalActArchiveServiceTest {
  @Test void emptyRecentArchiveWindowHasNoElapsedRateYet(){
    assertEquals(0,HistoricalActArchiveService.elapsedSeconds(null));
  }

  @Test void elapsedRateWindowUsesAtLeastOneMinute(){
    assertEquals(60,HistoricalActArchiveService.elapsedSeconds(12.5));
    assertEquals(90,HistoricalActArchiveService.elapsedSeconds(90));
  }
}
