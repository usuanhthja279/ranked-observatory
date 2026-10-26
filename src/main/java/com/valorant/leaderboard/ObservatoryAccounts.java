package com.valorant.leaderboard;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account")
public class ObservatoryAccounts {
 private final JdbcTemplate db;private final PasswordEncoder passwords;private final String admin;
 public ObservatoryAccounts(JdbcTemplate db,PasswordEncoder passwords,@Value("${spring.security.user.name:admin}")String admin){this.db=db;this.passwords=passwords;this.admin=admin;}
 public record Registration(String username,String name,String email,String password){}
 @PostMapping("/register") public ResponseEntity<Map<String,Object>> register(@RequestBody Registration r){
  String username=r.username()==null?"":r.username().trim().toLowerCase(Locale.ROOT),name=r.name()==null?"":r.name().trim(),email=r.email()==null?"":r.email().trim().toLowerCase(Locale.ROOT);
  if(!username.matches("[a-z0-9_]{3,30}")||username.equalsIgnoreCase(admin))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a username of 3–30 letters, numbers or underscores.");
  if(name.isBlank()||name.length()>120||name.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter your name (up to 120 characters).");
  if(email.length()>320||!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
  if(r.password()==null||r.password().length()<8||r.password().getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a password of at least 8 characters and at most 72 UTF-8 bytes.");
  try{db.update("insert into observatory_accounts(username,display_name,email,password_hash) values(?,?,?,?)",username,name,email,passwords.encode(r.password()));}
  catch(DuplicateKeyException duplicate){throw new ResponseStatusException(HttpStatus.CONFLICT,"That username or email is already registered.");}
  return ResponseEntity.status(201).body(Map.of("created",true,"username",username));
 }
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> accountError(ResponseStatusException error){return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",Objects.toString(error.getReason(),"Check your account details.")));}
 @GetMapping("/me") public Map<String,Object> me(Authentication auth){
  if(auth==null||auth instanceof AnonymousAuthenticationToken||!auth.isAuthenticated())return Map.of("authenticated",false);
  var names=db.queryForList("select display_name from observatory_accounts where username=?",String.class,auth.getName());
  return Map.of("authenticated",true,"username",auth.getName(),"name",names.isEmpty()?auth.getName():names.getFirst(),"administrator",auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")));
 }
}
