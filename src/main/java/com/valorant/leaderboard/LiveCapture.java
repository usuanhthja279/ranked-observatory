package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Checkpoints belong to one live collection window; successful runs never seed later runs. */
final class LiveCapture {
  interface Loader { TrackerLeaderboardClient.Page load() throws Exception; }
  private final Path directory;
  private final String act;
  private final ObjectMapper mapper;
  private final OffsetDateTime started;
  private final AtomicLong reused=new AtomicLong();
  LiveCapture(String base,String resume,String act,ObjectMapper mapper)throws Exception {
    this.act=act;this.mapper=mapper.copy().findAndRegisterModules();
    Path root=Path.of(base).toAbsolutePath().normalize();Files.createDirectories(root);
    directory=resume.isBlank()?root.resolve(UUID.randomUUID().toString()):Path.of(resume).toAbsolutePath().normalize();
    if(!directory.startsWith(root)||directory.equals(root))throw new IllegalArgumentException("Resume capture must be inside live cache directory");
    Files.createDirectories(directory);Path metadata=directory.resolve("capture.json");
    if(Files.exists(metadata)){
      var data=this.mapper.readTree(metadata.toFile());if(!act.equals(data.path("act").asText()))throw new IllegalStateException("Capture act mismatch");
      started=OffsetDateTime.parse(data.path("startedAt").asText());
      if(started.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)))throw new IllegalStateException("Resume capture older than two hours; start a new collection");
    }else{
      if(!resume.isBlank())throw new IllegalStateException("Resume metadata missing");
      started=OffsetDateTime.now(ZoneOffset.UTC);this.mapper.writeValue(metadata.toFile(),Map.of("act",act,"startedAt",started.toString()));
    }
  }
  TrackerLeaderboardClient.Page page(String source,int number,Loader loader)throws Exception {
    if((!TrackerLeaderboardClient.REGIONS.contains(source)&&!source.equals("global"))||number<1)throw new IllegalArgumentException("Invalid capture page");
    Path file=directory.resolve(source+"-"+number+".json");
    if(Files.exists(file)){
      var data=mapper.readTree(file.toFile());
      if(!act.equals(data.path("act").asText())||!source.equals(data.path("source").asText())||number!=data.path("page").asInt())throw new IllegalStateException("Capture page metadata mismatch");
      var result=mapper.treeToValue(data.get("response"),TrackerLeaderboardClient.Page.class);reused.incrementAndGet();return result;
    }
    var result=loader.load();
    Path temporary=file.resolveSibling(file.getFileName()+".tmp");
    mapper.writeValue(temporary.toFile(),Map.of("act",act,"source",source,"page",number,"response",result));
    Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
    return result;
  }
  OffsetDateTime started(){return started;}
  String directory(){return directory.toString();}
  long reusedPages(){return reused.get();}
}
