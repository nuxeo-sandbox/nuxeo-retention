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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.Record.RecordRule;
import org.nuxeo.ecm.retention.adapter.RetentionRule;
import org.nuxeo.ecm.retention.operations.AttachRetentionRule;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, AutomationFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.retention.service.nuxeo-retention-service",
        "org.nuxeo.ecm.retention.service.nuxeo-retention-service:retention-rules-contrib-test.xml" })
@Deploy("org.nuxeo.runtime.kv")
@Deploy("org.nuxeo.ecm.core.bulk")
@Deploy("org.nuxeo.ecm.core.bulk.test")
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class RetentionOperationsTest {

    public static Log log = LogFactory.getLog(RetentionOperationsTest.class);

    @Inject
    CoreSession session;

    @Inject
    RetentionService service;

    @Inject
    AutomationService automationService;

    @Inject
    WorkManager workManager;

    @Test
    public void testAttachRuleWithDoc() throws Exception {

        // we are deploying a very simple static rule
        RetentionRule rule = service.getRetentionRule("SimpleRule", session);
        assertNotNull(rule);
        assertEquals("P1Y", rule.getRetentionDuration());

        DocumentModel doc = session.createDocumentModel("/", "file", "File");
        doc.setPropertyValue("dc:title", "file");
        doc = session.createDocument(doc);

        session.save();
        waitForWorkers();

        doc = session.getDocument(doc.getRef());
        assertFalse(doc.hasFacet("Record"));

        OperationContext ctx = new OperationContext();
        ctx.setCoreSession(session);
        ctx.setInput(doc);
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("ruleId", rule.getId());

        doc = (DocumentModel) automationService.run(ctx, AttachRetentionRule.ID, params);
        assertTrue(doc.hasFacet("Record"));
        Record record = doc.getAdapter(Record.class);
        assertEquals("active", record.getStatus());
        List<RecordRule> attachedRules = record.getRecordRules();
        assertEquals(1, attachedRules.size());
        assertEquals("SimpleRule", attachedRules.get(0).getRuleId());
    }

    @Test
    public void testAttachRuleWithNxql() throws Exception {

        // we are deploying a very simple static rule
        RetentionRule rule = service.getRetentionRule("SimpleRule", session);
        assertNotNull(rule);
        assertEquals("P1Y", rule.getRetentionDuration());

        // Create a folder with a document that should not be put under retention
        DocumentModel folder1 = session.createDocumentModel("/", "folder1", "Folder");
        folder1 = session.createDocument(folder1);
        // Documents in this folder should not be put under retention
        DocumentModel noChangeDoc = session.createDocumentModel("/folder1", "folder1-file1", "File");
        noChangeDoc = session.createDocument(noChangeDoc);

        // Create a folder whose documents should all be put under retention
        DocumentModel folderRetention = session.createDocumentModel("/", "folderRetention", "Folder");
        folderRetention = session.createDocument(folderRetention);

        List<DocumentModel> docs = new ArrayList<DocumentModel>();
        DocumentModel doc;
        for (int i = 0; i < 5; i++) {
            doc = session.createDocumentModel("/folderRetention", "file-" + i, "File");
            doc = session.createDocument(doc);
            docs.add(doc);
        }

        session.save();
        waitForWorkers();
        
        // Just check all look good before attaching the rule
        noChangeDoc = session.getDocument(noChangeDoc.getRef());
        assertFalse(noChangeDoc.hasFacet("Record"));
        docs.forEach((oneDoc) -> {
            oneDoc.refresh();
            assertFalse(oneDoc.hasFacet("Record"));
        });
        
        // Attach the rule using NXQL
        OperationContext ctx = new OperationContext();
        ctx.setInput(null);
        ctx.setCoreSession(session);
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("ruleId", rule.getId());
        params.put("nxql",
                "SELECT * FROM Document WHERE ecm:path STARTSWITH '/folderRetention/' AND ecm:isTrashed = 0 AND ecm:isVersion = 0 AND ecm:isProxy = 0");

        automationService.run(ctx, AttachRetentionRule.ID, params);
        waitForWorkers();
        
        noChangeDoc = session.getDocument(noChangeDoc.getRef());
        assertFalse(noChangeDoc.hasFacet("Record"));
        
        docs.forEach((oneDoc) -> {
            oneDoc = session.getDocument(oneDoc.getRef());
            assertTrue(oneDoc.hasFacet("Record"));
        });
        
    }

    protected void waitForWorkers() throws InterruptedException {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        final boolean allCompleted = workManager.awaitCompletion(100, TimeUnit.SECONDS);
        assertTrue(allCompleted);
    }

}
