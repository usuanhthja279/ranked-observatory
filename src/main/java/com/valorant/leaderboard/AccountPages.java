package com.valorant.leaderboard;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AccountPages {
 @GetMapping("/login") public String login(){return "redirect:/signin.html";}
}
