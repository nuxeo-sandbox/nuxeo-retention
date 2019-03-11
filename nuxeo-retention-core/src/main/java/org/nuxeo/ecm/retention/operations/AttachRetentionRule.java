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
package org.nuxeo.ecm.retention.operations;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.retention.service.RetentionService;

@Operation(id = AttachRetentionRule.ID, category = "Retention", label = "Attach an existing retention rule to the input document or to the documents returned by the NXQL query. ruleId is either the unique name of an XML contribution or the UUID of a document with the RetentionRule facet")
public class AttachRetentionRule {

    public static final String ID = "Retention.AttachRule";
    
    public static Log log = LogFactory.getLog(AttachRetentionRule.class);

    @Param(name = "ruleId", required = true)
    protected String ruleId;

    @Param(name = "nxql", required = false)
    protected String nxql;

    @Context
    RetentionService retentionService;

    @Context
    CoreSession session;

    @OperationMethod
    public DocumentModel attachRetentionRule(DocumentModel doc) {
        if(StringUtils.isNotBlank(nxql)) {
            log.warn("Operation " + AttachRetentionRule.ID + " called with an input document and a NXQL query: Ignoring the NXQL query and applying the rul to the input document.");
        }
        retentionService.attachRule(ruleId, doc);
        return doc;

    }
    @OperationMethod
    public void attachRetentionRule() {
        retentionService.attachRule(ruleId, nxql, session);
    }
}
