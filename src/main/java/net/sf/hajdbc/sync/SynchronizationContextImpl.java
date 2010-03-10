/*
 * HA-JDBC: High-Availability JDBC
 * Copyright 2004-2009 Paul Ferraro
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.hajdbc.sync;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.Dialect;
import net.sf.hajdbc.Messages;
import net.sf.hajdbc.SynchronizationContext;
import net.sf.hajdbc.balancer.Balancer;
import net.sf.hajdbc.cache.DatabaseMetaDataCache;
import net.sf.hajdbc.cache.DatabaseProperties;
import net.sf.hajdbc.logging.Level;
import net.sf.hajdbc.logging.Logger;
import net.sf.hajdbc.logging.LoggerFactory;

/**
 * @author Paul Ferraro
 * @param <D> Driver or DataSource
 */
public class SynchronizationContextImpl<Z, D extends Database<Z>> implements SynchronizationContext<Z, D>
{
	private static Logger logger = LoggerFactory.getLogger(SynchronizationContextImpl.class);
	
	private Set<D> activeDatabaseSet;
	private D sourceDatabase;
	private D targetDatabase;
	private DatabaseCluster<Z, D> cluster;
	private DatabaseProperties sourceDatabaseProperties;
	private DatabaseProperties targetDatabaseProperties;
	private Map<D, Connection> connectionMap = new HashMap<D, Connection>();
	private ExecutorService executor;
	
	/**
	 * @param cluster
	 * @param database
	 * @throws SQLException
	 */
	public SynchronizationContextImpl(DatabaseCluster<Z, D> cluster, D database) throws SQLException
	{
		this.cluster = cluster;
		
		Balancer<Z, D> balancer = cluster.getBalancer();
		
		this.sourceDatabase = balancer.next();
		
		if (this.sourceDatabase == null)
		{
			throw new SQLException(Messages.NO_ACTIVE_DATABASES.getMessage(cluster));
		}
		
		this.activeDatabaseSet = balancer;
		this.targetDatabase = database;
		this.executor = Executors.newFixedThreadPool(this.activeDatabaseSet.size(), this.cluster.getThreadFactory());
		
		DatabaseMetaDataCache<Z, D> cache = cluster.getDatabaseMetaDataCache();
		
		this.targetDatabaseProperties = cache.getDatabaseProperties(this.targetDatabase, this.getConnection(this.targetDatabase));
		this.sourceDatabaseProperties = cache.getDatabaseProperties(this.sourceDatabase, this.getConnection(this.sourceDatabase));
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getConnection(net.sf.hajdbc.Database)
	 */
	@Override
	public Connection getConnection(D database) throws SQLException
	{
		synchronized (this.connectionMap)
		{
			Connection connection = this.connectionMap.get(database);
			
			if (connection == null)
			{
				connection = database.connect(database.createConnectionSource(), this.cluster.getCodec());
				
				this.connectionMap.put(database, connection);
			}
			
			return connection;
		}
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getSourceDatabase()
	 */
	@Override
	public D getSourceDatabase()
	{
		return this.sourceDatabase;
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getTargetDatabase()
	 */
	@Override
	public D getTargetDatabase()
	{
		return this.targetDatabase;
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getActiveDatabaseSet()
	 */
	@Override
	public Set<D> getActiveDatabaseSet()
	{
		return this.activeDatabaseSet;
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getSourceDatabaseProperties()
	 */
	@Override
	public DatabaseProperties getSourceDatabaseProperties()
	{
		return this.sourceDatabaseProperties;
	}

	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getTargetDatabaseProperties()
	 */
	@Override
	public DatabaseProperties getTargetDatabaseProperties()
	{
		return this.targetDatabaseProperties;
	}

	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getDialect()
	 */
	@Override
	public Dialect getDialect()
	{
		return this.cluster.getDialect();
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationContext#getExecutor()
	 */
	@Override
	public ExecutorService getExecutor()
	{
		return this.executor;
	}

	/**
	 * @see net.sf.hajdbc.SynchronizationContext#close()
	 */
	@Override
	public void close()
	{
		synchronized (this.connectionMap)
		{
			for (Connection connection: this.connectionMap.values())
			{
				if (connection != null)
				{
					try
					{
						if (!connection.isClosed())
						{
							connection.close();
						}
					}
					catch (SQLException e)
					{
						logger.log(Level.WARN, e, e.toString());
					}
				}
			}
		}
		
		this.executor.shutdown();
	}
}