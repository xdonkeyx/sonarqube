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
package org.sonar.scanner.index;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.design.Dependency;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.MeasuresFilter;
import org.sonar.api.measures.MeasuresFilters;
import org.sonar.api.resources.File;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.core.component.ComponentKeys;
import org.sonar.scanner.DefaultProjectTree;
import org.sonar.scanner.scan.measure.MeasureCache;
import org.sonar.scanner.sensor.DefaultSensorStorage;

public class DefaultIndex {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultIndex.class);

  private final BatchComponentCache componentCache;
  private final MeasureCache measureCache;
  private final DefaultProjectTree projectTree;
  // caches
  private DefaultSensorStorage sensorStorage;
  private Project currentProject;
  private Map<Resource, Bucket> buckets = Maps.newLinkedHashMap();

  public DefaultIndex(BatchComponentCache componentCache, DefaultProjectTree projectTree, MeasureCache measureCache) {
    this.componentCache = componentCache;
    this.projectTree = projectTree;
    this.measureCache = measureCache;
  }

  public void start() {
    Project rootProject = projectTree.getRootProject();
    if (StringUtils.isNotBlank(rootProject.getKey())) {
      doStart(rootProject);
    }
  }

  void doStart(Project rootProject) {
    Bucket bucket = new Bucket(rootProject);
    addBucket(rootProject, bucket);
    BatchComponent component = componentCache.add(rootProject, null);
    component.setInputComponent(new DefaultInputModule(rootProject.getEffectiveKey()));
    currentProject = rootProject;

    for (Project module : rootProject.getModules()) {
      addModule(rootProject, module);
    }
  }

  private void addBucket(Resource resource, Bucket bucket) {
    buckets.put(resource, bucket);
  }

  private void addModule(Project parent, Project module) {
    ProjectDefinition parentDefinition = projectTree.getProjectDefinition(parent);
    java.io.File parentBaseDir = parentDefinition.getBaseDir();
    ProjectDefinition moduleDefinition = projectTree.getProjectDefinition(module);
    java.io.File moduleBaseDir = moduleDefinition.getBaseDir();
    module.setPath(new PathResolver().relativePath(parentBaseDir, moduleBaseDir));
    addResource(module);
    for (Project submodule : module.getModules()) {
      addModule(module, submodule);
    }
  }

  public Project getProject() {
    return currentProject;
  }

  public void setCurrentProject(Project project, DefaultSensorStorage sensorStorage) {
    this.currentProject = project;

    // the following components depend on the current module, so they need to be reloaded.
    this.sensorStorage = sensorStorage;
  }

  /**
   * Keep only project stuff
   */
  public void clear() {
    Iterator<Map.Entry<Resource, Bucket>> it = buckets.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Resource, Bucket> entry = it.next();
      Resource resource = entry.getKey();
      if (!ResourceUtils.isSet(resource)) {
        entry.getValue().clear();
        it.remove();
      }

    }
  }

  @CheckForNull
  public Measure getMeasure(Resource resource, org.sonar.api.batch.measure.Metric<?> metric) {
    return getMeasures(resource, MeasuresFilters.metric(metric));
  }

  @CheckForNull
  public <M> M getMeasures(Resource resource, MeasuresFilter<M> filter) {
    // Reload resource so that effective key is populated
    Resource indexedResource = getResource(resource);
    if (indexedResource == null) {
      return null;
    }
    Collection<Measure> unfiltered = new ArrayList<>();
    if (filter instanceof MeasuresFilters.MetricFilter) {
      // optimization
      Measure byMetric = measureCache.byMetric(indexedResource, ((MeasuresFilters.MetricFilter<M>) filter).filterOnMetricKey());
      if (byMetric != null) {
        unfiltered.add(byMetric);
      }
    } else {
      for (Measure measure : measureCache.byResource(indexedResource)) {
        unfiltered.add(measure);
      }
    }
    return filter.filter(unfiltered);
  }

  public Measure addMeasure(Resource resource, Measure measure) {
    Bucket bucket = getBucket(resource);
    if (bucket != null) {
      return sensorStorage.saveMeasure(resource, measure);
    }
    return measure;
  }

  public Dependency addDependency(Dependency dependency) {
    return dependency;
  }

  public Set<Resource> getResources() {
    return buckets.keySet();
  }

  public String getSource(Resource reference) {
    Resource resource = getResource(reference);
    if (resource instanceof File) {
      File file = (File) resource;
      Project module = currentProject;
      ProjectDefinition def = projectTree.getProjectDefinition(module);
      try {
        return FileUtils.readFileToString(new java.io.File(def.getBaseDir(), file.getPath()));
      } catch (IOException e) {
        throw new IllegalStateException("Unable to read file content " + reference, e);
      }
    }
    return null;
  }

  /**
   * Does nothing if the resource is already registered.
   */
  public Resource addResource(Resource resource) {
    Bucket bucket = doIndex(resource);
    return bucket != null ? bucket.getResource() : null;
  }

  @CheckForNull
  public <R extends Resource> R getResource(@Nullable R reference) {
    Bucket bucket = getBucket(reference);
    if (bucket != null) {
      return (R) bucket.getResource();
    }
    return null;
  }

  public List<Resource> getChildren(Resource resource) {
    List<Resource> children = Lists.newLinkedList();
    Bucket bucket = getBucket(resource);
    if (bucket != null) {
      for (Bucket childBucket : bucket.getChildren()) {
        children.add(childBucket.getResource());
      }
    }
    return children;
  }

  public Resource getParent(Resource resource) {
    Bucket bucket = getBucket(resource);
    if (bucket != null && bucket.getParent() != null) {
      return bucket.getParent().getResource();
    }
    return null;
  }

  public boolean index(Resource resource) {
    Bucket bucket = doIndex(resource);
    return bucket != null;
  }

  private Bucket doIndex(Resource resource) {
    if (resource.getParent() != null) {
      doIndex(resource.getParent());
    }
    return doIndex(resource, resource.getParent());
  }

  public boolean index(Resource resource, Resource parentReference) {
    Bucket bucket = doIndex(resource, parentReference);
    return bucket != null;
  }

  private Bucket doIndex(Resource resource, @Nullable Resource parentReference) {
    Bucket bucket = getBucket(resource);
    if (bucket != null) {
      return bucket;
    }

    if (StringUtils.isBlank(resource.getKey())) {
      LOG.warn("Unable to index a resource without key " + resource);
      return null;
    }

    Resource parent = (Resource) ObjectUtils.defaultIfNull(parentReference, currentProject);

    Bucket parentBucket = getBucket(parent);
    if (parentBucket == null && parent != null) {
      LOG.warn("Resource ignored, parent is not indexed: " + resource);
      return null;
    }

    if (ResourceUtils.isProject(resource) || /* For technical projects */ResourceUtils.isRootProject(resource)) {
      resource.setEffectiveKey(resource.getKey());
    } else {
      resource.setEffectiveKey(ComponentKeys.createEffectiveKey(currentProject, resource));
    }
    bucket = new Bucket(resource).setParent(parentBucket);
    addBucket(resource, bucket);

    Resource parentResource = parentBucket != null ? parentBucket.getResource() : null;
    BatchComponent component = componentCache.add(resource, parentResource);
    if (ResourceUtils.isProject(resource)) {
      component.setInputComponent(new DefaultInputModule(resource.getEffectiveKey()));
    }

    return bucket;
  }

  public boolean isExcluded(@Nullable Resource reference) {
    return false;
  }

  public boolean isIndexed(@Nullable Resource reference, boolean acceptExcluded) {
    return getBucket(reference) != null;
  }

  private Bucket getBucket(@Nullable Resource reference) {
    if (reference == null) {
      return null;
    }
    if (StringUtils.isNotBlank(reference.getKey())) {
      return buckets.get(reference);
    }
    return null;
  }

}
