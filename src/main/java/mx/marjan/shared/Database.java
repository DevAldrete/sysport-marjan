package mx.marjan.shared;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single place that knows how to reach the database.
 * Repositories contain the SQL; this class only manages connections.
 */
public final class Database {

    private Database() {}

    public static String url() {
        String direct = env("DB_URL", null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return "jdbc:mariadb://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306")
                + "/" + env("DB_NAME", "sysportdb");
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

    @FunctionalInterface
    public interface TxWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Runs work in a transaction: commit on success, rollback on any failure. */
    public static <T> T inTransaction(TxWork<T> work) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception failure) {
            throw new RuntimeException(failure.getMessage(), failure);
        }
    }

    public static <T> List<T> queryList(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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

    public static <T> Optional<T> queryOne(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> rows = queryList(connection, sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public static int update(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    public static long insertReturningId(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("No generated key returned");
            }
        }
    }

    public static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    // Convenience overloads for simple reads/writes that open their own connection.

    public static <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = getConnection()) {
            return queryList(connection, sql, mapper, params);
        } catch (SQLException failure) {
            throw new RuntimeException(failure.getMessage(), failure);
        }
    }

    public static <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = getConnection()) {
            return queryOne(connection, sql, mapper, params);
        } catch (SQLException failure) {
            throw new RuntimeException(failure.getMessage(), failure);
        }
    }

    public static int update(String sql, Object... params) {
        try (Connection connection = getConnection()) {
            return update(connection, sql, params);
        } catch (SQLException failure) {
            throw new RuntimeException(failure.getMessage(), failure);
        }
    }

    public static long insert(String sql, Object... params) {
        try (Connection connection = getConnection()) {
            return insertReturningId(connection, sql, params);
        } catch (SQLException failure) {
            throw new RuntimeException(failure.getMessage(), failure);
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
