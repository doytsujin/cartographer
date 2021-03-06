/**
 * Copyright (C) 2012 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.cartographer.graph.filter;

import static org.apache.commons.lang.StringUtils.join;

import java.util.List;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.rel.RelationshipType;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

public class ExcludingFilter
    implements ProjectRelationshipFilter
{

    private static final long serialVersionUID = 1L;

    private final List<ProjectVersionRef> excludedSubgraphs;

    private final ProjectRelationshipFilter filter;

    private transient String longId;

    private transient String shortId;

    public ExcludingFilter( final List<ProjectVersionRef> excludedSubgraphs, final ProjectRelationshipFilter filter )
    {
        this.excludedSubgraphs = excludedSubgraphs;
        this.filter = filter;
    }

    @Override
    public boolean accept( final ProjectRelationship<?, ?> rel )
    {
        final ProjectVersionRef target = rel.getTarget()
                                            .asProjectVersionRef();
        return !excludedSubgraphs.contains( target ) && filter.accept( rel );
    }

    @Override
    public ProjectRelationshipFilter getChildFilter( final ProjectRelationship<?, ?> parent )
    {
        ProjectRelationshipFilter childfilter = filter.getChildFilter( parent );
        if ( childfilter == filter )
        {
            return this;
        }
        else
        {
            return new ExcludingFilter( excludedSubgraphs, childfilter );
        }
    }

    @Override
    public Set<ProjectRef> getDepExcludes()
    {
        return filter.getDepExcludes();
    }

    @Override
    public String getLongId()
    {
        if ( longId == null )
        {
            longId =
                "Excluded-Subgraphs [" + join( excludedSubgraphs, "\n" ) + "], delegating to: " + filter.getLongId();
        }
        return longId;
    }

    @Override
    public String getCondensedId()
    {
        if ( shortId == null )
        {
            shortId = DigestUtils.shaHex( getLongId() );
        }

        return shortId;
    }

    @Override
    public boolean includeManagedRelationships()
    {
        return filter.includeManagedRelationships();
    }

    @Override
    public boolean includeConcreteRelationships()
    {
        return filter.includeConcreteRelationships();
    }

    @Override
    public Set<RelationshipType> getAllowedTypes()
    {
        return filter.getAllowedTypes();
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
        final ExcludingFilter other = (ExcludingFilter) obj;
        if ( excludedSubgraphs == null )
        {
            if ( other.excludedSubgraphs != null )
            {
                return false;
            }
        }
        else if ( !excludedSubgraphs.equals( other.excludedSubgraphs ) )
        {
            return false;
        }
        if ( filter == null )
        {
            if ( other.filter != null )
            {
                return false;
            }
        }
        else if ( !filter.equals( other.filter ) )
        {
            return false;
        }
        return true;

    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( excludedSubgraphs == null ) ? 0 : excludedSubgraphs.hashCode() );
        result = prime * result + ( ( filter == null ) ? 0 : filter.hashCode() );
        return result;
    }

}
