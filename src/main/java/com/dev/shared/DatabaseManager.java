package com.dev.shared;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager implements AutoCloseable {
	private final String url;

	private Connection conn;

	public DatabaseManager(String username, String password, String dbName) throws SQLException {
		String driver = "mariadb";
		String host = "localhost";
		String port = "3306";
		this.url = "jdbc:" + driver + "://" + host + ":" + port + "/" + dbName;
		this.conn = DriverManager.getConnection(url, username, password);
	}
	
	public DatabaseManager(String driver, String host, String port, String username, String password, String dbName) throws SQLException {
		this.driver = driver;
		this.host = host;
		this.port = port;
		this.username = username;
		this.pswd = password;
		this.url = "jdbc:" + this.driver + "://" + this.host + ":" + this.port + "/" + dbName;

		this.conn = DriverManager.getConnection(this.url, this.username, this.pswd)
	}

	public Connection getConn() {
		return this.conn;
	}

	@Override
	public void close() throws SQLException {
		this.conn.close();
	}
}
