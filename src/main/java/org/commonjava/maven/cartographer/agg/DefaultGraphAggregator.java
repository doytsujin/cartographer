/*******************************************************************************
 * Copyright (C) 2014 John Casey.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.commonjava.maven.cartographer.agg;

import static org.apache.commons.lang.StringUtils.join;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.commonjava.cdi.util.weft.ExecutorConfig;
import org.commonjava.maven.atlas.graph.filter.ProjectRelationshipFilter;
import org.commonjava.maven.atlas.graph.model.EProjectCycle;
import org.commonjava.maven.atlas.graph.model.EProjectGraph;
import org.commonjava.maven.atlas.graph.model.EProjectNet;
import org.commonjava.maven.atlas.graph.model.EProjectWeb;
import org.commonjava.maven.atlas.graph.model.GraphView;
import org.commonjava.maven.atlas.graph.rel.ParentRelationship;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.rel.RelationshipType;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.data.CartoDataManager;
import org.commonjava.maven.cartographer.discover.DiscoveryResult;
import org.commonjava.maven.cartographer.discover.ProjectRelationshipDiscoverer;
import org.commonjava.util.logging.Logger;
import org.commonjava.util.logging.helper.JoinString;

@ApplicationScoped
public class DefaultGraphAggregator
    implements GraphAggregator
{

    private final Logger logger = new Logger( getClass() );

    @Inject
    private CartoDataManager dataManager;

    @Inject
    private ProjectRelationshipDiscoverer discoverer;

    @Inject
    @ExecutorConfig( daemon = true, named = "carto-aggregator", priority = 9, threads = 8 )
    private ExecutorService executor;

    protected DefaultGraphAggregator()
    {
    }

    public DefaultGraphAggregator( final CartoDataManager dataManager, final ProjectRelationshipDiscoverer discoverer, final ExecutorService executor )
    {
        this.dataManager = dataManager;
        this.discoverer = discoverer;
        this.executor = executor;
    }

    @Override
    public EProjectGraph connectIncomplete( final EProjectGraph graph, final AggregationOptions config )
        throws CartoDataException
    {
        return connect( graph, config );
    }

    @Override
    public EProjectNet connectIncomplete( final EProjectWeb web, final AggregationOptions config )
        throws CartoDataException
    {
        return connect( web, config );
    }

    private <T extends EProjectNet> T connect( final T net, final AggregationOptions config )
        throws CartoDataException
    {
        if ( net != null )
        {
            if ( config.isDiscoveryEnabled() )
            {
                final LinkedList<EProjectNet> nets = new LinkedList<EProjectNet>();
                nets.add( net );

                final Set<ProjectVersionRef> missing = new HashSet<ProjectVersionRef>();

                logger.debug( "Loading existing cycle participants..." );
                final Set<ProjectVersionRef> cycleParticipants = loadExistingCycleParticipants( net );

                logger.debug( "Loading initial set of GAVs to be resolved..." );
                final LinkedList<DiscoveryTodo> pending = loadInitialPending( net );
                final HashSet<DiscoveryTodo> done = new HashSet<DiscoveryTodo>();

                int pass = 0;
                while ( !pending.isEmpty() )
                {
                    final HashSet<DiscoveryTodo> current = new HashSet<DiscoveryTodo>( pending );
                    done.addAll( current );

                    logger.info( "%d. Next batch of TODOs:\n  %s", pass, new JoinString( "\n  ", current ) );
                    pending.clear();

                    final Set<DiscoveryTodo> newTodos = discover( current, config, cycleParticipants, missing, net, pass );
                    if ( newTodos != null )
                    {
                        newTodos.removeAll( done );
                        logger.info( "%d. Uncovered new batch of TODOs:\n  %s", pass, new JoinString( "\n  ", newTodos ) );

                        pending.addAll( newTodos );
                    }

                    pass++;
                }
            }
        }

        return net;
    }

    private Set<DiscoveryTodo> discover( final Set<DiscoveryTodo> todos, final AggregationOptions config,
                                         final Set<ProjectVersionRef> cycleParticipants, final Set<ProjectVersionRef> missing, final EProjectNet net,
                                         final int pass )
        throws CartoDataException
    {
        logger.debug( "%d. Performing discovery and cycle-detection on %d missing subgraphs:\n  %s", pass, todos.size(), new JoinString( "\n  ",
                                                                                                                                         todos ) );

        final Set<DiscoveryRunnable> runnables = new HashSet<DiscoveryRunnable>( todos.size() );

        final Set<ProjectVersionRef> roMissing = Collections.unmodifiableSet( missing );
        int idx = 0;
        for ( final DiscoveryTodo todo : todos )
        {
            final ProjectVersionRef todoRef = todo.getRef();

            if ( missing.contains( todoRef ) )
            {
                logger.info( "%d.%d. Skipping missing reference: %s", pass, idx++, todoRef );
                continue;
            }
            else if ( cycleParticipants.contains( todoRef ) )
            {
                logger.info( "%d.%d. Skipping cycle-participant reference: %s", pass, idx++, todoRef );
                continue;
            }
            else if ( net.containsGraph( todoRef ) )
            {
                logger.info( "%d.%d. Skipping already-discovered reference: %s", pass, idx++, todoRef );
                continue;
            }

            //            logger.info( "DISCOVER += %s", todo );
            final DiscoveryRunnable runnable = new DiscoveryRunnable( todo, config, roMissing, discoverer, false, pass, idx );
            runnables.add( runnable );
            idx++;
        }

        final CountDownLatch latch = new CountDownLatch( runnables.size() );
        for ( final DiscoveryRunnable runnable : runnables )
        {
            runnable.setLatch( latch );
            executor.execute( runnable );
        }

        try
        {
            latch.await();
        }
        catch ( final InterruptedException e )
        {
            logger.error( "Interrupted on subgraph discovery." );
            return null;
        }

        logger.info( "%d. Accounting for discovery results. Before discovery, these were missing:\n\n  %s\n\n", pass,
                     new JoinString( "\n  ", missing ) );

        final Set<ProjectRelationship<?>> newRels = new HashSet<ProjectRelationship<?>>();
        final Map<ProjectVersionRef, Set<ProjectRelationshipFilter>> newTargets = new HashMap<ProjectVersionRef, Set<ProjectRelationshipFilter>>();
        for ( final DiscoveryRunnable r : runnables )
        {
            final DiscoveryResult result = r.getResult();
            final DiscoveryTodo todo = r.getTodo();

            if ( result != null )
            {
                final Set<ProjectRelationshipFilter> filters = todo.getFilters();

                final Map<String, String> metadata = result.getMetadata();
                if ( metadata != null )
                {
                    dataManager.addMetadata( result.getSelectedRef(), metadata );
                }

                final Set<ProjectRelationship<?>> discoveredRels = result.getAcceptedRelationships();
                if ( discoveredRels != null )
                {
                    logger.info( "%d.%d. Processing %d new relationships for: %s\n\n  %s", pass, r.getIndex(), discoveredRels.size(),
                                 result.getSelectedRef(), join( discoveredRels, "\n  " ) );

                    final int index = r.getIndex();
                    idx = 0;

                    boolean contributedRels = false;
                    for ( final ProjectRelationship<?> rel : discoveredRels )
                    {
                        final ProjectVersionRef relTarget = rel.getTarget()
                                                               .asProjectVersionRef();
                        if ( !net.containsGraph( relTarget ) )
                        {
                            final Set<ProjectRelationshipFilter> acceptingChildren = new HashSet<ProjectRelationshipFilter>();
                            int fidx = 0;
                            for ( final ProjectRelationshipFilter filter : filters )
                            {
                                final boolean accepted = filter.accept( rel );
                                logger.info( "%d.%d.%d.%d. CHECK: %s\n  vs.\n\n  %s\n\n  Accepted? %s", pass, index, idx, fidx, rel, filter, accepted );
                                if ( accepted )
                                {
                                    acceptingChildren.add( filter.getChildFilter( rel ) );
                                }
                                fidx++;
                            }

                            if ( !acceptingChildren.isEmpty() )
                            {
                                logger.info( "%d.%d.%d. DISCOVER += %s\n  (filters:\n    %s)", pass, index, idx, relTarget,
                                             new JoinString( "\n    ", acceptingChildren ) );

                                newRels.add( rel );
                                contributedRels = true;

                                // Just map the target to the filter set and allow those to accumulate, then go back and assemble todo's from them afterward.
                                final Set<ProjectRelationshipFilter> targetFilters = newTargets.get( relTarget );
                                if ( targetFilters == null )
                                {
                                    newTargets.put( relTarget, acceptingChildren );
                                }
                                else
                                {
                                    targetFilters.addAll( acceptingChildren );
                                }
                            }
                            else if ( rel.getType() == RelationshipType.PARENT )
                            {
                                logger.info( "FORCE: Adding parent relationship: %s", rel );
                                newRels.add( rel );
                                contributedRels = true;
                            }
                            else
                            {
                                logger.info( "%d.%d.%d. SKIP: %s", pass, index, idx, relTarget );
                            }
                        }
                        else
                        {
                            logger.info( "%d.%d.%d. SKIP (already discovered): %s", pass, index, idx, relTarget );
                        }

                        idx++;
                    }

                    // if all relationships have been discarded by filter...
                    if ( !contributedRels && !discoveredRels.isEmpty() )
                    {
                        logger.info( "INJECT: Adding terminal parent relationship to mark %s as resolved in the dependency graph.",
                                     result.getSelectedRef() );

                        newRels.add( new ParentRelationship( config.getDiscoverySource(), result.getSelectedRef() ) );
                    }
                }
                else
                {
                    logger.info( "discovered relationships NULL for: %s", result.getSelectedRef() );
                }
            }
            else
            {
                markMissing( todo.getRef(), todo, missing );
            }
        }

        if ( !newRels.isEmpty() )
        {
            logger.info( "Storing relationships:\n\n  %s\n\n", join( newRels, "\n  " ) );

            final Set<ProjectRelationship<?>> rejected = dataManager.storeRelationships( newRels );
            logger.info( "Marking rejected relationships as cycle-injectors:\n  %s", join( rejected, "\n  " ) );
            addToCycleParticipants( rejected, cycleParticipants );
        }

        logger.info( "%d. After discovery, these are missing:\n\n  %s\n\n", pass, new JoinString( "\n  ", missing ) );

        final Set<DiscoveryTodo> newTodos = new HashSet<DiscoveryTodo>( newTargets.size() );
        for ( final Entry<ProjectVersionRef, Set<ProjectRelationshipFilter>> entry : newTargets.entrySet() )
        {
            final ProjectVersionRef target = entry.getKey();
            final Set<ProjectRelationshipFilter> targetFilters = entry.getValue();

            newTodos.add( new DiscoveryTodo( target, targetFilters ) );
        }

        return newTodos;
    }

    private void addToCycleParticipants( final Set<ProjectRelationship<?>> rejectedRelationships, final Set<ProjectVersionRef> cycleParticipants )
    {
        for ( final ProjectRelationship<?> rejected : rejectedRelationships )
        {
            cycleParticipants.add( rejected.getDeclaring()
                                           .asProjectVersionRef() );
            cycleParticipants.add( rejected.getTarget()
                                           .asProjectVersionRef() );
        }
    }

    private void markMissing( final ProjectVersionRef ref, final DiscoveryTodo todo, final Set<ProjectVersionRef> missing )
    {
        logger.info( "MISSING(1) += %s", ref );
        missing.add( ref );
        final ProjectVersionRef originalRef = todo.getRef();
        if ( !originalRef.equals( ref ) )
        {
            logger.info( "MISSING(2) += %s", originalRef );
            missing.add( originalRef );
        }
    }

    private Set<ProjectVersionRef> loadExistingCycleParticipants( final EProjectNet net )
    {
        final Set<ProjectVersionRef> participants = new HashSet<ProjectVersionRef>();
        final Set<EProjectCycle> cycles = net.getCycles();
        for ( final EProjectCycle cycle : cycles )
        {
            participants.addAll( cycle.getAllParticipatingProjects() );
        }

        return participants;
    }

    private LinkedList<DiscoveryTodo> loadInitialPending( final EProjectNet net )
    {
        final GraphView view = net.getView();
        final ProjectRelationshipFilter topFilter = view.getFilter();

        final Set<ProjectVersionRef> initialIncomplete = net.getIncompleteSubgraphs();

        logger.info( "Finding paths from: %s to:\n  %s", join( net.getView()
                                                                  .getRoots(), ", " ), join( initialIncomplete, "\n  " ) );

        final Set<List<ProjectRelationship<?>>> paths = net.getPathsTo( initialIncomplete.toArray( new ProjectVersionRef[initialIncomplete.size()] ) );

        if ( paths == null || paths.isEmpty() )
        {
            return new LinkedList<DiscoveryTodo>();
        }

        final Map<ProjectVersionRef, Set<ProjectRelationshipFilter>> filtersByRef = new HashMap<ProjectVersionRef, Set<ProjectRelationshipFilter>>();
        nextPath: for ( final List<ProjectRelationship<?>> path : paths )
        {
            if ( path == null || path.size() < 1 )
            {
                continue;
            }

            final ProjectVersionRef ref = path.get( path.size() - 1 )
                                              .getTarget()
                                              .asProjectVersionRef();
            Set<ProjectRelationshipFilter> pathFilters = filtersByRef.get( ref );
            if ( pathFilters == null )
            {
                pathFilters = new HashSet<ProjectRelationshipFilter>();
                filtersByRef.put( ref, pathFilters );
            }

            ProjectRelationshipFilter f = topFilter;
            for ( final ProjectRelationship<?> rel : path )
            {
                if ( !f.accept( rel ) )
                {
                    continue nextPath;
                }

                f = f.getChildFilter( rel );
            }

            logger.debug( "Adding todo: %s via filter: %s", ref, f );
            pathFilters.add( f );
        }

        final LinkedList<DiscoveryTodo> initialPending = new LinkedList<DiscoveryTodo>();
        for ( final Entry<ProjectVersionRef, Set<ProjectRelationshipFilter>> entry : filtersByRef.entrySet() )
        {
            final ProjectVersionRef ref = entry.getKey();
            final Set<ProjectRelationshipFilter> pathFilters = entry.getValue();

            if ( pathFilters.isEmpty() )
            {
                logger.info( "INIT-SKIP: %s", ref );
                continue;
            }

            final DiscoveryTodo todo = new DiscoveryTodo( ref );
            todo.setFilters( pathFilters );

            logger.info( "INIT-DISCOVER += %s\n  (filters:\n    %s)", ref, new Object()
            {
                @Override
                public String toString()
                {
                    return join( pathFilters, "\n    " );
                }
            } );

            initialPending.add( todo );
        }

        return initialPending;
    }

}
