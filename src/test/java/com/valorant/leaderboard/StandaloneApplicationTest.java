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

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:standalone;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.hikari.schema=PUBLIC", "spring.flyway.enabled=false",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/migration/V1__ranked_leaderboard_history.sql,classpath:db/migration/V2__duplicate_observation_diagnostics.sql,classpath:db/migration/V3__global_leaderboard_snapshots.sql,classpath:db/migration/V4__hourly_regional_observation_cache.sql,classpath:db/migration/V5__observatory_accounts.sql",
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

}
