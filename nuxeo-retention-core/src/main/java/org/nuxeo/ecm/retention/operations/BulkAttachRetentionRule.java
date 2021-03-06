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
 *     Thibaud Arguillere
 */
package org.nuxeo.ecm.retention.operations;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.retention.service.RetentionService;

@Operation(id = BulkAttachRetentionRule.ID, category = "Retention", label = "Retention: Bulk Attach Rule", description = "Attach an existing retention rule to the documents returned by the NXQL query. ruleId is either the unique name of an XML contribution or the UUID of a document with the RetentionRule facet")
public class BulkAttachRetentionRule {

    public static final String ID = "Retention.BulkAttachRule";
    
    @Param(name = "ruleId", required = true)
    protected String ruleId;

    @Param(name = "nxql", required = true)
    protected String nxql;

    @Context
    RetentionService retentionService;

    @Context
    CoreSession session;

    @OperationMethod
    public void run() {
        retentionService.attachRule(ruleId, nxql, session);
    }
}
