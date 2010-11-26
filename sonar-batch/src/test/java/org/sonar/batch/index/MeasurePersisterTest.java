/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource SA
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
package org.sonar.batch.index;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.sonar.api.database.model.MeasureModel;
import org.sonar.api.database.model.Snapshot;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.resources.JavaFile;
import org.sonar.api.resources.JavaPackage;
import org.sonar.api.resources.Project;
import org.sonar.jpa.test.AbstractDbUnitTestCase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MeasurePersisterTest extends AbstractDbUnitTestCase {

  private ResourcePersister resourcePersister;
  private MeasurePersister measurePersister;
  private Project project = new Project("foo");
  private JavaPackage aPackage = new JavaPackage("org.foo");
  private Snapshot projectSnapshot, packageSnapshot;
  private Metric ncloc, coverage;

  @Before
  public void mockResourcePersister() {
    setupData("shared");
    resourcePersister = mock(ResourcePersister.class);
    projectSnapshot = getSession().getSingleResult(Snapshot.class, "id", 3001);
    packageSnapshot = getSession().getSingleResult(Snapshot.class, "id", 3002);
    ncloc = getSession().getSingleResult(Metric.class, "key", "ncloc");
    coverage = getSession().getSingleResult(Metric.class, "key", "coverage");
    when(resourcePersister.saveResource((Project) anyObject(), eq(project))).thenReturn(projectSnapshot);
    when(resourcePersister.saveResource((Project) anyObject(), eq(aPackage))).thenReturn(packageSnapshot);
    measurePersister = new MeasurePersister(getSession(), resourcePersister);
  }

  @Test
  public void shouldInsertMeasure() {
    Measure measure = new Measure(ncloc).setValue(1234.0);

    measurePersister.saveMeasure(project, measure);

    checkTables("shouldInsertMeasure", "project_measures");
  }

  @Test
  public void shouldUpdateMeasure() {
    Measure measure = new Measure(coverage).setValue(12.5);
    measure.setId(1L);

    measurePersister.saveMeasure(project, measure);

    checkTables("shouldUpdateMeasure", "project_measures");
  }

  @Test
  @Ignore("to do")
  public void shouldInsertDataMeasure() {

  }

  @Test
  public void shouldDelaySaving() {
    measurePersister.setDelayedMode(true);

    measurePersister.saveMeasure(project, new Measure(ncloc).setValue(1234.0));
    measurePersister.saveMeasure(project, aPackage, new Measure(ncloc).setValue(50.0));

    assertThat(getSession().getResults(MeasureModel.class, "metricId", 1).size(), is(0));

    measurePersister.dump();
    checkTables("shouldDelaySaving", "project_measures");
  }

  @Test
  public void shouldNotDelaySavingWithDatabaseOnlyMeasure() {
    measurePersister.setDelayedMode(true);

    measurePersister.saveMeasure(project, new Measure(ncloc).setValue(1234.0).setPersistenceMode(PersistenceMode.DATABASE)); // database only
    measurePersister.saveMeasure(project, aPackage, new Measure(ncloc).setValue(50.0)); // database + memory

    // no dump => the db-only measure is saved

    checkTables("shouldNotDelaySavingWithDatabaseOnlyMeasure", "project_measures");
  }

  @Test
  public void shouldNotSaveBestValues() {
    JavaFile file = new JavaFile("org.foo.MyClass");

    Measure measure = new Measure(coverage).setValue(0.0);
    assertThat(MeasurePersister.shouldPersistMeasure(file, measure), is(true));

    measure = new Measure(coverage).setValue(75.8);
    assertThat(MeasurePersister.shouldPersistMeasure(file, measure), is(true));

    measure = new Measure(coverage).setValue(100.0);
    assertThat(MeasurePersister.shouldPersistMeasure(file, measure), is(false));
  }

  @Test
  public void shouldNotSaveMemoryOnlyMeasures() {
    Measure measure = new Measure("ncloc").setPersistenceMode(PersistenceMode.MEMORY);
    assertThat(MeasurePersister.shouldPersistMeasure(aPackage, measure), is(false));
  }
}