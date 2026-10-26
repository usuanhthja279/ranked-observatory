package com.valorant.leaderboard;

import java.time.OffsetDateTime;
import java.util.*;

/** First-source observations with explicit uncertainty; never fabricate reconciled statistics. */
final class GlobalObservations {
  static final class Row {
    final TrackerLeaderboardClient.Player player;
    final OffsetDateTime updated;
    Integer globalRank,regionalRank;
    boolean rankAmbiguous,statsAmbiguous,carriedForward;
    String observationSource;
    OffsetDateTime regionalRankUpdated;
    Row(TrackerLeaderboardClient.Player player,OffsetDateTime updated,String source) {
      this.player=player;this.updated=updated;this.observationSource=source;
      if(source.equals("global"))globalRank=player.rank();else {regionalRank=player.rank();regionalRankUpdated=updated;}
    }
    String key() { return player.key().toLowerCase(Locale.ROOT); }
  }
  private final Map<String,Row> rows=new LinkedHashMap<>();
  int rawRows,duplicates;
  void add(TrackerLeaderboardClient.Page page,String source) {
    int added=0;
    for(var player:page.players()) {
      rawRows++;
      String key=player.region()+"|"+player.key().toLowerCase(Locale.ROOT);
      var old=rows.get(key);
      if(old==null) {rows.put(key,new Row(player,page.updatedAt(),source));added++;continue;}
      duplicates++;
      if(old.player.rr()!=player.rr() || old.player.wins()!=player.wins() || !old.player.tier().equals(player.tier())
          || !Objects.equals(old.player.country(),player.country()))old.statsAmbiguous=true;
      Integer previous=source.equals("global")?old.globalRank:old.regionalRank;
      if(previous!=null && previous!=player.rank())old.rankAmbiguous=true;
      if(source.equals("global") && previous==null)old.globalRank=player.rank();
      if(!source.equals("global") && previous==null){old.regionalRank=player.rank();old.regionalRankUpdated=page.updatedAt();}
    }
    // Cross-source overlaps are expected; repeated pages within a source are checked by the collector.
  }
  void supplement(Collection<Row> cached){
    for(var saved:cached){
      String key=saved.player.region()+"|"+saved.key();var current=rows.get(key);
      if(current!=null){if(current.regionalRank==null){current.regionalRank=saved.regionalRank;current.regionalRankUpdated=saved.regionalRankUpdated;}continue;}
      var row=new Row(saved.player,saved.updated,saved.player.region());row.carriedForward=true;row.observationSource=saved.observationSource;
      row.statsAmbiguous=saved.statsAmbiguous;row.rankAmbiguous=saved.rankAmbiguous;
      row.regionalRankUpdated=saved.regionalRankUpdated;rows.put(key,row);
    }
  }
  int carried(){return (int)rows.values().stream().filter(r->r.carriedForward).count();}
  Collection<Row> rows(){return rows.values();}
  int size(){return rows.size();}
  int uncertain(){return (int)rows.values().stream().filter(r->r.statsAmbiguous||r.rankAmbiguous).count();}
  Map<String,Long> regionCounts(){var result=new TreeMap<String,Long>();for(var row:rows.values())result.merge(row.player.region(),1L,Long::sum);return result;}
}
