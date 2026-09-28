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

  /** Friendly, plain-language text for the unique index that was duplicated. */
  private static final java.util.Map<String, String> UNIQUE_MESSAGES = java.util.Map.ofEntries(
      java.util.Map.entry("username", "Ese nombre de usuario ya esta en uso"),
      java.util.Map.entry("name", "Ya existe un registro con ese nombre"),
      java.util.Map.entry("role_id", "Ese rol ya existe"),
      java.util.Map.entry("rfc", "Ya existe un cliente con ese RFC"),
      java.util.Map.entry("curp", "Ya existe un operador con esa CURP"),
      java.util.Map.entry("phone", "Ese telefono ya esta registrado"),
      java.util.Map.entry("email", "Ese correo ya esta registrado"),
      java.util.Map.entry("license_number", "Ya existe un operador con ese numero de licencia"),
      java.util.Map.entry("license_id", "Ese operador ya tiene una licencia asignada"),
      java.util.Map.entry("employee_id", "Ese empleado ya tiene un usuario asignado"),
      java.util.Map.entry("internal_code", "Ya existe una unidad con ese numero economico"),
      java.util.Map.entry("plates", "Ya existe una unidad con esas placas"),
      java.util.Map.entry("serial_number", "Ya existe una unidad con ese numero de serie"),
      java.util.Map.entry("folio", "Ese folio ya existe"),
      java.util.Map.entry("invoice_number", "Ese numero de factura ya existe"),
      java.util.Map.entry("service_request_id", "Esa solicitud ya tiene un viaje o una factura asociada"),
      java.util.Map.entry("trip_id", "Ese viaje ya tiene una entrega registrada"),
      java.util.Map.entry("uq_routes_pair", "Ya existe una ruta con ese origen y destino"));

  /** Friendly text for the foreign key that could not be satisfied. */
  private static final java.util.Map<String, String> FOREIGN_KEY_MESSAGES = java.util.Map.ofEntries(
      java.util.Map.entry("fk_invoice_request", "No se puede facturar: la solicitud no existe"),
      java.util.Map.entry("fk_rates_client", "No se puede guardar la tarifa: el cliente no existe"),
      java.util.Map.entry("fk_trip_employee", "No se puede asignar: el operador no existe"),
      java.util.Map.entry("fk_fuel_vehicle", "No se puede guardar la carga: la unidad no existe"),
      java.util.Map.entry("fk_maint_vehicle", "No se puede guardar el mantenimiento: la unidad no existe"),
      java.util.Map.entry("fk_payment_invoice", "No se puede guardar el pago: la factura no existe"),
      java.util.Map.entry("fk_users_employee", "No se puede guardar el usuario: el empleado no existe"));

  /** Turns raw JDBC codes into clear, user-facing messages (Spanish). */
  public static String translate(SQLException failure) {
    String state = failure.getSQLState();
    if (state != null && state.startsWith("08")) {
      return "No se pudo conectar con la base de datos. "
          + "Verifique que el servidor este disponible e intente de nuevo.";
    }
    String message = failure.getMessage();
    return switch (failure.getErrorCode()) {
      case 1062 -> duplicateMessage(message);
      case 1264, 1265, 3819, 4025 -> "Uno de los valores no cumple las reglas permitidas.";
      case 1366 -> "El valor capturado no es valido para uno de los campos.";
      case 1406 -> "Uno de los textos es demasiado largo.";
      case 1048, 1364 -> "Falta un dato obligatorio.";
      case 1451 -> "No se puede eliminar: hay otros registros que dependen de este.";
      case 1452 -> foreignKeyMessage(message);
      default -> "Ocurrio un error inesperado al acceder a los datos (codigo "
          + failure.getErrorCode() + ").";
    };
  }

  /** "Duplicate entry 'X' for key 'table.index'" -> a message naming the field and value. */
  private static String duplicateMessage(String message) {
    String value = between(message, "Duplicate entry '", "'");
    String key = between(message, "for key '", "'");
    String index = key == null ? null : key.substring(key.lastIndexOf('.') + 1).toLowerCase();
    String friendly = index == null ? null : UNIQUE_MESSAGES.get(index);
    if (friendly == null) {
      friendly = "Ya existe un registro con ese valor unico";
    }
    return value == null || value.isBlank() ? friendly + "." : friendly + " (\"" + value + "\").";
  }

  /** "foreign key constraint fails (..., CONSTRAINT `fk_x` ...)" -> a message naming the link. */
  private static String foreignKeyMessage(String message) {
    String constraint = between(message, "CONSTRAINT `", "`");
    String friendly = constraint == null ? null : FOREIGN_KEY_MESSAGES.get(constraint.toLowerCase());
    return friendly == null
        ? "No se puede guardar: falta o no existe el registro relacionado."
        : friendly + ".";
  }

  private static String between(String text, String prefix, String suffix) {
    if (text == null) {
      return null;
    }
    int start = text.indexOf(prefix);
    if (start < 0) {
      return null;
    }
    start += prefix.length();
    int end = text.indexOf(suffix, start);
    return end < 0 ? null : text.substring(start, end);
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
    try (Connection connection = getConnection()) {
      return invoke(connection, callSql, outTypes, inParams);
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  /** Same as {@link #call} but on a caller-owned connection (see {@link #inTransaction}). */
  public static Object[] call(Connection connection, String callSql, int[] outTypes,
      Object... inParams) {
    try {
      return invoke(connection, callSql, outTypes, inParams);
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  private static Object[] invoke(Connection connection, String callSql, int[] outTypes,
      Object... inParams) throws SQLException {
    try (CallableStatement statement = connection.prepareCall(callSql)) {
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
    }
  }

  /** Runs a read procedure and maps all rows of its first result set. */
  public static <T> List<T> callList(String callSql, RowMapper<T> mapper, Object... params) {
    try (Connection connection = getConnection()) {
      return readList(connection, callSql, mapper, params);
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  /** Same as {@link #callList} but on a caller-owned connection. */
  public static <T> List<T> callList(Connection connection, String callSql, RowMapper<T> mapper,
      Object... params) {
    try {
      return readList(connection, callSql, mapper, params);
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
  }

  private static <T> List<T> readList(Connection connection, String callSql, RowMapper<T> mapper,
      Object... params) throws SQLException {
    try (CallableStatement statement = connection.prepareCall(callSql)) {
      bind(statement, params);
      try (ResultSet rs = statement.executeQuery()) {
        List<T> rows = new ArrayList<>();
        while (rs.next()) {
          rows.add(mapper.map(rs));
        }
        return rows;
      }
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

  /** Calls a procedure shaped as {@code OUT p_problems TEXT, OUT p_id BIGINT}. */
  public static Result<Long> callForProblemsAndId(String callSql, Object... inParams) {
    Object[] out = call(callSql, new int[] { Types.VARCHAR, Types.BIGINT }, inParams);
    String problems = asProblems(out[0]);
    return problems == null ? Result.ok(asLong(out[1])) : Result.err(problems);
  }

  /** Connection-scoped variant of {@link #callVoid}. */
  public static Result<Void> callVoid(Connection connection, String callSql, Object... inParams) {
    Object[] out = call(connection, callSql, new int[] { Types.VARCHAR }, inParams);
    String problems = asProblems(out[0]);
    return problems == null ? Result.ok(null) : Result.err(problems);
  }

  /** Connection-scoped variant of {@link #callForId}. */
  public static Result<Long> callForId(Connection connection, String callSql, Object... inParams) {
    Object[] out = call(connection, callSql, new int[] { Types.BIGINT, Types.VARCHAR }, inParams);
    Long id = asLong(out[0]);
    String problems = asProblems(out[1]);
    return problems == null ? Result.ok(id) : Result.err(problems);
  }

  /** Connection-scoped variant of {@link #callNoOut}. */
  public static void callNoOut(Connection connection, String callSql, Object... inParams) {
    call(connection, callSql, new int[0], inParams);
  }

  /** A unit of work that receives a shared connection and may throw SQLException. */
  @FunctionalInterface
  public interface TransactionWork<T> {
    T run(Connection connection) throws SQLException;
  }

  /**
   * Runs several statements on one connection inside a transaction: commits on
   * success, rolls back on any failure. Lets repositories join one unit of work
   * (PRD 3.5), which the single-call helpers above cannot.
   */
  public static <T> T inTransaction(TransactionWork<T> work) {
    try (Connection connection = getConnection()) {
      connection.setAutoCommit(false);
      try {
        T result = work.run(connection);
        connection.commit();
        return result;
      } catch (Throwable failure) {
        try {
          connection.rollback();
        } catch (SQLException ignored) {
          // keep the original failure
        }
        if (failure instanceof SQLException sql) {
          throw new DataException(translate(sql), sql);
        }
        if (failure instanceof RuntimeException runtime) {
          throw runtime;
        }
        throw new DataException(String.valueOf(failure.getMessage()), new SQLException(failure));
      }
    } catch (SQLException failure) {
      throw new DataException(translate(failure), failure);
    }
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
