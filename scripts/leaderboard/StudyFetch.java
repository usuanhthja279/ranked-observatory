import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.io.*;
import java.util.zip.GZIPInputStream;

/** Read-only public Tracker data capture for the India study. No application state mutations. */
class StudyFetch {
  public static void main(String[] args)throws Exception {
    URI uri=URI.create(args[0]);
    if(!uri.getScheme().equals("https")||!uri.getHost().equals("api.tracker.gg"))throw new IllegalArgumentException("Tracker API only");
    var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
      .header("accept","application/json").header("accept-language","en-US,en;q=0.7")
      .header("origin","https://tracker.gg").header("referer","https://tracker.gg/")
      .header("Accept-Encoding","gzip")
      .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36")
      .header("sec-ch-ua","\"Brave\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"")
      .header("sec-ch-ua-mobile","?0").header("sec-ch-ua-platform","\"Windows\"")
      .header("sec-fetch-dest","empty").header("sec-fetch-mode","cors").header("sec-fetch-site","same-site");
    var cookie=System.getenv("LEADERBOARD_PROFILE_COOKIE");if(cookie!=null&&!cookie.isBlank())builder.header("Cookie",cookie);
    var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build().send(builder.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    System.out.println("HTTP "+response.statusCode());if(response.statusCode()!=200)return;
    byte[] body=response.body();if(response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip"))try(var in=new GZIPInputStream(new ByteArrayInputStream(body))){body=in.readAllBytes();}
    Path out=Path.of(args[1]).toAbsolutePath().normalize();Path root=Path.of("data/studies").toAbsolutePath().normalize();
    if(!out.startsWith(root))throw new IllegalArgumentException("Study output directory required");Files.createDirectories(out.getParent());Files.write(out,body);System.out.println("Saved "+body.length+" bytes");
  }
}
