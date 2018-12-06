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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitFilteringEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener that checks for retention triggered by an event specified in the rule
 * 
 * @since 9.2
 */
public class RetentionRecordCheckerListener implements PostCommitFilteringEventListener {

    public static Log log = LogFactory.getLog(RetentionRecordCheckerListener.class);

    List<String> ignoreEvents = Arrays.asList(new String[] { "sessionSaved", "loginSuccess",
            DocumentEventTypes.DOCUMENT_REMOVED, RetentionService.RETENTION_CHECKER_EVENT });

    @Override
    public void handleEvent(EventBundle events) {
        Map<String, List<String>> docsToCheckAndEvents = new HashMap<String, List<String>>();

        Map<String, Boolean> documentModifiedIgnored = new HashMap<String, Boolean>();
        Date dateToCheck = new Date();
        for (Event event : events) {
            DocumentEventContext docEventCtx = (DocumentEventContext) event.getContext();
            DocumentModel doc = docEventCtx.getSourceDocument();
            String docId = doc.getId();
            if (docEventCtx.getProperties().containsKey(RetentionService.RETENTION_CHECKER_LISTENER_IGNORE)
                    && !documentModifiedIgnored.containsKey(docId)) {
                // ignore only once per document per bundle, the rule can be attached and document later modified into
                // the same transaction
                documentModifiedIgnored.put(docId, true);
                continue;
            }

            if (doc == null || !doc.hasFacet(RetentionService.RECORD_FACET)) {
                continue;
            }

            if (docsToCheckAndEvents.containsKey(docId)) {
                List<String> eventsToCheck = docsToCheckAndEvents.get(docId);
                if (!eventsToCheck.contains(event.getName())) {
                    eventsToCheck.add(event.getName());
                }
                docsToCheckAndEvents.put(docId, eventsToCheck);
            } else {
                List<String> evs = new ArrayList<String>();
                evs.add(event.getName());
                docsToCheckAndEvents.put(docId, evs);
            }

            // workaround for tests
            if (event.getContext().getProperty("DATE_TO_CHECK") != null) {
                dateToCheck = (Date) event.getContext().getProperty("DATE_TO_CHECK");
            }

        }
        if (docsToCheckAndEvents.isEmpty()) {
            return;
        }

        // ToDo: check how many events max in a bundle
        Framework.getService(RetentionService.class).evalRules(docsToCheckAndEvents, dateToCheck);
    }

    @Override
    public boolean acceptEvent(Event event) {
        if (ignoreEvents.contains(event.getName())) {
            return false;
        }
        EventContext eventCtx = event.getContext();
        if (!(eventCtx instanceof DocumentEventContext)) {
            return false;
        }
        return true;
    }
}
