/**
 * Copyright (C) 2013 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.cartographer.INTERNAL.graph.agg;

import org.commonjava.cartographer.graph.RelationshipGraph;
import org.commonjava.cartographer.graph.model.GraphPath;
import org.commonjava.cartographer.graph.model.GraphPathInfo;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class DiscoveryTodo
{
    private final ProjectVersionRef ref;

    private Map<GraphPath<?>, GraphPathInfo> parentPaths;

    private RelationshipGraph graph;

    private Set<ProjectRef> depExcludes;

    public DiscoveryTodo( final ProjectVersionRef ref )
    {
        this.ref = ref;
    }

    public DiscoveryTodo( final ProjectVersionRef ref, final GraphPath<?> path, final GraphPathInfo pathInfo,
                          final RelationshipGraph graph, final Set<ProjectRef> depExcludes )
    {
        this.ref = ref;
        this.graph = graph;
        this.parentPaths = new HashMap<GraphPath<?>, GraphPathInfo>();
        this.depExcludes = depExcludes;
        parentPaths.put( path, pathInfo );
    }

    public RelationshipGraph getGraph()
    {
        return graph;
    }

    public ProjectVersionRef getRef()
    {
        return ref;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( ref == null ) ? 0 : ref.hashCode() );
        result = prime * result + ( ( depExcludes == null ) ? 0 : depExcludes.hashCode() );
        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final DiscoveryTodo other = (DiscoveryTodo) obj;
        if ( ref == null )
        {
            if ( other.ref != null )
            {
                return false;
            }
        }
        else if ( !ref.equals( other.ref ) )
        {
            return false;
        }
        if ( depExcludes == null )
        {
            if ( other.depExcludes != null )
            {
                return false;
            }
        }
        else if ( !depExcludes.equals( other.depExcludes ) )
        {
            return false;
        }

        return true;
    }

    @Override
    public String toString()
    {
        return String.format( "DiscoveryTodo [ref=%s, parent-paths: %s]", ref, parentPaths );
    }

    public Map<GraphPath<?>, GraphPathInfo> getParentPathMap()
    {
        return parentPaths;
    }

    public void setParentPathMap( final Map<GraphPath<?>, GraphPathInfo> paths )
    {
        this.parentPaths = paths;
    }

    public void addParentPath( final GraphPath<?> path, final GraphPathInfo pathInfo )
    {
        if ( parentPaths == null )
        {
            parentPaths = new HashMap<GraphPath<?>, GraphPathInfo>();
        }

        parentPaths.put( path, pathInfo );
    }

    public Set<ProjectRef> getDepExcludes()
    {
        return depExcludes == null ? Collections.emptySet() : depExcludes;
    }

}
