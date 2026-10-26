import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/** Resumable, serial read-only public match capture. Not part of application scheduling. */
class IndiaStudyCapture {
 static final ObjectMapper json=new ObjectMapper();
 static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
 static Path root;static long nextRequest;static int requests;
 static JsonNode fetch(String url,Path file)throws Exception{
  if(Files.exists(file))try(var in=new GZIPInputStream(Files.newInputStream(file))){return json.readTree(in);}
  for(int attempt=0;attempt<3;attempt++){
   long wait=nextRequest-System.currentTimeMillis();if(wait>0)Thread.sleep(wait);
   var req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(35))
    .header("accept","application/json").header("accept-language","en-US,en;q=0.7")
    .header("origin","https://tracker.gg").header("referer","https://tracker.gg/").header("Accept-Encoding","gzip")
    .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36")
    .header("sec-ch-ua","\"Brave\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"")
    .header("sec-ch-ua-mobile","?0").header("sec-ch-ua-platform","\"Windows\"")
    .header("sec-fetch-dest","empty").header("sec-fetch-mode","cors").header("sec-fetch-site","same-site");
   String cookie=System.getenv("LEADERBOARD_PROFILE_COOKIE");if(cookie!=null&&!cookie.isBlank())req.header("Cookie",cookie);
   nextRequest=System.currentTimeMillis()+1000;requests++;var response=http.send(req.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
   int status=response.statusCode();if(status==429||status>=500){
    long backoff=5000L*(attempt+1);String retry=response.headers().firstValue("Retry-After").orElse("");
    try{backoff=Math.max(backoff,Long.parseLong(retry)*1000);}catch(NumberFormatException ignored){try{backoff=Math.max(backoff,Duration.between(Instant.now(),ZonedDateTime.parse(retry,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());}catch(Exception absent){}}
    Thread.sleep(backoff);continue;
   }
   if(status!=200)throw new IOException("HTTP "+status);
   byte[] bytes=response.body();if(response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip"))try(var in=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=in.readAllBytes();}
   JsonNode doc=json.readTree(bytes);if(!doc.hasNonNull("data"))throw new IOException("Missing data");
   Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".partial");
   try(var out=new GZIPOutputStream(Files.newOutputStream(tmp))){out.write(bytes);}Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
   return doc;
  }throw new IOException("Retry limit");
 }
 static void save(String file,Object value)throws IOException{Path target=root.resolve(file),tmp=root.resolve(file+".partial");json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),value);Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
 public static void main(String[] args)throws Exception{
  root=Path.of(args[0]).toAbsolutePath().normalize();if(!root.startsWith(Path.of("data/studies").toAbsolutePath().normalize()))throw new IllegalArgumentException("Study path required");
  var cohort=json.readTree(root.resolve("cohort.json").toFile());
  var accounts=new ArrayList<Map<String,Object>>();var matches=new LinkedHashSet<String>();int index=0;
  for(var player:cohort.path("players")){
   index++;String name=player.path("display_name").asText(),key=player.path("player_key").asText();String encoded=URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20");
   String safe=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))).substring(0,16);
   var account=new LinkedHashMap<String,Object>();account.put("player",name);account.put("key",key);account.put("tier",player.path("tier").asText());account.put("region",player.path("region").asText());
   var ids=new LinkedHashSet<String>();String next=null;var cursors=new HashSet<String>();
   try{
    for(int page=0;page<6&&ids.size()<30;page++){
     String url="https://api.tracker.gg/api/v2/valorant/standard/matches/riot/"+encoded+"?platform=pc&type=competitive"+(next==null?"":"&next="+URLEncoder.encode(next,StandardCharsets.UTF_8));
     JsonNode data=fetch(url,root.resolve("histories/"+safe+"-"+page+".json.gz")).path("data");
     String actual=data.path("requestingPlayerAttributes").path("platformUserIdentifier").asText();if(!actual.equalsIgnoreCase(name))throw new IOException("History identity mismatch");
     int before=ids.size();for(var match:data.path("matches")){
      if(!match.path("metadata").path("modeName").asText().equalsIgnoreCase("Competitive"))continue;
      String id=match.path("attributes").path("id").asText();if(id.matches("[a-fA-F0-9-]{36}"))ids.add(id);if(ids.size()==30)break;
     }
     JsonNode cursor=data.path("metadata").path("next");if(cursor.isMissingNode()||cursor.isNull()||ids.size()==before)break;
     next=cursor.asText();if(!cursors.add(next))break;
    }
   }catch(Exception e){account.put("error",e.getMessage());}
   account.put("matches",new ArrayList<>(ids));account.put("matchCount",ids.size());accounts.add(account);matches.addAll(ids);save("histories-index.json",accounts);
   System.out.println("HISTORY "+index+"/"+cohort.path("players").size()+" "+name+" "+ids.size()+" matches"+(account.containsKey("error")?" "+account.get("error"):""));System.out.flush();
  }
  save("capture-plan.json",Map.of("uniqueMatches",matches,"playerCount",accounts.size(),"generatedAt",Instant.now().toString()));
  int done=0,failed=0;var failures=new LinkedHashMap<String,String>();
  for(String id:matches){
   try{var d=fetch("https://api.tracker.gg/api/v2/valorant/standard/matches/"+id,root.resolve("matches/"+id+".json.gz")).path("data");if(!d.path("attributes").path("id").asText().equals(id))throw new IOException("Match ID mismatch");done++;}
   catch(Exception e){failed++;failures.put(id,e.getMessage());}
   save("capture-status.json",Map.of("complete",done,"failed",failed,"total",matches.size(),"requests",requests,"updatedAt",Instant.now().toString(),"failures",failures));
   if((done+failed)%10==0||done+failed==matches.size()){System.out.println("MATCHES "+done+" complete / "+matches.size()+" total; "+failed+" unavailable");System.out.flush();}
   if(failed>=5&&done==0)throw new IOException("Repeated match retrieval failures; capture stopped");
  }
 }
}
