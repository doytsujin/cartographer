package org.commonjava.cartographer.dispatch.route;

import org.apache.camel.RecipientList;
import org.commonjava.cartographer.core.data.pkg.PackageInfoRegistry;
import org.commonjava.cartographer.spi.data.pkg.CartoPackageInfo;
import org.commonjava.cartgorapher.model.graph.PkgId;
import org.commonjava.cartographer.core.structure.EndpointKeys;
import org.commonjava.propulsor.deploy.camel.route.EndpointAliasManager;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Dynamically load the available package-specific node resolvers, then determine which of these to use to resolve the
 * target {@link PkgId}.
 */
// TODO: In a distributed scenario, we'd use something like Infinispan to register the available packageType -> route map
@ApplicationScoped
public class NodeResolverRecipientList
{
    @Inject
    private EndpointAliasManager aliasManager;

    @Inject
    private PackageInfoRegistry packageInfoRegistry;

    @RecipientList
    public String[] route( PkgId target )
    {
        CartoPackageInfo pkgInfo = packageInfoRegistry.getPackageInfo( target.getPackageType() );
        if ( pkgInfo != null )
        {
            return new String[] { aliasManager.lookup( pkgInfo.getResolverRoute() ) };
        }

        return new String[] { aliasManager.lookup( EndpointKeys.ROUTE_RESOLVER_NOT_FOUND ) };
    }
}