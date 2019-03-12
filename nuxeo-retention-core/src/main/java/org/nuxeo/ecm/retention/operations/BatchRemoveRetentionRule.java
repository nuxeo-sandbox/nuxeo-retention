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
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.retention.service.RetentionService;

@Operation(id = BatchRemoveRetentionRule.ID, category = "Retention", label = "Clear an existing retention rule from the documents returned by the NXQL query. If ruleId is empty, the record facet is removed with all the rules ")
public class BatchRemoveRetentionRule {

    public static final String ID = "Retention.BatchRemoveRule";

    @Param(name = "ruleId", required = false)
    protected String ruleId;

    @Param(name = "nxql", required = false)
    protected String nxql;

    @Context
    RetentionService retentionService;

    @Context
    CoreSession session;

    @OperationMethod
    public void removeRetentionRule() {
        if(StringUtils.isBlank(ruleId) && StringUtils.isBlank(nxql)) {
            throw new IllegalArgumentException("One of the parameter (ruleId or nxql) at least must be set");
        }
        
        if (StringUtils.isNotBlank(ruleId)) {
            retentionService.clearRule(ruleId, nxql);
        } else {
            retentionService.clearRules(nxql);
        }
    }
}
