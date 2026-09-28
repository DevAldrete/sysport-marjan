package mx.marjan.api.db;

import io.micronaut.context.annotation.Context;
import javax.sql.DataSource;
import mx.marjan.shared.Database;

/**
 * Installs the managed Hikari pool into the core {@link Database} helpers at
 * startup, so every repository keeps calling the same static accessors.
 */
@Context
public class DatabasePool {

    public DatabasePool(DataSource dataSource) {
        Database.useDataSource(dataSource);
    }
}
