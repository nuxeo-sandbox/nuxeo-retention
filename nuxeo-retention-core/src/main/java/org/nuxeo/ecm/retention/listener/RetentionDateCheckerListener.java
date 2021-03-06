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
package org.nuxeo.ecm.retention.listener;

import java.util.Date;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;

public class RetentionDateCheckerListener implements EventListener {

    public static final String DATE_TO_CHECK = "DATE_TO_CHECK";

    @Override
    public void handleEvent(Event event) {
        Date dateToCheck = new Date();
        // workaround for tests
        if (event.getContext().getProperty(DATE_TO_CHECK) != null) {
            dateToCheck = (Date) event.getContext().getProperty(DATE_TO_CHECK);
        }
        if (RetentionService.RETENTION_CHECKER_EVENT.equals(event.getName())) {
            Framework.getService(RetentionService.class).queryDocsAndEvalRulesForDate(dateToCheck);
        }
    }
}
