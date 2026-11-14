package com.tecnopc.admin;

import java.sql.Connection;
import java.util.ServiceLoader;
import org.flywaydb.community.database.mysql.tidb.TiDBDatabaseType;
import org.flywaydb.core.extensibility.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class TiDBMigrationSupportTest {
    @Test void migrationEngineDiscoversTiDBInsteadOfTreatingItAsLegacyMySql() throws Exception {
        var type=ServiceLoader.load(Plugin.class).stream().map(ServiceLoader.Provider::get)
            .filter(TiDBDatabaseType.class::isInstance).map(TiDBDatabaseType.class::cast).findFirst().orElseThrow();
        assertTrue(type.handlesDatabaseProductNameAndVersion("MySQL","5.7.25-TiDB-v8.5.0",connection("5.7.25-TiDB-v8.5.0")));
        assertFalse(type.handlesDatabaseProductNameAndVersion("MySQL","8.0.40",connection("8.0.40")));
    }
    private Connection connection(String version) throws Exception {
        var connection=mock(Connection.class);var statement=mock(java.sql.PreparedStatement.class);var result=mock(java.sql.ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true,false);when(result.getString(1)).thenReturn(version);return connection;
    }
}
