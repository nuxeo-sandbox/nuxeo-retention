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
package org.nuxeo.ecm.retention.actions;

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

public class AboutToExpireRetentionRuleAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "aboutToExpireRetenionRule";

    public static final String PARAM_DISABLE_AUDIT = "disableAuditLogger";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(AboutToExpireRuleComputation::new,
                               Arrays.asList(INPUT_1 + ":" + ACTION_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                       .build();
    }

    public static class AboutToExpireRuleComputation extends AbstractBulkComputation {

        private static final Logger log = LogManager.getLogger(AboutToExpireRuleComputation.class);

        protected boolean disableAudit;

        protected RetentionService retentionService;

        protected EventService eventService;

        public AboutToExpireRuleComputation() {
            super(ACTION_NAME);
        }

        @Override
        public void startBucket(String bucketKey) {
            BulkCommand command = getCurrentCommand();
            Serializable auditParam = command.getParam(PARAM_DISABLE_AUDIT);
            disableAudit = auditParam != null && Boolean.parseBoolean(auditParam.toString());
            eventService = Framework.getService(EventService.class);
            retentionService = Framework.getService(RetentionService.class);
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
            for (DocumentModel doc : loadDocuments(session, ids)) {
                if (disableAudit) {
                    doc.putContextData(PARAM_DISABLE_AUDIT, Boolean.TRUE);
                }
                // Evaluate rules
                Record record = doc.getAdapter(Record.class);
                if (record != null) {
                    notifyEvent(session, RetentionService.RETENTION_ABOUT_TO_EXPIRE_EVENT, doc);

                } else {
                    log.warn("Document should be impacted by retention but is no longer a Record:" + doc.getId());
                }
            }
        }

        protected void notifyEvent(CoreSession session, String eventId, DocumentModel doc) throws NuxeoException {
            DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
            ctx.setCategory(RetentionService.RETENTION_CATEGORY_EVENT);
            ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, session.getRepositoryName());
            ctx.setProperty(CoreEventConstants.SESSION_ID, session.getSessionId());
            Event event = ctx.newEvent(eventId);
            eventService.fireEvent(event);
        }
    }

}
