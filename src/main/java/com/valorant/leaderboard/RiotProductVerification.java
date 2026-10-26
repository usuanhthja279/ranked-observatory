package com.valorant.leaderboard;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Riot app verification also supports the submitted dashboard-page URL. */
@RestController
public class RiotProductVerification {
  @GetMapping(value = {"/riot.txt", "/leaderboard.html/riot.txt"}, produces = MediaType.TEXT_PLAIN_VALUE)
  public Resource verification() {
    return new ClassPathResource("static/riot.txt");
  }
}
