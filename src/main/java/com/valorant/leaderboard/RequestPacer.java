package com.valorant.leaderboard;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;

/** One start-rate budget shared by every page and retry, independent of worker count. */
final class RequestPacer {
  interface Sleeper { void sleep(long millis) throws InterruptedException; }
  private final long intervalMillis;
  private final LongSupplier clock;
  private final Sleeper sleeper;
  private long nextStart,starts,lastStart,minInterval=Long.MAX_VALUE,liveWaiters;
  RequestPacer(long intervalMillis){this(intervalMillis,System::currentTimeMillis,Thread::sleep);}
  RequestPacer(long intervalMillis,LongSupplier clock,Sleeper sleeper){this.intervalMillis=Math.max(500,intervalMillis);this.clock=clock;this.sleeper=sleeper;}
  void acquire() throws InterruptedException { acquireLive(); }
  void acquireLive() throws InterruptedException {
    synchronized(this){liveWaiters++;}
    try{acquireSlot(false);}finally{synchronized(this){liveWaiters--;notifyAll();}}
  }
  void acquireBackground() throws InterruptedException { acquireSlot(true); }
  private void acquireSlot(boolean background) throws InterruptedException {
    for(;;){
      long wait;
      synchronized(this){long now=clock.getAsLong();wait=nextStart-now;if(background&&liveWaiters>0)wait=Math.max(wait,50);if(wait<=0){if(starts>0)minInterval=Math.min(minInterval,now-lastStart);lastStart=now;starts++;nextStart=now+intervalMillis;return;}}
      sleeper.sleep(wait);
    }
  }
  synchronized java.util.Map<String,Object> metrics(){return java.util.Map.of("requestsStarted",starts,"minimumObservedStartIntervalMillis",starts<2?0:minInterval,"configuredIntervalMillis",intervalMillis);}
  synchronized void defer(long millis){nextStart=Math.max(nextStart,clock.getAsLong()+Math.max(0,millis));}
  static long retryAfterMillis(String value,Instant now){
    try{return Math.max(0,Math.multiplyExact(Long.parseLong(value.trim()),1000));}
    catch(Exception ignored){try{return Math.max(0,Duration.between(now,ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());}catch(Exception invalid){return 0;}}
  }
}
