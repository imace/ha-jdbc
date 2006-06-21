/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (c) 2004-2006 Paul Ferraro
 * 
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by the 
 * Free Software Foundation; either version 2.1 of the License, or (at your 
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Contact: ferraro@users.sourceforge.net
 */
package net.sf.hajdbc.cache;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.sf.hajdbc.DatabaseMetaDataCache;
import net.sf.hajdbc.ForeignKeyConstraint;
import net.sf.hajdbc.UniqueConstraint;

/**
 * DatabaseMetaDataCache implementation for populating actual cache implementations.
 * 
 * @author Paul Ferraro
 * @since 1.2
 */
public class DatabaseMetaDataCacheImpl implements DatabaseMetaDataCache
{
	// As defined in SQL-92 specification: http://www.andrew.cmu.edu/user/shadow/sql/sql1992.txt
	private static final String[] SQL_92_RESERVED_WORDS = new String[] {
		// SQL-92 reserved words
		"absolute", "action", "add", "all", "allocate", "alter", "and", "any", "are", "as", "asc", "assertion", "at", "authorization", "avg",
		"begin", "between", "bit", "bit_length", "both", "by",
		"cascade", "cascaded", "case", "cast", "catalog", "char", "character", "char_length", "character_length", "check", "close", "coalesce", "collate", "collation", "column", "commit", "connect", "connection", "constraint", "constraints", "continue", "convert", "corresponding", "count", "create", "cross", "current", "current_date", "current_time", "current_timestamp", "current_user", "cursor",
		"date", "day", "deallocate", "dec", "decimal", "declare", "default", "deferrable", "deferred", "delete", "desc", "describe", "descriptor", "diagnostics", "disconnect", "distinct", "domain", "double", "drop",
		"else", "end", "end-exec", "escape", "except", "exception", "exec", "execute", "exists", "external", "extract",
		"false", "fetch", "first", "float", "for", "foreign", "found", "from", "full",
		"get", "global", "go", "goto", "grant", "group",
		"having", "hour",
		"identity", "immediate", "in", "indicator", "initially", "inner", "input", "insensitive", "insert", "int", "integer", "intersect", "interval", "into", "is", "isolation",
		"join",
		"key",
		"language", "last", "leading", "left", "level", "like", "local", "lower",
		"match", "max", "min", "minute", "module", "month",
		"names", "national", "natural", "nchar", "next", "no", "not", "null", "nullif", "numeric",
		"octet_length", "of", "on", "only", "open", "option", "or", "order", "outer", "output", "overlaps",
		"pad", "partial", "position", "precision", "prepare", "preserve", "primary", "prior", "privileges", "procedure", "public",
		"read", "real", "references", "relative", "restrict", "revoke", "right", "rollback", "rows",
		"schema", "scroll", "second", "section", "select", "session", "session_user", "set", "size", "smallint", "some", "space", "sql", "sqlcode", "sqlerror", "sqlstate", "substring", "sum", "system_user",
		"table", "temporary", "then", "time", "timestamp", "timezone_hour", "timezone_minute", "to", "trailing", "transaction", "translate", "translation", "trim", "true",
		"union", "unique", "unknown", "update", "upper", "usage", "user", "using",
		"value", "values", "varchar", "varying", "view",
		"when", "whenever", "where", "with", "work", "write",
		"year",
		"zone",
		// ISO/IEC 9075:1989 reserved words
		"after", "alias", "async",
		"before", "boolean", "breadth",
		"completion", "call", "cycle",
		"data", "depth", "dictionary",
		"each", "elseif", "equals",
		"general",
		"if", "ignore",
		"leave", "less", "limit", "loop",
		"modify",
		"new", "none",
		"object", "off", "oid", "old", "operation", "operators", "others",
		"parameters", "pendant", "preorder", "private", "protected",
		"recursive", "ref", "referencing", "replace", "resignal", "return", "returns", "role", "routine", "row",
		"savepoint", "search", "sensitive", "sequence", "signal", "similar", "sqlexception", "sqlwarning", "structure",
		"test", "there", "trigger", "type",
		"under",
		"variable", "virtual", "visible",
		"wait", "while", "without"
	};
	
	private static final Pattern UPPER_CASE_PATTERN = Pattern.compile("[A-Z]");
	private static final Pattern LOWER_CASE_PATTERN = Pattern.compile("[a-z]");
	
	private static ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<Connection>();
	
	private Set<String> reservedIdentifierSet;
	private Pattern identifierPattern;
	
	private DatabaseMetaData getDatabaseMetaData() throws SQLException
	{
		return threadLocalConnection.get().getMetaData();
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#flush()
	 */
	public synchronized void flush() throws SQLException
	{
		DatabaseMetaData metaData = this.getDatabaseMetaData();
		
		this.reservedIdentifierSet = new HashSet<String>(Arrays.asList(SQL_92_RESERVED_WORDS));
		this.reservedIdentifierSet.addAll(Arrays.asList(metaData.getSQLKeywords().split(",")));
		
		this.identifierPattern = Pattern.compile("[\\w" + Pattern.quote(metaData.getExtraNameCharacters()) + "]+");
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#setConnection(java.sql.Connection)
	 */
	public void setConnection(Connection connection)
	{
		threadLocalConnection.set(connection);
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#getTables()
	 */
	public Map<String, Collection<String>> getTables() throws SQLException
	{
		Map<String, Collection<String>> schemaMap = new HashMap<String, Collection<String>>();
		
		ResultSet resultSet = this.getDatabaseMetaData().getTables(null, null, "%", new String[] { "TABLE" });
		
		while (resultSet.next())
		{
			String table = this.quote(resultSet.getString("TABLE_NAME"));
			String schema = this.quote(resultSet.getString("TABLE_SCHEM"));

			Collection<String> tables = schemaMap.get(schema);
			
			if (tables == null)
			{
				tables = new LinkedList<String>();
				
				schemaMap.put(schema, tables);
			}
			
			tables.add(table);
		}
		
		resultSet.close();
		
		return schemaMap;
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#getColumns(java.lang.String, java.lang.String)
	 */
	public Map<String, ColumnProperties> getColumns(String schema, String table) throws SQLException
	{
		Map<String, ColumnProperties> columnMap = new HashMap<String, ColumnProperties>();
		
		ResultSet resultSet = this.getDatabaseMetaData().getColumns(null, null, "%", "%");
		
		while (resultSet.next())
		{
			String name = resultSet.getString("COLUMN_NAME");
			int type = resultSet.getInt("DATA_TYPE");
			String nativeType = resultSet.getString("TYPE_NAME");
			
			String column = this.quote(name);
			
			columnMap.put(this.quote(name), new ColumnProperties(column, type, nativeType));
		}
		
		resultSet.close();
		
		return columnMap;
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#getPrimaryKey(java.lang.String, java.lang.String)
	 */
	public UniqueConstraint getPrimaryKey(String schema, String table) throws SQLException
	{
		UniqueConstraint constraint = null;

		ResultSet resultSet = this.getDatabaseMetaData().getPrimaryKeys(null, schema, table);
		
		while (resultSet.next())
		{
			String name = this.quote(resultSet.getString("PK_NAME"));

			if (constraint == null)
			{
				constraint = new UniqueConstraint(name, schema, table);
			}
			
			String column = this.quote(resultSet.getString("COLUMN_NAME"));
			
			constraint.getColumnList().add(column);
		}
		
		resultSet.close();
		
		return constraint;
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#getForeignKeyConstraints(java.lang.String, java.lang.String)
	 */
	public Collection<ForeignKeyConstraint> getForeignKeyConstraints(String schema, String table) throws SQLException
	{
		Map<String, ForeignKeyConstraint> foreignKeyMap = new HashMap<String, ForeignKeyConstraint>();
		
		ResultSet resultSet = this.getDatabaseMetaData().getImportedKeys(null, schema, table);
		
		while (resultSet.next())
		{
			String name = this.quote(resultSet.getString("FK_NAME"));
			
			ForeignKeyConstraint foreignKey = foreignKeyMap.get(name);
			
			if (foreignKey == null)
			{
				foreignKey = new ForeignKeyConstraint(name, schema, table);
				
				foreignKey.setForeignSchema(this.quote(resultSet.getString("PKTABLE_SCHEM")));
				foreignKey.setForeignTable(this.quote(resultSet.getString("PKTABLE_NAME")));
				foreignKey.setDeleteRule(resultSet.getInt("DELETE_RULE"));
				foreignKey.setUpdateRule(resultSet.getInt("UPDATE_RULE"));
				foreignKey.setDeferrability(resultSet.getInt("DEFERRABILITY"));
			}
			
			String column = this.quote(resultSet.getString("FKCOLUMN_NAME"));
			String foreignColumn = this.quote(resultSet.getString("PKCOLUMN_NAME"));

			foreignKey.getColumnList().add(column);
			foreignKey.getForeignColumnList().add(foreignColumn);
		}
		
		resultSet.close();
		
		return foreignKeyMap.values();
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#getUniqueConstraints(java.lang.String, java.lang.String)
	 */
	public Collection<UniqueConstraint> getUniqueConstraints(String schema, String table) throws SQLException
	{
		Map<String, UniqueConstraint> keyMap = new HashMap<String, UniqueConstraint>();
		
		ResultSet resultSet = this.getDatabaseMetaData().getIndexInfo(null, schema, table, true, false);
		
		while (resultSet.next())
		{
			if (resultSet.getInt("TYPE") == DatabaseMetaData.tableIndexStatistic) continue;
			
			String name = this.quote(resultSet.getString("INDEX_NAME"));
			
			UniqueConstraint key = keyMap.get(name);
			
			if (key == null)
			{
				key = new UniqueConstraint(name, schema, table);
				
				keyMap.put(name, key);
			}
			
			String column = resultSet.getString("COLUMN_NAME");
			
			key.getColumnList().add(column);
		}
		
		resultSet.close();
		
		return keyMap.values();
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#containsAutoIncrementColumn(java.lang.String, java.lang.String)
	 */
	public boolean containsAutoIncrementColumn(String qualifiedTable) throws SQLException
	{
		boolean autoIncrement = false;
		
		Statement statement = this.getDatabaseMetaData().getConnection().createStatement();
		
		ResultSet resultSet = statement.executeQuery("SELECT * FROM " + qualifiedTable + " WHERE 0=1");
		
		ResultSetMetaData metaData = resultSet.getMetaData();
		
		for (int i = 1; i <= metaData.getColumnCount(); ++i)
		{
			if (metaData.isAutoIncrement(i))
			{
				autoIncrement = true;
				break;
			}
		}
		
		resultSet.close();
		statement.close();
		
		return autoIncrement;
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#qualifyTableForDML(java.lang.String, java.lang.String)
	 */
	public String getQualifiedNameForDML(String schema, String table) throws SQLException
	{
		return this.getDatabaseMetaData().supportsSchemasInDataManipulation() ? schema + "." + table : table;
	}

	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#qualifyTableForDDL(java.lang.String, java.lang.String)
	 */
	public String getQualifiedNameForDDL(String schema, String table) throws SQLException
	{
		return this.getDatabaseMetaData().supportsSchemasInTableDefinitions() ? schema + "." + table : table;
	}
	
	/**
	 * @see net.sf.hajdbc.DatabaseMetaDataCache#supportsSelectForUpdate()
	 */
	public boolean supportsSelectForUpdate() throws SQLException
	{
		return this.getDatabaseMetaData().supportsSelectForUpdate();
	}

	private String quote(String identifier) throws SQLException
	{
		DatabaseMetaData metaData = this.getDatabaseMetaData();
		
		String quote = metaData.getIdentifierQuoteString();
		
		// Sometimes, drivers return identifiers already quoted.  If so, exit early.
		if (identifier.startsWith(quote)) return identifier;
		
		// Quote reserved identifiers
		boolean requiresQuoting = this.reservedIdentifierSet.contains(identifier.toLowerCase());
		
		// Quote identifers containing special characters
		requiresQuoting |= !this.identifierPattern.matcher(identifier).matches();
		
		// Quote mixed-case identifers if detected and supported by DBMS
		requiresQuoting |= !metaData.supportsMixedCaseIdentifiers() && metaData.supportsMixedCaseQuotedIdentifiers() && ((metaData.storesLowerCaseIdentifiers() && UPPER_CASE_PATTERN.matcher(identifier).find()) || (metaData.storesUpperCaseIdentifiers() && LOWER_CASE_PATTERN.matcher(identifier).find()));
		
		return requiresQuoting ? quote + identifier + quote : identifier;
	}
}
