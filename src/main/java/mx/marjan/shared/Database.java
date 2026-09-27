package mx.marjan.shared;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single place that knows how to reach the database.
 * Repositories contain the SQL; this class only manages connections.
 */
public final class Database {

  private Database() {
  }

  public static String url() {
    String direct = env("DB_URL", null);
    if (direct != null && !direct.isBlank()) {
      return direct;
    }
    return "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306")
        + "/" + env("DB_NAME", "sysportdb")
        + "?sslMode=DISABLED&allowPublicKeyRetrieval=true";
  }

  public static String user() {
    return env("DB_USER", "marjan");
  }

  public static String password() {
    return env("DB_PASSWORD", "changeme");
  }

  private static String env(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(url(), user(), password());
  }

  public static boolean testConnection() {
    try (Connection connection = getConnection()) {
      return connection.isValid(2);
    } catch (SQLException failure) {
      return false;
    }
  }

  /** Turns raw JDBC codes into user-facing messages (Spanish). */
  public static String translate(SQLException failure) {
    return switch (failure.getErrorCode()) {
      case 1062 -> "Ya existe un registro con ese valor unico.";
      case 1264, 1265 -> "Uno de los valores esta fuera del rango permitido.";
      case 1406 -> "Uno de los textos es demasiado largo.";
      case 1048, 1364 -> "Falta un dato obligatorio.";
      case 1451 -> "No se puede eliminar: hay registros relacionados.";
      case 1452 -> "No se puede guardar: la referencia relacionada no existe.";
      default -> failure.getMessage();
    };
  }

  public static void bind(PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      statement.setObject(i + 1, params[i]);
    }
  }

  /**
   * Invokes a stored procedure, registering {@code outTypes.length} OUT parameters
   * after the IN parameters, and returns their values in order.
   */
  public static Object[] call(String callSql, int[] outTypes, Object... inParams) {
    try (Connection connection = getConnection();
        CallableStatement statement = connection.prepareCall(callSql)) {
      bind(statement, inParams);
      for (int i = 0; i < outTypes.length; i++) {
        statement.registerOutParameter(inParams.length + 1 + i, outTypes[i]);
      }
      statement.execute();
      Object[] outs = new Object[outTypes.length];
      for (int i = 0; i < outTypes.length; i++) {
        outs[i] = statement.getObject(inParams.length + 1 + i);
      }
      return outs;
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  /** Runs a read procedure and maps all rows of its first result set. */
  public static <T> List<T> callList(String callSql, RowMapper<T> mapper, Object... params) {
    try (Connection connection = getConnection();
        CallableStatement statement = connection.prepareCall(callSql)) {
      bind(statement, params);
      try (ResultSet rs = statement.executeQuery()) {
        List<T> rows = new ArrayList<>();
        while (rs.next()) {
          rows.add(mapper.map(rs));
        }
        return rows;
      }
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  /** Runs a read procedure and maps the first row of its first result set. */
  public static <T> Optional<T> callOne(String callSql, RowMapper<T> mapper, Object... params) {
    List<T> rows = callList(callSql, mapper, params);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** Calls a procedure with no OUT parameters (pure write). */
  public static void callNoOut(String callSql, Object... inParams) {
    call(callSql, new int[0], inParams);
  }

  /** Calls a procedure whose single OUT is {@code p_problems TEXT}. */
  public static Result<Void> callVoid(String callSql, Object... inParams) {
    Object[] out = call(callSql, new int[] { Types.VARCHAR }, inParams);
    String problems = asProblems(out[0]);
    return problems == null ? Result.ok(null) : Result.err(problems);
  }

  /** Calls a procedure shaped as {@code OUT p_id BIGINT, OUT p_problems TEXT}. */
  public static Result<Long> callForId(String callSql, Object... inParams) {
    Object[] out = call(callSql, new int[] { Types.BIGINT, Types.VARCHAR }, inParams);
    Long id = asLong(out[0]);
    String problems = asProblems(out[1]);
    return problems == null ? Result.ok(id) : Result.err(problems);
  }

  public static Long asLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  public static String asProblems(Object value) {
    if (value == null) {
      return null;
    }
    String problems = value.toString().trim();
    return problems.isEmpty() ? null : problems;
  }

  /** Runs a read procedure and returns the raw first result set as a report. */
  public static <T> T callReport(java.util.function.Function<ResultSet, T> reader, String callSql,
      Object... params) {
    try (Connection connection = getConnection();
        CallableStatement statement = connection.prepareCall(callSql)) {
      bind(statement, params);
      try (ResultSet rs = statement.executeQuery()) {
        return reader.apply(rs);
      }
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  @FunctionalInterface
  public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
  }
}
