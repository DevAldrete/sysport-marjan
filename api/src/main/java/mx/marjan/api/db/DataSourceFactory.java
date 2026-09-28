package mx.marjan.api.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import mx.marjan.shared.Database;

/**
 * Builds the Hikari pool from the same environment variables the desktop app
 * reads, so both clients point at the same database with the same defaults.
 */
@Factory
public class DataSourceFactory {

    private final int maximumPoolSize;

    public DataSourceFactory(@Value("${sysport.db.pool-size:10}") int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    @Singleton
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Database.url());
        config.setUsername(Database.user());
        config.setPassword(Database.password());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setPoolName("sysport");
        return new HikariDataSource(config);
    }
}
