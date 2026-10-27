package com.valorant.leaderboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.valorant.leaderboard.matchintelligence.MatchIntelligenceFixtures;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:standalone;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.hikari.schema=PUBLIC", "spring.flyway.enabled=false",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/migration/V1__ranked_leaderboard_history.sql,classpath:db/migration/V2__duplicate_observation_diagnostics.sql,classpath:db/migration/V3__global_leaderboard_snapshots.sql,classpath:db/migration/V4__hourly_regional_observation_cache.sql,classpath:db/migration/V5__observatory_accounts.sql,classpath:db/migration/V8__observatory_watchlist.sql,classpath:db/migration/V9__observatory_ui_preferences.sql,classpath:db/test-publication-revision.sql,classpath:db/test-match-intelligence.sql",
    "spring.security.user.password=test-admin-only", "leaderboard.enabled=false"
})
@AutoConfigureMockMvc
class StandaloneApplicationTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  @Test void bootsWithOnlyLeaderboardComponentsAndOwnSessionCookie() {
    assertFalse(java.util.Arrays.stream(context.getBeanDefinitionNames())
        .map(context::getType).filter(java.util.Objects::nonNull)
        .anyMatch(type -> type.getName().startsWith("com.valorant.tracker.")));
    assertEquals("LEADERBOARD_SESSION",context.getEnvironment().getProperty("server.servlet.session.cookie.name"));
    assertEquals("8090",context.getEnvironment().getProperty("server.port"));
  }
  @Test void servesDashboardAndIndependentStatus() throws Exception {
    mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/leaderboard.html"));
    mvc.perform(get("/leaderboard.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Valorant Ranked Observatory")));
    mvc.perform(get("/api/leaderboard/status")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
  }
  @Test void canonicalOverviewAndRegionRoutesPreserveLegacyActScope() throws Exception {
    String act="01234567-89ab-cdef-0123-456789abcdef";
    mvc.perform(get("/overview").param("act",act)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=world&act="+act));
    mvc.perform(get("/regions")).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=world"));
    mvc.perform(get("/regions").param("region","ap").param("act",act)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=region&region=ap&act="+act));
    mvc.perform(get("/regions/eu").param("act",act)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=region&region=eu&act="+act));
    mvc.perform(get("/regions/unknown")).andExpect(status().isNotFound());
    mvc.perform(get("/overview").param("act","invalid")).andExpect(status().isBadRequest());
  }

  @Test void canonicalCountryRoutesPreserveRegionSearchAndActContext() throws Exception {
    String act="01234567-89ab-cdef-0123-456789abcdef";
    mvc.perform(get("/countries")).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=countries"));
    mvc.perform(get("/countries").param("region","ap").param("q","India").param("act",act))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=countries&region=ap&q=India&act="+act));
    mvc.perform(get("/countries/in").param("region","ap").param("act",act))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=country&region=ap&country=in&act="+act));
    mvc.perform(get("/countries/unknown").param("region","ap")).andExpect(status().isNotFound());
    mvc.perform(get("/countries/in").param("region","invalid")).andExpect(status().isNotFound());
    mvc.perform(get("/countries/in")).andExpect(status().isBadRequest());
    mvc.perform(get("/countries").param("region","invalid")).andExpect(status().isNotFound());
    mvc.perform(get("/countries").param("act","invalid")).andExpect(status().isBadRequest());
  }

  @Test void canonicalGlobalLeaderboardRoutePreservesSearchPageAndAct() throws Exception {
    String act="01234567-89ab-cdef-0123-456789abcdef";
    mvc.perform(get("/leaderboard")).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=global&region=all&country=all"));
    mvc.perform(get("/leaderboard").param("q","India #1").param("page","3").param("act",act))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=global&region=all&country=all&q=India%20%231&page=3&act="+act));
    mvc.perform(get("/leaderboard").param("sort","rr").param("direction","desc"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/leaderboard.html?view=global&region=all&country=all&sort=rr&direction=desc"));
    mvc.perform(get("/leaderboard").param("page","0")).andExpect(status().isBadRequest());
    mvc.perform(get("/leaderboard").param("act","invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/leaderboard").param("q","x".repeat(101))).andExpect(status().isBadRequest());
    mvc.perform(get("/leaderboard").param("sort","sql-injection")).andExpect(status().isBadRequest());
  }

  @Test void canonicalPlayerProfileRouteForwardsEncodedIdentityWithoutLosingQuery() throws Exception {
    mvc.perform(get("/players").param("player","S8UL SkRossi#4646").param("region","ap"))
        .andExpect(status().isOk()).andExpect(forwardedUrl("/leaderboard.html"));
    mvc.perform(get("/players").param("player","missing-tag").param("region","ap"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/players").param("player","Riot#tag").param("region","unknown"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/players").param("player","Riot#tag").param("region","ap").param("act","invalid"))
        .andExpect(status().isBadRequest());
  }

  @Test void canonicalCompareRouteValidatesModeAndPreservesSelections() throws Exception {
    String act="01234567-89ab-cdef-0123-456789abcdef";
    mvc.perform(get("/compare")).andExpect(status().isOk())
        .andExpect(forwardedUrl("/leaderboard.html?view=compare&mode=country&window=60"));
    mvc.perform(get("/compare").param("mode","player").param("act",act)
        .param("a","Player One#TAG").param("b","Player Two#TAG").param("window","360"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/leaderboard.html?view=compare&mode=player&act="+act+"&a=Player%20One%23TAG&b=Player%20Two%23TAG&window=360"));
    mvc.perform(get("/compare").param("mode","unknown")).andExpect(status().isBadRequest());
    mvc.perform(get("/compare").param("window","999")).andExpect(status().isBadRequest());
  }
  @Test void canonicalArchiveRoutePreservesActAndRejectsMalformedAct() throws Exception {
    String act="01234567-89ab-cdef-0123-456789abcdef";
    mvc.perform(get("/archive")).andExpect(status().isOk()).andExpect(forwardedUrl("/leaderboard.html?view=archive"));
    mvc.perform(get("/archive").param("act",act)).andExpect(status().isOk())
        .andExpect(forwardedUrl("/leaderboard.html?view=archive&act="+act));
    mvc.perform(get("/archive").param("act","invalid")).andExpect(status().isBadRequest());
  }

  @Test void canonicalTrendsRoutePreservesValidatedScope() throws Exception {
    mvc.perform(get("/trends").param("region", "ap").param("country", "in"))
        .andExpect(status().isOk()).andExpect(forwardedUrl("/leaderboard.html?view=trends&region=ap&country=in"));
    mvc.perform(get("/trends").param("region", "invalid"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/trends").param("country", "invalid"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/trends").param("country", "in"))
        .andExpect(status().isBadRequest());
  }

  @Test void playerSortRejectsUnsupportedColumnsAndDirections() throws Exception {
    mvc.perform(get("/api/leaderboard/players").param("sort","p.player_key;drop table x"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/leaderboard/players").param("direction","sideways"))
        .andExpect(status().isBadRequest());
  }

  @Test void collectionUsesIndependentAuthenticationAndCsrf() throws Exception {
    mvc.perform(post("/api/leaderboard/collect").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/leaderboard/collect").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    mvc.perform(post("/api/leaderboard/collect").with(user("admin").roles("ADMIN")).with(csrf()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.accepted").value(false));
    mvc.perform(get("/login")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/signin.html"));
    mvc.perform(get("/signin.html")).andExpect(status().isOk());
    mvc.perform(get("/signup.html")).andExpect(status().isOk());
    mvc.perform(post("/api/leaderboard/collect?country=in&country=ph").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    mvc.perform(post("/api/leaderboard/collect?country=invalid").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    mvc.perform(get("/api/leaderboard/players?region=ap&country=unknown")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/leaderboard/players?region=invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/leaderboard/history?player=x&region=all")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/leaderboard/trends?minutes=17")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/leaderboard/trends?direction=invalid")).andExpect(status().isBadRequest());
  }
  @Test void createsHashedIndependentAccountsAndSignsInWithoutGrantingAdminPrivileges()throws Exception {
    String json="{\"username\":\"Sample_User\",\"name\":\"Sample Player\",\"email\":\"sample@example.test\",\"password\":\"A-local-test-password\",\"role\":\"ADMIN\"}";
    mvc.perform(post("/api/account/register").contentType("application/json").content(json)).andExpect(status().isForbidden());
    mvc.perform(post("/api/account/register").with(csrf()).contentType("application/json").content(json)).andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("sample_user"));
    String hash=db.queryForObject("select password_hash from observatory_accounts where username='sample_user'",String.class);
    assertNotEquals("A-local-test-password",hash);assertTrue(passwords.matches("A-local-test-password",hash));
    mvc.perform(post("/api/account/register").with(csrf()).contentType("application/json").content(json)).andExpect(status().isConflict());
    var login=mvc.perform(post("/login").with(csrf()).param("username","sample@example.test").param("password","A-local-test-password")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/leaderboard.html")).andReturn();
    var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);assertNotNull(session);
    mvc.perform(get("/api/account/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.administrator").value(false)).andExpect(jsonPath("$.name").value("Sample Player"));
    mvc.perform(post("/api/leaderboard/collect").session(session).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get("/api/account/me")).andExpect(jsonPath("$.authenticated").value(false));
    mvc.perform(post("/login").with(csrf()).param("username","sample_user").param("password","wrong-password")).andExpect(redirectedUrl("/signin.html?error=1"));
    mvc.perform(post("/api/account/register").with(csrf()).contentType("application/json").content(json.replace("Sample_User","admin").replace("sample@example.test","reserved@example.test"))).andExpect(status().isBadRequest());
  }

  @Test void administratorCanAuthenticateRepeatedlyAfterCredentialsAreErased()throws Exception {
    for(int n=0;n<2;n++)mvc.perform(get("/api/account/me").with(httpBasic("admin","test-admin-only"))).andExpect(status().isOk()).andExpect(jsonPath("$.administrator").value(true));
    mvc.perform(post("/login").with(csrf()).param("username","admin").param("password","test-admin-only")).andExpect(redirectedUrl("/leaderboard.html"));
    mvc.perform(get("/api/account/me").with(httpBasic("admin","test-admin-only"))).andExpect(status().isOk()).andExpect(jsonPath("$.administrator").value(true));
  }

  @Test void watchlistPersistsAllTypesAndIsolatedByAccount()throws Exception {
    db.update("insert into observatory_accounts(username,display_name,email,password_hash) values(?,?,?,?)","watch_a","Watch A","watch-a@example.test","hash-a");
    db.update("insert into observatory_accounts(username,display_name,email,password_hash) values(?,?,?,?)","watch_b","Watch B","watch-b@example.test","hash-b");
    var owner=user("watch_a").roles("USER");var other=user("watch_b").roles("USER");
    mvc.perform(get("/api/account/watchlist")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/account/watchlist").with(owner).contentType("application/json").content("{\"type\":\"REGION\",\"key\":\"eu\",\"scope\":\"all\",\"label\":\"Europe\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/account/watchlist").with(owner).with(csrf()).contentType("application/json").content("{\"type\":\"PLAYER\",\"key\":\"Example#123\",\"scope\":\"invalid\",\"label\":\"Example\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/account/watchlist").with(owner).with(csrf()).contentType("application/json").content("{\"type\":\"PLAYER\",\"key\":\"Example#123\",\"scope\":\"ap\",\"label\":\"Example · AP\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("PLAYER"));
    mvc.perform(post("/api/account/watchlist").with(owner).with(csrf()).contentType("application/json").content("{\"type\":\"COUNTRY\",\"key\":\"in\",\"scope\":\"ap\",\"label\":\"India · AP\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/account/watchlist").with(owner).with(csrf()).contentType("application/json").content("{\"type\":\"REGION\",\"key\":\"eu\",\"scope\":\"all\",\"label\":\"Europe\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/account/watchlist").with(owner).with(csrf()).contentType("application/json").content("{\"type\":\"ACT\",\"key\":\"ce2783e8-44fc-dd48-3da3-33b5ba6c4a22\",\"scope\":\"all\",\"label\":\"V26 Act III\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/account/watchlist").with(owner).with(csrf()).contentType("application/json").content("{\"type\":\"PLAYER\",\"key\":\"Example#123\",\"scope\":\"ap\",\"label\":\"Updated label\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Updated label"));
    var listing=mvc.perform(get("/api/account/watchlist").with(owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(4,com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(listing).size());
    mvc.perform(get("/api/account/watchlist").with(other)).andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
    Long itemId=db.queryForObject("select id from observatory_watchlist where account_id=(select id from observatory_accounts where username='watch_a') and entity_type='ACT'",Long.class);
    mvc.perform(delete("/api/account/watchlist/"+itemId).with(other).with(csrf())).andExpect(status().isNotFound());
    mvc.perform(delete("/api/account/watchlist/"+itemId).with(owner).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get("/api/account/watchlist").with(owner)).andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(3)));
  }

  @Test void matchAnalysisPageUsesCanonicalRoute() throws Exception {
    mvc.perform(get("/match-analysis")).andExpect(status().isOk())
        .andExpect(forwardedUrl("/leaderboard.html?view=match-analysis"));
  }
  @Test void watchlistPageUsesCanonicalRoute()throws Exception {
    mvc.perform(get("/watchlist")).andExpect(status().isOk()).andExpect(forwardedUrl("/leaderboard.html?view=watchlist"));
  }

  @Test void displayPreferencesPersistPrivatelyAndRequireCsrf()throws Exception {
    db.update("insert into observatory_accounts(username,display_name,email,password_hash) values(?,?,?,?)","prefs_a","Prefs A","prefs-a@example.test","hash-a");
    db.update("insert into observatory_accounts(username,display_name,email,password_hash) values(?,?,?,?)","prefs_b","Prefs B","prefs-b@example.test","hash-b");
    var owner=user("prefs_a").roles("USER");var other=user("prefs_b").roles("USER");
    mvc.perform(get("/api/account/preferences")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/account/preferences").with(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.compactTables").value(false)).andExpect(jsonPath("$.reduceMotion").value(false));
    mvc.perform(put("/api/account/preferences").with(owner).contentType("application/json").content("{\"compactTables\":true,\"reduceMotion\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/account/preferences").with(owner).with(csrf()).contentType("application/json").content("{\"compactTables\":true,\"reduceMotion\":false}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.compactTables").value(true));
    mvc.perform(get("/api/account/preferences").with(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.compactTables").value(true)).andExpect(jsonPath("$.reduceMotion").value(false));
    mvc.perform(get("/api/account/preferences").with(other)).andExpect(status().isOk()).andExpect(jsonPath("$.compactTables").value(false));
    mvc.perform(put("/api/account/preferences").with(owner).with(csrf()).contentType("application/json").content("{\"compactTables\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test void settingsMethodologyAndRoadmapPagesHaveCanonicalTargets()throws Exception {
    mvc.perform(get("/settings")).andExpect(status().isOk()).andExpect(forwardedUrl("/leaderboard.html?view=settings"));
    mvc.perform(get("/methodology")).andExpect(status().isOk()).andExpect(forwardedUrl("/methodology.html"));
    mvc.perform(get("/roadmap")).andExpect(status().isOk()).andExpect(forwardedUrl("/roadmap.html"));
  }

  @Test void matchIntelligenceImportsAreCsrfProtectedDeduplicatedAndOwnerScoped() throws Exception {
    String json=MatchIntelligenceFixtures.JSON.writeValueAsString(MatchIntelligenceFixtures.export(2,2));
    var owner=user("mi-owner").roles("USER");var other=user("mi-other").roles("USER");
    mvc.perform(post("/api/match-intelligence/imports").with(csrf()).contentType("application/json").content(json)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/match-intelligence/imports").with(owner).contentType("application/json").content(json)).andExpect(status().isForbidden());
    var imported=mvc.perform(post("/api/match-intelligence/imports").with(owner).with(csrf()).contentType("application/json").content(json))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETE"))
        .andExpect(jsonPath("$.acceptedMatches").value(1)).andExpect(jsonPath("$.rejectedMatches").value(0)).andReturn();
    String body=imported.getResponse().getContentAsString();
    String jobId=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).path("jobId").asText();
    assertEquals(1,db.queryForObject("select count(*) from mi_matches",Integer.class));
    assertEquals(1,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
    assertEquals(1,db.queryForObject("select count(*) from mi_import_job_matches",Integer.class));
    assertEquals(1,db.queryForObject("select count(*) from mi_raw_imports",Integer.class));
    assertEquals(2,db.queryForObject("select count(*) from mi_match_rounds",Integer.class));
    assertEquals(20,db.queryForObject("select count(*) from mi_player_round_stats",Integer.class));
    int persistedEvents=db.queryForObject("select count(*) from mi_match_events",Integer.class);
    assertTrue(persistedEvents>0);
    assertTrue(db.queryForObject("select count(*) from mi_event_positions",Integer.class)>0);
    var duplicateImport=mvc.perform(post("/api/match-intelligence/imports").with(owner).with(csrf()).contentType("application/json").content(json))
        .andExpect(status().isCreated()).andReturn();
    String secondJobId=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(duplicateImport.getResponse().getContentAsString()).path("jobId").asText();
    assertEquals(1,db.queryForObject("select count(*) from mi_matches",Integer.class));
    assertEquals(1,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
    assertEquals(persistedEvents,db.queryForObject("select count(*) from mi_match_events",Integer.class));
    assertEquals(2,db.queryForObject("select count(*) from mi_import_jobs",Integer.class));
    mvc.perform(get("/api/match-intelligence/imports/"+jobId).with(owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.matches",org.hamcrest.Matchers.hasSize(1)));
    mvc.perform(get("/api/match-intelligence/matches").with(owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.matches[0].providerMatchId").value(MatchIntelligenceFixtures.MATCH_ID));
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID).with(owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.perspective.riotId").value(MatchIntelligenceFixtures.PLAYER))
        .andExpect(jsonPath("$.match.rounds",org.hamcrest.Matchers.hasSize(2)));
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID+"/analysis"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID+"/analysis").with(owner))
        .andExpect(status().isOk()).andExpect(jsonPath("$.analysisVersion").value("deterministic-tactics-v1"))
        .andExpect(jsonPath("$.metrics",org.hamcrest.Matchers.hasSize(13)))
        .andExpect(jsonPath("$.metrics[0].key").value("player_opening_kills"))
        .andExpect(jsonPath("$.metrics[0].numerator").value(1))
        .andExpect(jsonPath("$.metrics[0].denominator").value(1))
        .andExpect(jsonPath("$.findings[0].matchId").value(MatchIntelligenceFixtures.MATCH_ID));
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID+"/analysis").with(other))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/match-intelligence/benchmarks")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/match-intelligence/benchmarks").with(owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
    String members = java.util.stream.IntStream.rangeClosed(1,10).mapToObj(rank ->
        "{\"selectionRank\":"+rank+",\"riotId\":\""+(rank==1?MatchIntelligenceFixtures.PLAYER:"Radiant "+rank+"#TAG")+"\",\"displayName\":\"Selected #"+rank+"\",\"matchesRequested\":20}")
        .collect(java.util.stream.Collectors.joining(","));
    String cohortRequest = "{\"name\":\"Synthetic top-rank reference\",\"region\":\"ap\",\"actId\":\"synthetic-act\",\"leaderboardSnapshotDate\":\"2026-10-27\",\"dataAccessStatus\":\"OWNER_REPORTED_PERMISSION\",\"members\":["+members+"]}";
    mvc.perform(post("/api/match-intelligence/benchmarks").with(owner).contentType("application/json").content(cohortRequest))
        .andExpect(status().isForbidden());
    var cohortCreated=mvc.perform(post("/api/match-intelligence/benchmarks").with(owner).with(csrf()).contentType("application/json").content(cohortRequest))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.dataAccessStatusMeaning").value(org.hamcrest.Matchers.containsString("not independently verified")))
        .andExpect(jsonPath("$.uniqueMatches").value(1)).andExpect(jsonPath("$.members",org.hamcrest.Matchers.hasSize(10)))
        .andExpect(jsonPath("$.members[0].identityAvailable").value(true)).andExpect(jsonPath("$.members[0].matchesSuccessfullyImported").value(1))
        .andExpect(jsonPath("$.members[1].identityAvailable").value(false)).andReturn();
    String cohortId=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(cohortCreated.getResponse().getContentAsString()).path("cohortId").asText();
    mvc.perform(get("/api/match-intelligence/benchmarks/"+cohortId).with(other)).andExpect(status().isNotFound());
    mvc.perform(get("/api/match-intelligence/players/{playerId}/benchmark-comparison","Player 0#TAG").with(owner).param("cohortId",cohortId))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
        .andExpect(jsonPath("$.eligibleReferencePlayers").value(0)).andExpect(jsonPath("$.excludedSubjectMatches").value(1))
        .andExpect(jsonPath("$.metrics[0].referenceMedian").doesNotExist());
    mvc.perform(get("/api/match-intelligence/players/{playerId}/benchmark-comparison","Unrelated#TAG").with(owner).param("cohortId",cohortId))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
        .andExpect(jsonPath("$.eligibleReferencePlayers").value(1)).andExpect(jsonPath("$.uniqueReferenceMatches").value(1))
        .andExpect(jsonPath("$.metrics[0].playersWithMetric").value(1)).andExpect(jsonPath("$.metrics[0].uniqueReferenceMatches").value(1))
        .andExpect(jsonPath("$.metrics[0].referenceMedian").doesNotExist());
    String duplicateMemberRequest=cohortRequest.replace("Radiant 2#TAG","player 0#tag");
    mvc.perform(post("/api/match-intelligence/benchmarks").with(owner).with(csrf()).contentType("application/json").content(duplicateMemberRequest))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID+"/rounds/1").with(owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.roundNumber").value(1)).andExpect(jsonPath("$.playerRounds",org.hamcrest.Matchers.hasSize(10)));
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID).with(other)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/match-intelligence/imports/"+jobId+"/raw").with(other).with(csrf())).andExpect(status().isNotFound());
    mvc.perform(delete("/api/match-intelligence/imports/"+jobId+"/raw").with(owner)).andExpect(status().isForbidden());
    mvc.perform(delete("/api/match-intelligence/imports/"+jobId+"/raw").with(owner).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(delete("/api/match-intelligence/imports/"+jobId).with(other).with(csrf())).andExpect(status().isNotFound());
    mvc.perform(delete("/api/match-intelligence/imports/"+jobId).with(owner).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID).with(owner)).andExpect(status().isOk());
    mvc.perform(delete("/api/match-intelligence/imports/"+secondJobId).with(owner).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get("/api/match-intelligence/matches/"+MatchIntelligenceFixtures.MATCH_ID).with(owner)).andExpect(status().isNotFound());
    assertEquals(0,db.queryForObject("select count(*) from mi_match_versions",Integer.class));
    assertEquals(0,db.queryForObject("select count(*) from mi_matches",Integer.class));
  }

}
