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
package org.nuxeo.ecm.retention.rest;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.retention.service.RetentionService;

@Operation(id = CreateRetentionRule.ID, category = "Retention", label = "Creates a new retention rule")
public class CreateRetentionRule {

    public static final String ID = "Retention.CreateRule";

    @Param(name = "beginDelay", required = false)
    protected Long beginDelay;

    @Param(name = "retentionPeriod")
    protected Long retentionPeriod;

    @Param(name = "retentionReminder", required = false)
    protected int retentionReminder;

    @Param(name = "beginAction", required = false)
    protected String beginAction;

    @Param(name = "endAction", required = false)
    protected String endAction;

    @Param(name = "beginCondExpression", required = false)
    protected String beginCondExpression;

    @Param(name = "beginCondEvent", required = false)
    protected String beginCondEvent;

    @Param(name = "endCondExpression", required = false)
    protected String endCondExpression;

    @Context
    RetentionService retentionService;

    @Context
    CoreSession session;

    @OperationMethod
    public String createRetentionRule(DocumentModel doc) {
        return retentionService.createOrUpdateDynamicRuleOnDocument(beginDelay, retentionPeriod, retentionReminder,
                beginAction, endAction, beginCondExpression, beginCondEvent, endCondExpression, doc, session);

    }
}
