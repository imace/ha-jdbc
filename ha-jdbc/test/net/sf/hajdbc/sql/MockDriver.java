/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (C) 2005 Paul Ferraro
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
package net.sf.hajdbc.sql;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Mock driver that creates mock connections
 * @author  Paul Ferraro
 * @since   1.1
 */
public class MockDriver implements Driver
{
	/**
	 * @see java.sql.Driver#connect(java.lang.String, java.util.Properties)
	 */
	public Connection connect(String url, Properties properties) throws SQLException
	{
		return new MockConnection();
	}

	/**
	 * @see java.sql.Driver#acceptsURL(java.lang.String)
	 */
	public boolean acceptsURL(String url) throws SQLException
	{
		return url.startsWith("jdbc:mock:");
	}

	/**
	 * @see java.sql.Driver#getPropertyInfo(java.lang.String, java.util.Properties)
	 */
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) throws SQLException
	{
		return new DriverPropertyInfo[0];
	}

	/**
	 * @see java.sql.Driver#getMajorVersion()
	 */
	public int getMajorVersion()
	{
		return 0;
	}

	/**
	 * @see java.sql.Driver#getMinorVersion()
	 */
	public int getMinorVersion()
	{
		return 0;
	}

	/**
	 * @see java.sql.Driver#jdbcCompliant()
	 */
	public boolean jdbcCompliant()
	{
		return false;
	}
}
