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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.ecm.retention.work.RetentionRecordCheckerWork;
import org.nuxeo.runtime.api.Framework;

public class RetentionRecordCheckerListener implements PostCommitEventListener {

    public static Log log = LogFactory.getLog(RetentionRecordCheckerListener.class);

    @Override
    public void handleEvent(EventBundle events) {
        for (Event event : events) {
            checkRecord(event);
        }

    }

    protected void checkRecord(Event event) {
        EventContext eventCtx = event.getContext();
        if (!(eventCtx instanceof DocumentEventContext)) {
            return;
        }
        DocumentEventContext docEventCtx = (DocumentEventContext) eventCtx;
        DocumentModel doc = docEventCtx.getSourceDocument();
        if (doc == null || !doc.hasFacet(RetentionService.RECORD_FACET)) {
            return;
        }
        // should filter for retention in progress
        RetentionRecordCheckerWork work = new RetentionRecordCheckerWork();
        work.setDocument(null, doc.getId());
        Framework.getService(WorkManager.class).schedule(work, WorkManager.Scheduling.ENQUEUE);
    }
}
