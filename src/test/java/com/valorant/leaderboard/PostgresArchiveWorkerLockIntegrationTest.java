package com.valorant.leaderboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requires the dedicated disposable database, never the local archive database. */
@EnabledIfEnvironmentVariable(named="LEADERBOARD_TEST_DB_URL", matches=".*ranked_observatory_redesign_test.*")
class PostgresArchiveWorkerLockIntegrationTest {
  private static final long ARCHIVE_WORKER_LOCK=782613505L;

  @Test void onlyOneConnectionOwnsArchiveWorkAndDisconnectReleasesOwnership() throws Exception {
    String url=System.getenv("LEADERBOARD_TEST_DB_URL");
    String user=System.getenv("LEADERBOARD_TEST_DB_USERNAME");
    String password=System.getenv("LEADERBOARD_TEST_DB_PASSWORD");
    try(var owner=DriverManager.getConnection(url,user,password)){
      assertTrue(tryLock(owner));
      try(var contender=DriverManager.getConnection(url,user,password)){
        assertFalse(tryLock(contender),"a second application instance must not become the archive worker");
      }
    }
    try(var recovered=DriverManager.getConnection(url,user,password)){
      assertTrue(tryLock(recovered),"closing a crashed process connection must release its session lock");
      try(var statement=recovered.createStatement()){
        statement.execute("select pg_advisory_unlock("+ARCHIVE_WORKER_LOCK+")");
      }
    }
  }

  private static boolean tryLock(java.sql.Connection connection) throws Exception {
    try(var statement=connection.prepareStatement("select pg_try_advisory_lock(?)")){
      statement.setLong(1,ARCHIVE_WORKER_LOCK);
      try(var result=statement.executeQuery()){result.next();return result.getBoolean(1);}
    }
  }
}
