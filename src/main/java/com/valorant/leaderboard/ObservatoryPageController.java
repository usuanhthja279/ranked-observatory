package com.valorant.leaderboard;

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

  private RedirectView regionRedirect(String region, String act) {
    if (!GlobalLeaderboardService.REGIONS.containsKey(region)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown leaderboard region");
    }
    return redirect("region", region, act);
  }

  private RedirectView redirect(String view, String region, String act) {
    if (act != null && !act.isBlank() && !act.matches("[a-fA-F0-9-]{36}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Act reference");
    }
    var target = UriComponentsBuilder.fromPath("/leaderboard.html")
        .queryParam("view", view);
    if (region != null) target.queryParam("region", region);
    if (act != null && !act.isBlank()) target.queryParam("act", act);
    return new RedirectView(target.build().encode().toUriString(), false);
  }
}
