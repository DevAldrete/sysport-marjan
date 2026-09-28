package mx.marjan.shared;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link Database} must prefer an installed pool over {@link java.sql.DriverManager}. */
class DatabaseDataSourceTest {

    @Test
    void usesTheInstalledDataSourceInsteadOfDriverManager() {
        DataSource pool = new StubDataSource();
        Database.useDataSource(pool);
        try {
            SQLException failure = assertThrows(SQLException.class, Database::getConnection);
            assertEquals("from-pool", failure.getMessage());
        } finally {
            Database.useDataSource(null);
        }
    }

    /** Minimal DataSource whose only meaningful method is {@code getConnection}. */
    private static final class StubDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("from-pool", "08001", 0);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
