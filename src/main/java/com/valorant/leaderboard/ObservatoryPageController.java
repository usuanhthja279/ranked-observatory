package com.valorant.leaderboard;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

/** Read-only canonical page aliases; the legacy query renderer remains the compatibility implementation. */
@Controller
public class ObservatoryPageController {
  @GetMapping("/overview")
  public RedirectView overview(@RequestParam(required = false) String act) {
    return redirect("world", null, act);
  }

  @GetMapping("/regions")
  public RedirectView regions(@RequestParam(required = false) String region,
                              @RequestParam(required = false) String act) {
    if (region == null || region.isBlank() || region.equals("all")) return redirect("world", null, act);
    return regionRedirect(region, act);
  }

  @GetMapping("/regions/{region}")
  public RedirectView region(@PathVariable String region,
                             @RequestParam(required = false) String act) {
    return regionRedirect(region, act);
  }

  @GetMapping("/countries")
  public RedirectView countries(@RequestParam(required = false) String region,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String act) {
    if (region != null && !region.equals("all") && !GlobalLeaderboardService.REGIONS.containsKey(region)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown leaderboard region");
    }
    validateAct(act);
    var target = UriComponentsBuilder.fromPath("/leaderboard.html").queryParam("view", "countries");
    if (region != null && !region.isBlank()) target.queryParam("region", region);
    if (q != null && !q.isBlank()) target.queryParam("q", q);
    if (act != null && !act.isBlank()) target.queryParam("act", act);
    return new RedirectView(target.build().encode().toUriString(), false);
  }

  @GetMapping("/countries/{country}")
  public RedirectView country(@PathVariable String country,
                              @RequestParam String region,
                              @RequestParam(required = false) String act) {
    if (!country.matches("[a-z]{2}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown country code");
    if (!GlobalLeaderboardService.REGIONS.containsKey(region)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown leaderboard region");
    validateAct(act);
    return redirect("country", region, country, act);
  }

  @GetMapping("/leaderboard")
  public RedirectView globalLeaderboard(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) String act,
                                       @RequestParam(defaultValue = "globalRank") String sort,
                                       @RequestParam(defaultValue = "asc") String direction) {
    validateAct(act);
    if (page != null && page < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leaderboard page");
    if (q != null && q.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search is too long");
    if (!Set.of("globalRank","regionalRank","name","country","tier","rr","rrChange","updated").contains(sort)
        || !Set.of("asc","desc").contains(direction)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leaderboard sort");
    var target = UriComponentsBuilder.fromPath("/leaderboard.html")
        .queryParam("view", "global").queryParam("region", "all").queryParam("country", "all");
    if (q != null && !q.isBlank()) target.queryParam("q", q);
    if (page != null && page > 1) target.queryParam("page", page);
    if (act != null && !act.isBlank()) target.queryParam("act", act);
    if (!sort.equals("globalRank")) target.queryParam("sort", sort);
    if (!direction.equals("asc")) target.queryParam("direction", direction);
    return new RedirectView(target.build().encode().toUriString(), false);
  }

  private RedirectView regionRedirect(String region, String act) {
    if (!GlobalLeaderboardService.REGIONS.containsKey(region)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown leaderboard region");
    }
    return redirect("region", region, act);
  }

  private RedirectView redirect(String view, String region, String act) {
    validateAct(act);
    var target = UriComponentsBuilder.fromPath("/leaderboard.html")
        .queryParam("view", view);
    if (region != null) target.queryParam("region", region);
    if (act != null && !act.isBlank()) target.queryParam("act", act);
    return new RedirectView(target.build().encode().toUriString(), false);
  }

  private RedirectView redirect(String view, String region, String country, String act) {
    validateAct(act);
    var target = UriComponentsBuilder.fromPath("/leaderboard.html")
        .queryParam("view", view).queryParam("region", region).queryParam("country", country);
    if (act != null && !act.isBlank()) target.queryParam("act", act);
    return new RedirectView(target.build().encode().toUriString(), false);
  }

  private void validateAct(String act) {
    if (act != null && !act.isBlank() && !act.matches("[a-fA-F0-9-]{36}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Act reference");
    }
  }
}
