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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class MeasureQuery {
  private final String analysisUuid;
  private final List<String> componentUuids;
  @CheckForNull
  private final Collection<Integer> metricIds;
  @CheckForNull
  private final Collection<String> metricKeys;
  @CheckForNull
  private final Long personId;

  private MeasureQuery(Builder builder) {
    this(builder.analysisUuid, builder.componentUuids, builder.metricIds, builder.metricKeys, builder.personId);
  }

  private MeasureQuery(@Nullable String analysisUuid,
    List<String> componentUuids,
    @Nullable Collection<Integer> metricIds,
    @Nullable Collection<String> metricKeys,
    @Nullable Long personId) {
    this.analysisUuid = analysisUuid;
    requireNonNull(componentUuids, "Component UUIDs must be set");
    checkState(metricIds == null || metricKeys == null, "Metric IDs and keys must not be set both");
    this.componentUuids = componentUuids;
    this.metricIds = metricIds;
    this.metricKeys = metricKeys;
    this.personId = personId;
  }

  public String getAnalysisUuid() {
    return analysisUuid;
  }

  public List<String> getComponentUuids() {
    return componentUuids;
  }

  @CheckForNull
  public Collection<Integer> getMetricIds() {
    return metricIds;
  }

  @CheckForNull
  public Collection<String> getMetricKeys() {
    return metricKeys;
  }

  @CheckForNull
  public Long getPersonId() {
    return personId;
  }

  public boolean returnsEmpty() {
    return componentUuids.isEmpty()
      || (metricIds != null && metricIds.isEmpty())
      || (metricKeys != null && metricKeys.isEmpty());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MeasureQuery that = (MeasureQuery) o;
    return Objects.equals(analysisUuid, that.analysisUuid) &&
        Objects.equals(componentUuids, that.componentUuids) &&
        Objects.equals(metricIds, that.metricIds) &&
        Objects.equals(metricKeys, that.metricKeys) &&
        Objects.equals(personId, that.personId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(analysisUuid, componentUuids, metricIds, metricKeys, personId);
  }

  public static Builder builder() {
    return new Builder();
  }

  static MeasureQuery copyWithSubsetOfComponentUuids(MeasureQuery query, List<String> componentUuids) {
    return new MeasureQuery(query.analysisUuid, componentUuids, query.metricIds, query.metricKeys, query.personId);
  }

  public static final class Builder {
    private String analysisUuid;
    private List<String> componentUuids;
    private Collection<Integer> metricIds;
    private Collection<String> metricKeys;
    private Long personId;

    private Builder() {
      // see MeasureQuery#builder()
    }

    public Builder setAnalysisUuid(String analysisUuid) {
      this.analysisUuid = analysisUuid;
      return this;
    }

    public Builder setComponentUuids(List<String> componentUuids) {
      this.componentUuids = componentUuids;
      return this;
    }

    public Builder setComponentUuid(String componentUuid) {
      this.componentUuids = singletonList(componentUuid);
      return this;
    }

    /**
     * All the measures are returned if parameter is {@code null}.
     */
    public Builder setMetricIds(@Nullable Collection<Integer> metricIds) {
      this.metricIds = metricIds;
      return this;
    }

    public Builder setMetricId(int metricId) {
      this.metricIds = singleton(metricId);
      return this;
    }

    /**
     * All the measures are returned if parameter is {@code null}.
     */
    public Builder setMetricKeys(@Nullable Collection<String> s) {
      this.metricKeys = s;
      return this;
    }

    public Builder setMetricKey(String s) {
      this.metricKeys = singleton(s);
      return this;
    }

    public Builder setPersonId(@Nullable Long l) {
      this.personId = l;
      return this;
    }

    public MeasureQuery build() {
      return new MeasureQuery(this);
    }
  }
}
