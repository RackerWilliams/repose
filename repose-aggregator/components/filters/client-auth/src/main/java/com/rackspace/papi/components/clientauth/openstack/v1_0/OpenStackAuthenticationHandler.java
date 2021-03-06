package com.rackspace.papi.components.clientauth.openstack.v1_0;

import com.rackspace.auth.AuthGroup;
import com.rackspace.auth.AuthGroups;
import com.rackspace.auth.AuthToken;
import com.rackspace.auth.openstack.AuthenticationService;
import com.rackspace.auth.openstack.OpenStackToken;
import com.rackspace.papi.commons.util.regex.ExtractorResult;
import com.rackspace.papi.commons.util.servlet.http.ReadableHttpServletResponse;
import com.rackspace.papi.components.clientauth.common.*;
import com.rackspace.papi.filter.logic.FilterDirector;
import org.openstack.docs.identity.api.v2.AuthenticateResponse;
import org.openstack.docs.identity.api.v2.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author fran
 */
public class OpenStackAuthenticationHandler extends AuthenticationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackAuthenticationHandler.class);
    private static final String WWW_AUTH_PREFIX = "Keystone uri=";
    private final String wwwAuthHeaderContents;
    private final AuthenticationService authenticationService;
    private final List<String> serviceAdminRoles;
    private final List<String> ignoreTenantRoles;

    public OpenStackAuthenticationHandler(
            Configurables cfg,
            AuthenticationService serviceClient,
            AuthTokenCache cache,
            AuthGroupCache grpCache,
            AuthUserCache usrCache,
            EndpointsCache endpointsCache,
            UriMatcher uriMatcher) {

        super(cfg, cache, grpCache, usrCache, endpointsCache, uriMatcher);
        this.authenticationService = serviceClient;
        this.wwwAuthHeaderContents = WWW_AUTH_PREFIX + cfg.getAuthServiceUri();
        this.serviceAdminRoles = cfg.getServiceAdminRoles();
        this.ignoreTenantRoles = cfg.getIgnoreTenantRoles();
    }

    private boolean roleIsServiceAdmin(AuthToken authToken) {
        if (authToken.getRoles() == null || serviceAdminRoles == null) {
            return false;
        }

        for (String role : authToken.getRoles().split(",")) {
            if (serviceAdminRoles.contains(role)) {
                return true;
            }
        }

        return false;
    }

    private AuthToken validateTenant(AuthenticateResponse resp, String tenantID) {
        AuthToken authToken = null;
        if(resp != null) {
            authToken = new OpenStackToken(resp);
        }

        if (authToken != null && !roleIsServiceAdmin(authToken) && !authToken.getTenantId().equalsIgnoreCase(tenantID)) {
            // tenant ID from token did not match URI.

            if (resp.getUser() != null && resp.getUser().getRoles() != null) {
                for (Role role : resp.getUser().getRoles().getRole()) {
                    if(tenantID.equalsIgnoreCase(role.getTenantId())) {
                        //we have the real tenantID
                        return authToken;
                    }
                }
            }

            LOG.error("Unable to validate token for tenant.  Invalid token.");
            return null;
        } else {
            return authToken;
        }
    }

    @Override
    public AuthToken validateToken(ExtractorResult<String> account, String token) {
        AuthToken authToken = null;

        if (account != null) {
            authToken = validateTenant(authenticationService.validateToken(account.getResult(), token), account.getResult());
        } else {
            AuthenticateResponse authResp = authenticationService.validateToken(null, token);
            if(authResp != null) {
                authToken = new OpenStackToken(authResp);
            }
        }

        /**
         * If any role in that token is in the BypassTenantRoles list, bypass the tenant check
         */
        if (authToken != null) { //authToken could still be null at this point :(
            boolean ignoreTenantRequirement = false;
            for (String role : authToken.getRoles().split(",")) {
                if (ignoreTenantRoles.contains(role)) {
                    ignoreTenantRequirement = true;
                }
            }

            if (!ignoreTenantRequirement) {
                if (authToken.getTenantId() == null || authToken.getTenantName() == null) {
                    //Moved this check from within the OpenStackToken into here
                    throw new IllegalArgumentException("Invalid Response from Auth. Token object must have a tenant");
                }
            }
        }
        return authToken;
    }

    @Override
    public AuthGroups getGroups(String group) {
        return authenticationService.getGroups(group);
    }

    @Override
    public FilterDirector processResponse(ReadableHttpServletResponse response) {
        return new OpenStackResponseHandler(response, wwwAuthHeaderContents).handle();
    }

    @Override //getting the final encoded string
    protected String getEndpointsBase64(String token, EndpointsConfiguration endpointsConfiguration) {
        return authenticationService.getBase64EndpointsStringForHeaders(token, endpointsConfiguration.getFormat());
    }

    @Override
    public void setFilterDirectorValues(
            String authToken,
            AuthToken cachableToken,
            Boolean delegatable,
            FilterDirector filterDirector,
            String extractedResult,
            List<AuthGroup> groups,
            String endpointsInBase64, boolean tenanted, boolean sendAllTenantIds) {

        new OpenStackAuthenticationHeaderManager(authToken, cachableToken, delegatable, filterDirector, extractedResult,
                groups, wwwAuthHeaderContents, endpointsInBase64, tenanted, sendAllTenantIds)
                .setFilterDirectorValues();
    }
}
