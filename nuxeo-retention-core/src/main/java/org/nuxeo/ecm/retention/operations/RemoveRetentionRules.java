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

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.retention.service.RetentionService;

@Operation(id = RemoveRetentionRules.ID, category = "Retention", label = "Remove a list of retention rules. If ruleIds is empty, removes all the rules")
public class RemoveRetentionRules {

    public static final String ID = "Retention.RemoveRules";

    @Param(name = "ruleIds", required = false, description = "Clear rules from document.  If no rules are specified, all rules are removed")
    StringList ruleIds;

    @Context
    RetentionService retentionService;

    @Context
    CoreSession session;

    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel attachRetentionRule(DocumentModel doc) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            retentionService.clearRules(doc);
        } else {
            for (String ruleId : ruleIds) {
                retentionService.clearRule(ruleId, doc);
            }
        }
        return doc;

    }
}
