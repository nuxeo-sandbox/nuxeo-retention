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
import java.util.Calendar;
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
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;

public class RetentionRecordCheckerListener implements PostCommitEventListener {

    public static Log log = LogFactory.getLog(RetentionRecordCheckerListener.class);

    @Override
    public void handleEvent(EventBundle events) {
        Map<String, List<String>> docsToCheckAndEvents = new HashMap<String, List<String>>();
        String ignoreId = null;
        if (events.containsEventName(RetentionService.RETENTION_CHECKER_LISTENER_IGNORE_EVENT)) {
            for (Event event : events) {
                if (RetentionService.RETENTION_CHECKER_LISTENER_IGNORE_EVENT.equals(event.getName())) {
                    ignoreId = ((DocumentEventContext) event.getContext()).getSourceDocument().getId();
                }
            }
        }
        for (Event event : events) {
            if (RetentionService.RETENTION_CHECKER_LISTENER_IGNORE_EVENT.equals(event.getName())) {
                continue;
            }
            if (DocumentEventTypes.DOCUMENT_REMOVED.equals(event.getName())) {
                // is too late for retention, this will later trigger a document not found exception
                continue;

            }
            EventContext eventCtx = event.getContext();
            if (!(eventCtx instanceof DocumentEventContext)) {
                continue;
            }
            DocumentEventContext docEventCtx = (DocumentEventContext) eventCtx;
            DocumentModel doc = docEventCtx.getSourceDocument();
            if (doc == null || !doc.hasFacet(RetentionService.RECORD_FACET)) {
                continue;
            }
            String docId = doc.getId();
            // avoid triggering the retention by the first 'documentModified' triggered when the rule is attached to the
            // document
            // a better solution?
            if (ignoreId != null && ignoreId.equals(docId)
                    && DocumentEventTypes.DOCUMENT_UPDATED.equals(event.getName())) {
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

        }
        if (docsToCheckAndEvents.isEmpty()) {
            return;
        }

        // ToDo: check how many events max in a bundle
        Framework.getLocalService(RetentionService.class).evalRules(docsToCheckAndEvents,
                Calendar.getInstance().getTime());

    }
}
