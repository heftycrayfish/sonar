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
package org.sonar.server.filters;

import org.apache.commons.collections.comparators.ReverseComparator;

import java.io.Serializable;
import java.util.*;

public class FilterResult {
  private List<Object[]> rows;
  private Filter filter;
  public static final int SORTED_COLUMN_INDEX = 3;

  public FilterResult(Filter filter, List rows) {
    this.rows = new ArrayList(rows);
    this.filter = filter;
  }

  /**
   * @return a list of arrays
   */
  public List getRows() {
    return rows;
  }

  public int size() {
    return rows.size();
  }

  public Integer getSnapshotId(Object row) {
    return (Integer) ((Object[]) row)[getSnapshotIdIndex()];
  }

  public Integer getProjectId(Object row) {
    return (Integer) ((Object[]) row)[getProjectIdIndex()];
  }

  public Integer getRootProjectId(Object row) {
    return (Integer) ((Object[]) row)[getRootProjectIdIndex()];
  }

  public int getSnapshotIdIndex() {
    return 0;
  }

  public int getProjectIdIndex() {
    return 1;
  }

  public int getRootProjectIdIndex() {
    return 2;
  }

  public void sort() {
    if (filter.isSorted()) {
      Comparator comparator = (filter.isTextSort() ? new StringIgnoreCaseComparator(SORTED_COLUMN_INDEX) : new NumericComparator(SORTED_COLUMN_INDEX));
      if (!filter.isAscendingSort()) {
        comparator = new ReverseComparator(comparator);
      }
      Collections.sort(rows, comparator);
    }
  }

  public void removeUnvalidRows() {
    int numberOfCriteria = filter.getMeasureCriteria().size();
    if (numberOfCriteria > 0) {
      int fromColumnIndex = (filter.isSorted() ? SORTED_COLUMN_INDEX + 1 : SORTED_COLUMN_INDEX);
      for (Iterator<Object[]> it = rows.iterator(); it.hasNext(); ) {
        Object[] row = it.next();
        boolean remove = false;
        for (int index = 0; index < numberOfCriteria; index++) {
          if (row[fromColumnIndex + index] == null) {
            remove = true;
          }
        }
        if (remove) {
          it.remove();
        }
      }
    }
  }

  static final class NumericComparator implements Comparator, Serializable {
    private static final long serialVersionUID = 4627704879575964978L;
    private int index;

    NumericComparator(int index) {
      this.index = index;
    }

    public int compare(Object a1, Object a2) {
      Comparable c1 = (Comparable) ((Object[]) a1)[index];
      Object o2 = ((Object[]) a2)[index];
      return (c1 == null ? -1 : (o2 == null ? 1 : c1.compareTo(o2)));
    }
  }

  static final class StringIgnoreCaseComparator implements Comparator, Serializable {
    private int index;

    StringIgnoreCaseComparator(int index) {
      this.index = index;
    }

    public int compare(Object o1, Object o2) {
      String s1 = (String)((Object[]) o1)[index];
      if (s1 == null) {
        return -1;
      }
      String s2 = (String)((Object[]) o2)[index];
      if (s2 == null) {
        return 1;
      }
      return s1.compareToIgnoreCase(s2);
    }
  }
}

