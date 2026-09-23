package mx.marjan.shared;

import java.sql.Connection;
import java.sql.SQLException;

/** Facade over {@link SequenceRepository} used by feature repositories. */
public final class Sequences {

    private Sequences() {}

    public static long next(Connection connection, String table) throws SQLException {
        return SequenceRepository.next(connection, table);
    }
}
