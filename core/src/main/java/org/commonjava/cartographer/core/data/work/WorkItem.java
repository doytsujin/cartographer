package org.commonjava.cartographer.core.data.work;

import org.commonjava.cartgorapher.model.graph.PkgId;
import org.commonjava.cartgorapher.model.graph.PkgVersion;
import org.commonjava.cartgorapher.model.user.TraverseScope;

import java.util.Collections;
import java.util.Set;

/**
 * Contains the progress in processing a single node, starting with its parent node, package-id, and traversal scope to
 * use (which is inherited from last traversal). As the BSP step is executed for the node, additional information is
 * filled in, including:
 * <p>
 * <ul>
 * <li>selected {@link PkgVersion}: from {@link org.commonjava.cartographer.spi.service.NodeSelector}</li>
 * </ul>
 * <p>
 * This class doesn't need to track current status through the BSP step, since that step executes more or less linearly.
 * It may become interesting to add this status in order to enable user feedback, but using wireTap EIPs seems to make
 * more sense for that purpose, at least for now.
 */
public class WorkItem
{
    private final WorkId workId;

    private final int ordinal;

    private final int depth;

    private final WorkId parentId;

    private final PkgId target;

    private final String targetVersionAdvice;

    private final TraverseScope scope;

    private PkgVersion selected;

    private final Set<PkgId> exclusions;

    private Exception error;

    public WorkItem( WorkId workId, PkgId target, String targetVersionAdvice, TraverseScope scope,
                     Set<PkgId> exclusions, int ordinal )
    {
        this( workId, target, targetVersionAdvice, scope, exclusions, null, ordinal );
    }

    public WorkItem( WorkId workId, PkgId target, String targetVersionAdvice, TraverseScope scope,
                     Set<PkgId> exclusions, WorkItem parent, int ordinal )
    {
        this.workId = workId;
        this.targetVersionAdvice = targetVersionAdvice;
        this.exclusions = exclusions;
        this.ordinal = ordinal;
        this.parentId = parent == null ? null : parent.getWorkId();
        this.depth = parent == null ? 0 : parent.getDepth()+1;
        this.target = target;
        this.scope = scope;
    }

    public WorkId getWorkId()
    {
        return workId;
    }

    public int getOrdinal()
    {
        return ordinal;
    }

    public int getDepth()
    {
        return depth;
    }

    public PkgId getTarget()
    {
        return target;
    }

    public TraverseScope getScope()
    {
        return scope;
    }

    public PkgVersion getSelected()
    {
        return selected;
    }

    public void setSelected( final PkgVersion selected )
    {
        this.selected = selected;
    }

    public Set<PkgId> getExclusions()
    {
        return exclusions == null ? Collections.emptySet() : exclusions;
    }

    public WorkId getParentId()
    {
        return parentId;
    }

    public String getTargetVersionAdvice()
    {
        return targetVersionAdvice;
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( !( o instanceof WorkItem ) )
        {
            return false;
        }

        final WorkItem workItem = (WorkItem) o;

        return workId.equals( workItem.workId );
    }

    @Override
    public int hashCode()
    {
        return workId.hashCode();
    }

    public Exception getError()
    {
        return error;
    }

    public void setError( final Exception error )
    {
        this.error = error;
    }
}
