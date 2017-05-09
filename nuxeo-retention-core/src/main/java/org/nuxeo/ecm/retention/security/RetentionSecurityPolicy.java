/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     mcedica@nuxeo.com
 */
package org.nuxeo.ecm.retention.security;

import java.security.Principal;
import java.util.Arrays;

import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.Access;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.security.AbstractSecurityPolicy;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.service.RetentionService;

/**
 * Security policy that blocks WRITE permission on a document while in retention
 */
public class RetentionSecurityPolicy extends AbstractSecurityPolicy {


    @Override
    public Access checkPermission(Document doc, ACP mergedAcp, Principal principal, String permission,
            String[] resolvedPermissions, String[] additionalPrincipals) {
        Access access = Access.UNKNOWN;
        // policy only applies on WRITE
        if (resolvedPermissions == null || !Arrays.asList(resolvedPermissions).contains(SecurityConstants.WRITE)) {
            return access;
        }
        if (doc.hasFacet(RetentionService.RECORD_FACET)) {
            if ("active".equals((String) doc.getPropertyValue(Record.STATUS_FIELD))) {
                access = Access.DENY;
            }
        }
        return access;
    }

    @Override
    public boolean isExpressibleInQuery(String repositoryName) {
        return true;
    }

    @Override
    public SQLQuery.Transformer getQueryTransformer(String repositoryName) {
        return SQLQuery.Transformer.IDENTITY;
    }


}
