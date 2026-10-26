package com.valorant.leaderboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoricalTrendsQueryServiceTest {
  @Test void acceptsAllThirtySixActsInTheInclusiveCatalogWindow(){
    assertDoesNotThrow(()->HistoricalTrendsQueryService.validateRangeSize(0,35));
  }

  @Test void rejectsAWindowLargerThanThirtySixActs(){
    assertThrows(IllegalArgumentException.class,()->HistoricalTrendsQueryService.validateRangeSize(0,36));
  }
}
