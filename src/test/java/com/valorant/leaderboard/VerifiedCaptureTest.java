package com.valorant.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedCaptureTest {
  @TempDir Path directory;
  private final ObjectMapper mapper=new ObjectMapper();
  private void prepare()throws Exception {
    var row=new LinkedHashMap<String,Object>(Map.of("player","Public#id","region","ap","rank",1,"rr",500,"tier","Radiant","wins",5,"sourceUpdated","2026-10-20T12:00:00Z"));row.put("country",null);
    byte[] bytes=mapper.writeValueAsBytes(Map.of("region","global","page",1,"checkedAt","2026-10-20T12:01:00Z","rows",List.of(row)));
    Files.write(directory.resolve("global-1.json"),bytes);
    var manifest=Map.of("act","test-act","observedFrom","2026-10-20T12:01:00Z","observedThrough","2026-10-20T12:02:00Z","regions",List.of("ap","kr","eu","na","latam","br"),"files",Map.of("global-1.json",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
    Files.writeString(directory.resolve("manifest.json"),mapper.writeValueAsString(manifest));
  }
  @Test void readsVerifiedSourceValuesIncludingUnknownCountry()throws Exception {
    prepare();var capture=new VerifiedCapture(directory.toString(),"test-act",mapper);var page=capture.page("global",1);
    assertEquals("ap",page.players().getFirst().region());assertNull(page.players().getFirst().country());assertEquals(500,page.players().getFirst().rr());
    assertEquals("2026-10-20T12:00Z",page.updatedAt().toString());
  }
  @Test void rejectsActMismatchMissingPagesAndTampering()throws Exception {
    prepare();assertThrows(IllegalStateException.class,()->new VerifiedCapture(directory.toString(),"other-act",mapper));
    var capture=new VerifiedCapture(directory.toString(),"test-act",mapper);
    assertThrows(IllegalStateException.class,()->capture.page("global",2));
    Files.writeString(directory.resolve("global-1.json"),"{}");assertThrows(IllegalStateException.class,()->capture.page("global",1));
  }
}
