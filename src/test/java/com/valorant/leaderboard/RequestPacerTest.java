package com.valorant.leaderboard;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestPacerTest {
 @Test void workersAndRetriesShareOneStartBudgetAndCooldown()throws Exception {
  var clock=new AtomicLong(1000);
  var pacer=new RequestPacer(100,clock::get,clock::addAndGet);
  pacer.acquire();assertEquals(1000,clock.get());
  pacer.acquire();assertEquals(1500,clock.get());
  pacer.defer(60000);pacer.acquire();assertEquals(61500,clock.get());
  pacer.acquire();assertEquals(62000,clock.get());
 }
 @Test void parsesBothRetryAfterFormatsWithoutShorteningTheDelay(){
  var now=Instant.parse("2026-10-20T12:00:00Z");
  assertEquals(60000,RequestPacer.retryAfterMillis("60",now));
  assertEquals(90000,RequestPacer.retryAfterMillis("Tue, 20 Oct 2026 12:01:30 GMT",now));
  assertEquals(0,RequestPacer.retryAfterMillis("invalid",now));
 }
}
