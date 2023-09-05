// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.datasource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.password.PasswordResolver;
import org.xipki.password.PasswordResolverException;
import org.xipki.util.Args;
import org.xipki.util.FileOrValue;
import org.xipki.util.IoUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;

/**
 * Factory to create {@link DataSourceWrapper}.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class DataSourceFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DataSourceFactory.class);

  public DataSourceWrapper createDataSource(String name, FileOrValue conf, PasswordResolver passwordResolver)
      throws PasswordResolverException, IOException {
    Args.notNull(conf, "conf");

    PropertiesConfiguration props = new PropertiesConfiguration();
    try (Reader reader = new StringReader(conf.readContent())) {
      props.load(reader);
    } catch (ConfigurationException e) {
        throw new IOException(e);
    }

      return createDataSource(name, props, passwordResolver);
  } // method createDataSource

  public DataSourceWrapper createDataSource(String name, InputStream conf, PasswordResolver passwordResolver)
      throws PasswordResolverException, IOException {
    Args.notNull(conf, "conf");
    PropertiesConfiguration config = new PropertiesConfiguration();
    try {
      config.load(conf);
    } catch (ConfigurationException e) {
        throw new IOException(e);
    } finally {
      try {
        conf.close();
      } catch (Exception ex) {
        LOG.error("could not close stream: {}", ex.getMessage());
      }
    }

    return createDataSource(name, config, passwordResolver);
  } // method createDataSource

  public DataSourceWrapper createDataSource(String name, PropertiesConfiguration conf, PasswordResolver passwordResolver)
      throws PasswordResolverException {
    Args.notNull(conf, "conf");
    DatabaseType databaseType;
    String className = String.valueOf(conf.getProperty("dataSourceClassName"));
    if (className != null) {
      databaseType = DatabaseType.forDataSourceClass(className);
    } else {
      className = String.valueOf(conf.getProperty("driverClassName"));
      if (className != null) {
        databaseType = DatabaseType.forDriver(className);
      } else {
        String jdbcUrl = String.valueOf(conf.getProperty("jdbcUrl"));
        if (jdbcUrl == null) {
          throw new IllegalArgumentException("none of the properties dataSourceClassName"
              + ", driverClassName and jdbcUrl is configured");
        }

        databaseType = DatabaseType.forJdbcUrl(jdbcUrl);
      }
    }

    String password = String.valueOf(conf.getProperty("password"));
    if (password != null) {
      if (passwordResolver != null) {
        password = new String(passwordResolver.resolvePassword(password));
      }
      conf.setProperty("password", password);
    }

    password = String.valueOf(conf.getProperty("dataSource.password"));
    if (password != null) {
      if (passwordResolver != null) {
        password = new String(passwordResolver.resolvePassword(password));
      }
      conf.setProperty("dataSource.password", password);
    }

    /*
     * Expand the file path like
     *   dataSource.url = jdbc:h2:~/xipki/db/h2/ocspcrl
     *   dataSource.url = jdbc:hsqldb:file:~/xipki/db/hsqldb/ocspcache;sql.syntax_pgs=true
     */
    String dataSourceUrl = String.valueOf(conf.getProperty("dataSource.url"));
    if (dataSourceUrl != null) {
      String newUrl = null;

      final String h2_prefix = "jdbc:h2:";
      final String hsqldb_prefix = "jdbc:hsqldb:file:";

      if (dataSourceUrl.startsWith(h2_prefix + "~")) {
        newUrl = h2_prefix + IoUtil.expandFilepath(dataSourceUrl.substring(h2_prefix.length()));
      } else if (dataSourceUrl.startsWith(hsqldb_prefix + "~")) {
        newUrl = hsqldb_prefix + IoUtil.expandFilepath(dataSourceUrl.substring(hsqldb_prefix.length()));
      }
      if (newUrl != null) {
        conf.setProperty("dataSource.url", newUrl);
      }
    }

    Iterator<String> keys = conf.getKeys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (key.startsWith("sqlscript.") || key.startsWith("liquibase.")) {
        conf.clearProperty(key);
      }
    }

    return DataSourceWrapper.createDataSource(name, conf, databaseType);
  } // method createDataSource

  public DataSourceWrapper createDataSourceForFile(String name, String confFile, PasswordResolver passwordResolver)
      throws PasswordResolverException, IOException {
    Args.notBlank(confFile, "confFile");
    InputStream fileIn = Files.newInputStream(Paths.get(IoUtil.expandFilepath(confFile)));
    return createDataSource(name, fileIn, passwordResolver);
  }

}
