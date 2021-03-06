/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2011 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.cpd.index;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.dbunit.Assertion;
import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseUnitException;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.*;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.resources.JavaFile;
import org.sonar.api.resources.Resource;
import org.sonar.duplications.block.Block;
import org.sonar.duplications.block.ByteArray;
import org.sonar.persistence.InMemoryDatabase;
import org.sonar.persistence.MyBatis;
import org.sonar.persistence.dao.DuplicationDao;

/**
 * TODO Godin: would be better to split this test on two - one for DAO and one for DbDuplicationsIndex
 */
public class DbDuplicationsIndexTest {

  private static IDatabaseTester databaseTester;
  private static InMemoryDatabase database;
  private static DuplicationDao dao;

  private DbDuplicationsIndex index;

  @Before
  public void startDatabase() throws Exception {
    database = new InMemoryDatabase();
    MyBatis myBatis = new MyBatis(database);

    database.start();
    myBatis.start();

    dao = new DuplicationDao(myBatis);
    databaseTester = new DataSourceDatabaseTester(database.getDataSource());
  }

  @After
  public void stopDatabase() throws Exception {
    if (databaseTester != null) {
      databaseTester.onTearDown();
    }
    database.stop();
  }

  @Test
  public void shouldGetByHash() throws Exception {
    Resource resource = new JavaFile("foo");
    index = spy(new DbDuplicationsIndex(dao, null, 9, 7));
    doReturn(10).when(index).getSnapshotIdFor(resource);
    setupData("shouldGetByHash");

    index.prepareCache(resource);
    Collection<Block> blocks = index.getByHash(new ByteArray("aa"));
    Iterator<Block> blocksIterator = blocks.iterator();

    assertThat(blocks.size(), is(1));

    Block block = blocksIterator.next();
    assertThat("block resourceId", block.getResourceId(), is("bar-last"));
    assertThat("block hash", block.getBlockHash(), is(new ByteArray("aa")));
    assertThat("block index in file", block.getIndexInFile(), is(0));
    assertThat("block start line", block.getFirstLineNumber(), is(1));
    assertThat("block end line", block.getLastLineNumber(), is(2));

    // check null for lastSnapshotId
    index = spy(new DbDuplicationsIndex(dao, null, 9, null));
    doReturn(10).when(index).getSnapshotIdFor(resource);

    index.prepareCache(resource);
  }

  @Test
  public void shouldInsert() throws Exception {
    Resource resource = new JavaFile("foo");
    index = spy(new DbDuplicationsIndex(dao, null, 1, null));
    doReturn(2).when(index).getSnapshotIdFor(resource);
    setupData("shouldInsert");

    index.insert(resource, Arrays.asList(new Block("foo", new ByteArray("bb"), 0, 1, 2)));

    checkTables("shouldInsert", "duplications_index");
  }

  // ============================================================
  // TODO Godin: a kind of copy-paste from AbstractDbUnitTestCase

  private final void setupData(String... testNames) {
    InputStream[] streams = new InputStream[testNames.length];
    try {
      for (int i = 0; i < testNames.length; i++) {
        String className = getClass().getName();
        className = String.format("/%s/%s.xml", className.replace(".", "/"), testNames[i]);
        streams[i] = getClass().getResourceAsStream(className);
        if (streams[i] == null) {
          throw new RuntimeException("Test not found :" + className);
        }
      }

      setupData(streams);

    } finally {
      for (InputStream stream : streams) {
        IOUtils.closeQuietly(stream);
      }
    }
  }

  private final void setupData(InputStream... dataSetStream) {
    try {
      IDataSet[] dataSets = new IDataSet[dataSetStream.length];
      for (int i = 0; i < dataSetStream.length; i++) {
        ReplacementDataSet dataSet = new ReplacementDataSet(new FlatXmlDataSet(dataSetStream[i]));
        dataSet.addReplacementObject("[null]", null);
        dataSets[i] = dataSet;
      }
      CompositeDataSet compositeDataSet = new CompositeDataSet(dataSets);

      databaseTester.setDataSet(compositeDataSet);
      IDatabaseConnection connection = databaseTester.getConnection();

      DatabaseOperation.CLEAN_INSERT.execute(connection, databaseTester.getDataSet());

      connection.getConnection().commit();
      connection.close();
    } catch (Exception e) {
      throw translateException("Could not setup DBUnit data", e);
    }
  }

  private final void checkTables(String testName, String... tables) {
    checkTables(testName, new String[] {}, tables);
  }

  private final void checkTables(String testName, String[] excludedColumnNames, String... tables) {
    // getSession().commit();
    try {
      IDataSet dataSet = getCurrentDataSet();
      IDataSet expectedDataSet = getExpectedData(testName);
      for (String table : tables) {
        ITable filteredTable = DefaultColumnFilter.excludedColumnsTable(dataSet.getTable(table), excludedColumnNames);
        Assertion.assertEquals(expectedDataSet.getTable(table), filteredTable);
      }
    } catch (DataSetException e) {
      throw translateException("Error while checking results", e);
    } catch (DatabaseUnitException e) {
      fail(e.getMessage());
    }
  }

  private final IDataSet getExpectedData(String testName) {
    String className = getClass().getName();
    className = String.format("/%s/%s-result.xml", className.replace(".", "/"), testName);

    InputStream in = getClass().getResourceAsStream(className);
    try {
      return getData(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
  }

  private final IDataSet getData(InputStream stream) {
    try {
      ReplacementDataSet dataSet = new ReplacementDataSet(new FlatXmlDataSet(stream));
      dataSet.addReplacementObject("[null]", null);
      return dataSet;
    } catch (Exception e) {
      throw translateException("Could not read the dataset stream", e);
    }
  }

  private final IDataSet getCurrentDataSet() {
    try {
      IDatabaseConnection connection = databaseTester.getConnection();
      return connection.createDataSet();
    } catch (Exception e) {
      throw translateException("Could not create the current dataset", e);
    }
  }

  private static RuntimeException translateException(String msg, Exception cause) {
    RuntimeException runtimeException = new RuntimeException(String.format("%s: [%s] %s", msg, cause.getClass().getName(), cause.getMessage()));
    runtimeException.setStackTrace(cause.getStackTrace());
    return runtimeException;
  }
}
