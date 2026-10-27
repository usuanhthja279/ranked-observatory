package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TrackerProfilesSingleFlightTest {
  @Test void concurrentRequestsForSamePlayerShareOneBoundedProviderCall() throws Exception {
    var client=mock(TrackerLeaderboardClient.class);
    var started=new CountDownLatch(1);var release=new CountDownLatch(1);
    var mapper=new ObjectMapper();
    when(client.fetchProfile(anyString(),anyString())).thenAnswer(call->{
      started.countDown();
      if(!release.await(5,TimeUnit.SECONDS))throw new TimeoutException("test did not release provider request");
      return mapper.readTree("{\"data\":{\"platformInfo\":{\"platformUserIdentifier\":\"Player#Tag\"},\"metadata\":{\"defaultPlatform\":\"pc\",\"activeShard\":\"ap\"},\"userInfo\":{\"countryCode\":\"IN\"},\"segments\":[]}}");
    });
    var profiles=new TrackerProfiles(client,"d816f426-48ea-f052-117f-9697a155b319");
    var pool=Executors.newFixedThreadPool(2);
    try{
      var first=pool.submit(()->profiles.get("Player#Tag"));
      assertTrue(started.await(2,TimeUnit.SECONDS));
      var second=pool.submit(()->profiles.get("Player#Tag"));
      release.countDown();
      assertTrue(Boolean.TRUE.equals(first.get(5,TimeUnit.SECONDS).get("available")));
      assertTrue(Boolean.TRUE.equals(second.get(5,TimeUnit.SECONDS).get("available")));
      verify(client,times(1)).fetchProfile("Player#Tag","d816f426-48ea-f052-117f-9697a155b319");
    }finally{release.countDown();pool.shutdownNow();}
  }
}
