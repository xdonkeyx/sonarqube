/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.measure;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;

import static org.sonar.db.DatabaseUtils.executeLargeInputs;

public class MeasureDao implements Dao {

  public Optional<MeasureDto> selectSingle(DbSession dbSession, MeasureQuery query) {
    List<MeasureDto> measures = selectByQuery(dbSession, query);
    return Optional.ofNullable(Iterables.getOnlyElement(measures, null));
  }

  public List<MeasureDto> selectByQuery(DbSession dbSession, MeasureQuery query) {
    if (query.returnsEmpty()) {
      return Collections.emptyList();
    }
    if (query.getComponentUuids() == null) {
      return mapper(dbSession).selectByQuery(query);
    }
    return executeLargeInputs(query.getComponentUuids(), componentUuids -> {
      MeasureQuery pageQuery = MeasureQuery.copyWithSubsetOfComponentUuids(query, componentUuids);
      return mapper(dbSession).selectByQuery(pageQuery);
    });
  }

  public List<PastMeasureDto> selectPastMeasures(DbSession dbSession,
    String componentUuid,
    String analysisUuid,
    Collection<Integer> metricIds) {
    return executeLargeInputs(
      metricIds,
      ids -> mapper(dbSession).selectPastMeasures(componentUuid, analysisUuid, ids));
  }

  public void insert(DbSession session, MeasureDto measureDto) {
    mapper(session).insert(measureDto);
  }

  public void insert(DbSession session, Collection<MeasureDto> items) {
    for (MeasureDto item : items) {
      insert(session, item);
    }
  }

  public void insert(DbSession session, MeasureDto item, MeasureDto... others) {
    insert(session, Lists.asList(item, others));
  }

  private static MeasureMapper mapper(DbSession session) {
    return session.getMapper(MeasureMapper.class);
  }
}
