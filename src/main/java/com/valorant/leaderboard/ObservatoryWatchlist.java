package com.valorant.leaderboard;

import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account/watchlist")
public class ObservatoryWatchlist {
  private static final Set<String> TYPES=Set.of("PLAYER","COUNTRY","REGION","ACT");
  private static final Set<String> REGIONS=Set.of("ap","br","eu","kr","latam","na");
  private final JdbcTemplate db;
  public ObservatoryWatchlist(JdbcTemplate db){this.db=db;}

  public record WatchItem(String type,String key,String scope,String label){}

  private long accountId(Authentication auth){
    if(auth==null||auth instanceof AnonymousAuthenticationToken||!auth.isAuthenticated())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to use your watchlist.");
    var ids=db.query("select id from observatory_accounts where username=lower(?)",(rs,n)->rs.getLong(1),auth.getName());
    if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only customer accounts can own a watchlist.");
    return ids.getFirst();
  }

  @GetMapping public List<Map<String,Object>> list(Authentication auth){
    long owner=accountId(auth);
    return db.queryForList("select id,entity_type,entity_key,scope_key,display_label,created_at from observatory_watchlist where account_id=? order by created_at desc,id desc",owner);
  }

  @PostMapping public ResponseEntity<Map<String,Object>> save(@RequestBody WatchItem item,Authentication auth){
    long owner=accountId(auth);
    String type=item.type()==null?"":item.type().trim().toUpperCase(Locale.ROOT);
    String key=item.key()==null?"":item.key().trim();
    String scope=item.scope()==null?"all":item.scope().trim().toLowerCase(Locale.ROOT);
    String label=item.label()==null||item.label().isBlank()?key:item.label().trim();
    validate(type,key,scope,label);
    var ids=db.query("select id from observatory_watchlist where account_id=? and entity_type=? and entity_key=? and scope_key=?",(rs,n)->rs.getLong(1),owner,type,key,scope);
    long id;
    if(!ids.isEmpty()){
      id=ids.getFirst();
      db.update("update observatory_watchlist set display_label=? where id=? and account_id=?",label,id,owner);
    }else{
      try{
        db.update("insert into observatory_watchlist(account_id,entity_type,entity_key,scope_key,display_label) values(?,?,?,?,?)",owner,type,key,scope,label);
      }catch(DuplicateKeyException concurrentDuplicate){
        db.update("update observatory_watchlist set display_label=? where account_id=? and entity_type=? and entity_key=? and scope_key=?",label,owner,type,key,scope);
      }
      id=db.queryForObject("select id from observatory_watchlist where account_id=? and entity_type=? and entity_key=? and scope_key=?",Long.class,owner,type,key,scope);
    }
    return ResponseEntity.ok(Map.of("id",id,"type",type,"key",key,"scope",scope,"label",label));
  }

  @DeleteMapping("/{id}") public ResponseEntity<Void> remove(@PathVariable long id,Authentication auth){
    if(id<1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Watchlist item not found.");
    int removed=db.update("delete from observatory_watchlist where id=? and account_id=?",id,accountId(auth));
    if(removed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Watchlist item not found.");
    return ResponseEntity.noContent().build();
  }

  private void validate(String type,String key,String scope,String label){
    if(!TYPES.contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported watchlist type.");
    if(key.isBlank()||key.length()>300||key.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid item identifier (up to 300 characters).");
    if(scope.length()>10||(!scope.equals("all")&&!REGIONS.contains(scope)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported region scope.");
    if(label.isBlank()||label.length()>300||label.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid display label (up to 300 characters).");
    switch(type){
      case "PLAYER" -> {if(!key.contains("#")||scope.equals("all"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Players require a Riot ID and region.");}
      case "COUNTRY" -> {if(!key.equals("unknown")&&!key.matches("[a-z]{2}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Country identifiers must be two-letter codes or unknown.");}
      case "REGION" -> {if(!REGIONS.contains(key)||!scope.equals("all"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported region.");}
      case "ACT" -> {if(!key.matches("[a-fA-F0-9-]{36}")||!scope.equals("all"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Acts require a valid Act identifier.");}
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported watchlist type.");
    }
  }
}
