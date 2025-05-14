package com.umg.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresConnection {

    // Ajusta estos datos a tu entorno
    private static final String URL = "jdbc:postgresql://localhost:5432/assenta_db"; // Cambié a postgres
    private static final String USER = "postgres"; // El usuario que uses en Postgres
    private static final String PASS = "umg.2025"; // Tu contraseña

    static {
        try {
            Class.forName("org.postgresql.Driver"); // Driver de PostgreSQL
        } catch (ClassNotFoundException e) {
            System.err.println("Error al cargar el Driver de PostgreSQL: " + e.getMessage());
        }
    }

    /**
     * Intenta obtener una conexión a la base de datos PostgreSQL.
     * Imprime en consola si fue exitosa o falló.
     * @return Connection si fue exitosa; null si falló.
     */
    public static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            if (conn != null && !conn.isClosed()) {
                //System.out.println("✅ Conexión a PostgreSQL exitosa.");
                return conn;
            } else {
                System.err.println("⚠️ Conexión a PostgreSQL retornó nula o cerrada.");
                return null;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al conectar con PostgreSQL: " + e.getMessage());
            return null;
        }
    }
}
