import java.nio.file.*;
import java.sql.*;
/** Read-only, repeatable-read export; credentials remain in the child process environment. */
class ExportActGrinding {
 public static void main(String[] args)throws Exception {
  try(var connection=DriverManager.getConnection(System.getenv("LEADERBOARD_DB_URL"),System.getenv("LEADERBOARD_DB_USERNAME"),System.getenv("LEADERBOARD_DB_PASSWORD"))){
   connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
   try(var schema=connection.createStatement()){schema.execute("set search_path to leaderboard");schema.execute("set statement_timeout='120s'");}
   try(var statement=connection.createStatement();var result=statement.executeQuery(Files.readString(Path.of(args[0])))){
    if(!result.next())throw new IllegalStateException("No export result");Files.writeString(Path.of(args[1]),result.getString(1));
   }
   connection.rollback();
  }
 }
}
