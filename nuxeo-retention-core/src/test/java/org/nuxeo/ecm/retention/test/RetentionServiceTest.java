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

package org.nuxeo.ecm.retention.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.retention.service.nuxeo-retention-service" })
@LocalDeploy("org.nuxeo.ecm.retention.service.nuxeo-retention-service:retention-types-contrib.xml")
public class RetentionServiceTest {

    @Inject
    CoreSession session;

    @Inject
    RetentionService service;

    @Inject
    WorkManager workManager;

    @Test
    public void testRetentionService() throws InterruptedException {
        assertNotNull(service);
        DocumentModel doc = session.createDocumentModel("/", "root", "Folder");
        doc = session.createDocument(doc);
        service.attachRule(null, doc, session);
        doc = session.getDocument(doc.getRef());
        assertTrue(doc.hasFacet(RetentionService.RECORD_FACET));

        List<DocumentModel> docs = new ArrayList<DocumentModel>();

        for (int i = 0; i < 5; i++) {
            doc = session.createDocumentModel("/", "root", "Folder");
            doc = session.createDocument(doc);
            docs.add(doc);
        }
        session.save();
        service.attachRule(null, "Select * from Folder", session);

        waitForWorkers();
        for (DocumentModel documentModel : docs) {
            documentModel = session.getDocument(documentModel.getRef());
            assertTrue(documentModel.hasFacet(RetentionService.RECORD_FACET));
        }

    }

    @Test
    public void testRecordChecker() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "root", "Folder");
        doc = session.createDocument(doc);
        service.attachRule(null, doc, session);
        Framework.getLocalService(EventProducer.class)
                 .fireEvent(
                         new DocumentEventContext(doc.getCoreSession(), session.getPrincipal(), doc).newEvent(RetentionService.CHECK_RECORD_EVENT));
        waitForWorkers();
        // TO BE continued !!

    }

    protected void waitForWorkers() throws InterruptedException {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        final boolean allCompleted = workManager.awaitCompletion(10, TimeUnit.SECONDS);
        assertTrue(allCompleted);
    }

}
