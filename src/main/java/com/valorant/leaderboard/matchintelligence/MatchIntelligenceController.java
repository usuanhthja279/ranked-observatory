package com.valorant.leaderboard.matchintelligence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.concurrent.Semaphore;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/match-intelligence")
public class MatchIntelligenceController {
  private final MatchIntelligenceStore store;
  private final DeterministicMatchAnalyzer analyzer;
  private final Semaphore importPermit = new Semaphore(1);
  public MatchIntelligenceController(MatchIntelligenceStore store, DeterministicMatchAnalyzer analyzer) {
    this.store = store;
    this.analyzer = analyzer;
  }

  @PostMapping(value="/imports", consumes=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MatchIntelligenceStore.ImportReceipt> importMatches(
      Authentication authentication, HttpServletRequest request) {
    if (!importPermit.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A match import is already being processed. Try again shortly.");
    try {
    int max = MatchExportParser.MAX_EXPORT_BYTES;
    if (request.getContentLengthLong() > max) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Match export exceeds the configured size limit.");
    final byte[] payload;
    try { payload = request.getInputStream().readNBytes(max + 1); }
    catch (IOException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the match export."); }
    if (payload.length > max) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Match export exceeds the configured size limit.");
    final String decoded;
    try { decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString(); }
    catch (CharacterCodingException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match export must use UTF-8 encoding."); }
    // Decode before passing bytes to storage so malformed sequences cannot be persisted as replacement characters.
    return ResponseEntity.status(HttpStatus.CREATED).body(store.importExport(authentication.getName(), decoded.getBytes(StandardCharsets.UTF_8)));
    } finally { importPermit.release(); }
  }

  @GetMapping("/imports/{jobId}")
  public MatchIntelligenceStore.JobView getImport(Authentication authentication, @PathVariable String jobId) {
    if (jobId == null || !jobId.matches("[0-9a-fA-F-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import was not found for this account.");
    return store.getJob(authentication.getName(), jobId);
  }

  @DeleteMapping("/imports/{jobId}/raw")
  public ResponseEntity<Void> deleteRawImport(Authentication authentication, @PathVariable String jobId) {
    if (jobId == null || !jobId.matches("[0-9a-fA-F-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import was not found for this account.");
    store.deleteRawImport(authentication.getName(), jobId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/imports/{jobId}")
  public ResponseEntity<Void> deleteImport(Authentication authentication, @PathVariable String jobId) {
    if (jobId == null || !jobId.matches("[0-9a-fA-F-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import was not found for this account.");
    store.deleteImport(authentication.getName(), jobId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/players/{playerId}/matches")
  public MatchIntelligenceStore.MatchPage playerMatches(Authentication authentication,
      @PathVariable String playerId, @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size) {
    if (playerId == null || playerId.isBlank() || playerId.length() > 300) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid player identity.");
    return store.listMatches(authentication.getName(), playerId, page, size);
  }

  @GetMapping("/matches")
  public MatchIntelligenceStore.MatchPage matches(Authentication authentication,
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
    return store.listMatches(authentication.getName(), null, page, size);
  }

  @GetMapping("/matches/{matchId}")
  public MatchIntelligenceStore.MatchView match(Authentication authentication, @PathVariable String matchId) {
    return store.getMatch(authentication.getName(), matchId);
  }

  @GetMapping("/matches/{matchId}/rounds")
  public java.util.List<CanonicalMatch.Round> rounds(Authentication authentication, @PathVariable String matchId) {
    return store.getRounds(authentication.getName(), matchId);
  }

  @GetMapping("/matches/{matchId}/analysis")
  public DeterministicMatchAnalyzer.Report analysis(Authentication authentication, @PathVariable String matchId) {
    // Reuse the owner-scoped lookup so the analyzer cannot expose another account's match.
    return analyzer.analyze(store.getMatch(authentication.getName(), matchId).match());
  }

  @GetMapping("/matches/{matchId}/rounds/{roundNumber}")
  public MatchIntelligenceStore.RoundView round(Authentication authentication,
      @PathVariable String matchId, @PathVariable int roundNumber) {
    return store.getRound(authentication.getName(), matchId, roundNumber);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String,String>> invalidRequest(IllegalArgumentException error) {
    return ResponseEntity.badRequest().body(Map.of("message", error.getMessage() == null ? "Invalid match intelligence request." : error.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String,String>> requestError(ResponseStatusException error) {
    String message = error.getReason() == null ? "Match intelligence request could not be completed." : error.getReason();
    return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", message));
  }
}
