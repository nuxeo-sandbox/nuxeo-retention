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
import org.nuxeo.ecm.retention.service.RetentionService;

@Operation(id = EnableService.ID, category = "Retention", label = "Retention: Enable Service", description = "Restore the handling of events and rules evaluation that was paused with Retention.DisableService")
public class EnableService {

    public static final String ID = "Retention.EnableService";

    @Context
    RetentionService retentionService;

    @OperationMethod
    public void run() {
        retentionService.activateEventsAndEvaluationRulesProcessing();
    }
}
