package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LiveCaptureTest {
 @TempDir Path temporary;
 @Test void resumesOnlyItsOwnWindowWithoutRefetchingAndNewCollectionsStartEmpty()throws Exception {
  var mapper=new ObjectMapper();var first=new LiveCapture(temporary.toString(),"","act",mapper);
  var page=new TrackerLeaderboardClient.Page(List.of(new TrackerLeaderboardClient.Player("player#tag",null,1,900,"Radiant",3,"ap")),OffsetDateTime.parse("2026-10-20T14:00:00Z"));
  assertEquals(page,first.page("global",1,()->page));
  var resumed=new LiveCapture(temporary.toString(),first.directory(),"act",mapper);
  assertEquals(page,resumed.page("global",1,()->{throw new AssertionError("Must not fetch a checkpointed page");}));assertEquals(1,resumed.reusedPages());assertEquals(first.started(),resumed.started());
  var next=new LiveCapture(temporary.toString(),"","act",mapper);assertNotEquals(first.directory(),next.directory());
  var calls=new java.util.concurrent.atomic.AtomicInteger();next.page("global",1,()->{calls.incrementAndGet();return page;});assertEquals(1,calls.get());
  assertThrows(IllegalStateException.class,()->new LiveCapture(temporary.toString(),first.directory(),"other-act",mapper));
 }
}
