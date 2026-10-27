package com.valorant.leaderboard;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account/preferences")
public class ObservatoryPreferences {
  private final JdbcTemplate db;
  public ObservatoryPreferences(JdbcTemplate db){this.db=db;}
  public record Preferences(Boolean compactTables,Boolean reduceMotion){}
  private long owner(Authentication auth){
    if(auth==null||auth instanceof AnonymousAuthenticationToken||!auth.isAuthenticated())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to manage your preferences.");
    var ids=db.query("select id from observatory_accounts where username=lower(?)",(rs,n)->rs.getLong(1),auth.getName());
    if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Preferences belong to customer accounts.");
    return ids.getFirst();
  }
  @GetMapping public Map<String,Object> get(Authentication auth){
    long owner=owner(auth);
    var rows=db.queryForList("select compact_tables,reduce_motion,updated_at from observatory_ui_preferences where account_id=?",owner);
    if(rows.isEmpty())return Map.of("compactTables",false,"reduceMotion",false);
    var row=rows.getFirst();return Map.of("compactTables",row.get("compact_tables"),"reduceMotion",row.get("reduce_motion"),"updatedAt",row.get("updated_at"));
  }
  @PutMapping public Map<String,Object> save(@RequestBody Preferences value,Authentication auth){
    long owner=owner(auth);
    if(value.compactTables()==null||value.reduceMotion()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose both display preferences.");
    var existing=db.query("select account_id from observatory_ui_preferences where account_id=?",(rs,n)->rs.getLong(1),owner);
    if(existing.isEmpty()){
      try{db.update("insert into observatory_ui_preferences(account_id,compact_tables,reduce_motion) values(?,?,?)",owner,value.compactTables(),value.reduceMotion());}
      catch(org.springframework.dao.DuplicateKeyException raced){db.update("update observatory_ui_preferences set compact_tables=?,reduce_motion=?,updated_at=current_timestamp where account_id=?",value.compactTables(),value.reduceMotion(),owner);}
    }else db.update("update observatory_ui_preferences set compact_tables=?,reduce_motion=?,updated_at=current_timestamp where account_id=?",value.compactTables(),value.reduceMotion(),owner);
    return Map.of("compactTables",value.compactTables(),"reduceMotion",value.reduceMotion());
  }
}
