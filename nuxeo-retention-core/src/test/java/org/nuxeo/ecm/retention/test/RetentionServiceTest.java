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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.retention.TestUtils;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.RetentionRule;
import org.nuxeo.ecm.retention.adapter.Record.RecordRule;
import org.nuxeo.ecm.retention.operations.AttachRetentionRule;
import org.nuxeo.ecm.retention.operations.CreateRetentionRule;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, AutomationFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.retention.service.nuxeo-retention-service",
        "org.nuxeo.ecm.retention.service.nuxeo-retention-service:retention-rules-contrib-test.xml" })
@Deploy("org.nuxeo.runtime.kv")
@Deploy("org.nuxeo.ecm.core.bulk")
@Deploy("org.nuxeo.ecm.core.bulk.test")
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class RetentionServiceTest {

    public static Log log = LogFactory.getLog(RetentionServiceTest.class);

    @Inject
    CoreSession session;

    @Inject
    RetentionService service;

    @Inject
    UserManager userManager;

    @Inject
    CoreFeature settings;

    @Inject
    AutomationService automationService;

    @Test
    public void testStaticConfigurationWithActions() {

        RetentionRule rule = service.getRetentionRule("RuleWithActions", session);
        assertNotNull(rule);

        String actions[] = rule.getBeginActions();
        assertNotNull(actions);
        assertEquals(1, actions.length);
        assertEquals("Document.Lock", actions[0]);

        actions = rule.getEndActions();
        assertNotNull(actions);
        assertEquals(3, actions.length);
        assertEquals("Document.Unlock", actions[0]);
        assertEquals("Document.Trash", actions[1]);
        assertEquals("Document.Delete", actions[2]);
    }

    @Test
    public void testStaticConfigurationShouldFail() {

        try {
            @SuppressWarnings("unused")
            RetentionRule rule = service.getRetentionRule("TooManyActionsShouldFailValidation", session);
            assertTrue("Loading this rule should have fail", false);
        } catch (NuxeoException e) {
            // It is normal to be here
        }
    }

    @Test
    public void testRetentionService() throws InterruptedException {
        List<DocumentModel> docs = new ArrayList<DocumentModel>();
        DocumentModel doc;
        for (int i = 0; i < 5; i++) {
            doc = session.createDocumentModel("/", "root", "Folder");
            doc = session.createDocument(doc);
            docs.add(doc);
        }

        session.save();
        TestUtils.waitForWorkers();

        service.attachRule("myTestRuleId2", "Select * from Folder", session);
        DocumentModelList folders = session.query("Select * from Folder");
        for (DocumentModel f : folders) {
            f.setPropertyValue("dc:title", "blah");
            f = session.saveDocument(f);
        }

        session.save();
        TestUtils.waitForWorkers();

        for (DocumentModel documentModel : docs) {
            documentModel = session.getDocument(documentModel.getRef());
            assertTrue(documentModel.hasFacet(RetentionService.RECORD_FACET));
        }

    }

    @Test
    public void testStartRetentionOnModifiedEvent() throws Exception {
        RetentionRule rule = service.getRetentionRule("myTestRuleId", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "Folder");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        doc = session.getDocument(doc.getRef());
        TestUtils.waitForWorkers();
        // modify the document to see if the rule is invoked
        doc.setPropertyValue("dc:title", "Blahhaa");
        doc = session.saveDocument(doc);
        TestUtils.waitForWorkers();
        assertTrue(doc.isLocked());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());

        LocalDate minCutoff = LocalDate.parse(
                new SimpleDateFormat("yyyy-MM-dd").format(record.getMinCutoffAt().getTime()));

        LocalDate maxRetention = LocalDate.parse(
                new SimpleDateFormat("yyyy-MM-dd").format(record.getMaxRetentionAt().getTime()));
        assertTrue(minCutoff.isEqual(LocalDate.now()));
        LocalDate yearCutoff = LocalDate.now();

        // NXP-23478: Test duration is longer than 1 year (specifically 1Y2M4D)
        // Calculate the actual year
        Period period = rule.getRetentionDurationAsPeriod();
        yearCutoff = yearCutoff.plusYears(period.getYears());
        yearCutoff = yearCutoff.plusMonths(period.getMonths());
        yearCutoff = yearCutoff.plusDays(period.getDays());

        assertEquals(maxRetention.getYear(), yearCutoff.getYear());
        // assertEquals(maxRetention.getMonthValue(), minCutoff.getMonthValue()
        // + rule.getRetentionDurationAsPeriod().getMonths());

        // assertEquals(maxRetention.getDayOfMonth(), minCutoff.getDayOfMonth()
        // + rule.getRetentionDurationAsPeriod().getDays());

        DocumentEventContext context = new DocumentEventContext(session, null, doc);
        context.setProperty("DATE_TO_CHECK",
                new SimpleDateFormat("yyyy-MM-dd").parse(maxRetention.plusDays(1).toString()));
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);

        TestUtils.waitForWorkers();

        doc = session.getDocument(doc.getRef());
        assertFalse(doc.isLocked());
        record = doc.getAdapter(Record.class);
        assertEquals("expired", record.getStatus());
    }

    @Test
    public void testStartRetentionOnCreationEvent() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreation", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        TestUtils.waitForWorkers();
        assertTrue(doc.isLocked());
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
    }

    @Test
    public void testStartRetentionWithDelay() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionWithDelay", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        session.save();

        TestUtils.waitForWorkers();

        try (CloseableCoreSession sessionAsJdoe = settings.openCoreSession("jdoe")) {
            ACP acp = doc.getACP();
            ACL localACL = acp.getOrCreateACL(ACL.LOCAL_ACL);
            localACL.add(new ACE("jdoe", SecurityConstants.READ_WRITE, true));
            doc.setACP(acp, true);
            doc = session.saveDocument(doc);
            doc.setPropertyValue("dc:title", "ddd");

            doc = session.getDocument(doc.getRef());
            Record record = doc.getAdapter(Record.class);
            doc = sessionAsJdoe.saveDocument(doc);

            // NXP-23478 save must complete before retention delay is checked
            TestUtils.waitForWorkers();

            // check that cutoff date was set to currentDate + 1
            assertTrue(record.getMinCutoffAt().getTime().after(new Date()));

            Calendar maxRetention = record.getMinCutoffAt();
            Calendar checkRetention = Calendar.getInstance(maxRetention.getTimeZone());
            checkRetention.setTimeInMillis(maxRetention.getTimeInMillis());
            checkRetention.add(Calendar.HOUR, 1);

            DocumentEventContext context = new DocumentEventContext(session, null, doc);
            // NXP-23478 need timestamp here instead of just date
            context.setProperty("DATE_TO_CHECK", checkRetention.getTime());

            // NXP-23478 Fire sync event
            EventService evts = Framework.getService(EventService.class);
            evts.fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);
            evts.waitForAsyncCompletion();

            TestUtils.waitForWorkers();

            doc = session.getDocument(doc.getRef());
            assertTrue(doc.isLocked());
            record = doc.getAdapter(Record.class);
            assertNotNull(record);
            assertEquals("active", record.getStatus());
            Exception e = null;
            try {
                doc = sessionAsJdoe.saveDocument(doc);
            } catch (DocumentSecurityException e1) {
                e = e1;
            }
            assertNotNull(e);
        }

    }

    @Test
    public void testRules() {
        // we are deploying a static rule
        RetentionRule rule = service.getRetentionRule("myTestRuleId", session);
        assertNotNull(rule);
        assertEquals("myTestRuleId", rule.getId());

        // add a dynamic rule
        DocumentModel doc = session.createDocumentModel("/", "root", "Folder");
        doc = session.createDocument(doc);
        String ruleId = service.createOrUpdateDynamicRuleOnDocument(null, null, 0, null, null, "endAction", null, null,
                "documentUpdated", null, doc, session);
        assertEquals(ruleId, doc.getId());
        rule = service.getRetentionRule(ruleId, session);
        assertNull(rule.getBeginAction());
        assertEquals("endAction", rule.getEndAction());
        assertEquals("documentUpdated", rule.getBeginCondition().getEvent());

    }

    @Test
    public void testRetentionOperations() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "root", "Folder");
        doc = session.createDocument(doc);

        OperationContext ctx = new OperationContext();
        ctx.setInput(doc);
        ctx.setCoreSession(session);
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("retentionPeriod", "2M3D");
        params.put("beginAction", "Document.Lock");

        String ruleId = (String) automationService.run(ctx, CreateRetentionRule.ID, params);
        assertEquals(ruleId, doc.getId());

        RetentionRule rule = service.getRetentionRule(ruleId, session);
        assertNotNull(rule);
        DocumentModel file = session.createDocumentModel("/", "root", "File");
        file = session.createDocument(file);
        service.attachRule(rule.getId(), file);
        session.save();

        Record record = file.getAdapter(Record.class);
        LocalDate maxRetention = LocalDate.parse(
                new SimpleDateFormat("yyyy-MM-dd").format(record.getMaxRetentionAt().getTime()));

        DocumentEventContext context = new DocumentEventContext(session, null, doc);
        context.setProperty("DATE_TO_CHECK",
                new SimpleDateFormat("yyyy-MM-dd").parse(maxRetention.plusDays(1).toString()));
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);

        TestUtils.waitForWorkers();
        file = session.getDocument(file.getRef());
        assertTrue(file.isLocked());
        record = file.getAdapter(Record.class);
        assertEquals("active", record.getStatus());

    }

    @Test
    public void testRetentionReminder() throws Exception {
        // we are deploying a static rule
        RetentionRule rule = service.getRetentionRule("retentionWithReminder", session);
        assertNotNull(rule);
        assertEquals("retentionWithReminder", rule.getId());
        assertTrue(3 == rule.getRetentionReminderDays());

        DocumentModel file = session.createDocumentModel("/", "root", "File");
        file = session.createDocument(file);
        service.attachRule(rule.getId(), file);
        session.save();

        Record record = file.getAdapter(Record.class);
        LocalDate maxRetention = LocalDate.parse(
                new SimpleDateFormat("yyyy-MM-dd").format(record.getMaxRetentionAt().getTime()));

        DocumentEventContext context = new DocumentEventContext(session, null, file);
        context.setProperty("DATE_TO_CHECK",
                new SimpleDateFormat("yyyy-MM-dd").parse(maxRetention.plusDays(1).toString()));
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);

        TestUtils.waitForWorkers();
        file = session.getDocument(file.getRef());
        record = file.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        context.setProperty("DATE_TO_CHECK",
                new SimpleDateFormat("yyyy-MM-dd").parse(maxRetention.minusDays(2).toString()));
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECK_REMINDER_EVENT, context);

        assertTrue(record.getReminderStartDate().getTime().after(record.getMinCutoffAt().getTime()));
        assertTrue(record.getReminderStartDate().getTime().before(record.getMaxRetentionAt().getTime()));
        @SuppressWarnings("deprecation")
        List<String> docsAboutToExpire = service.queryDocsAboutToExpire(
                Date.from(maxRetention.minusDays(2).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
        assertTrue(docsAboutToExpire.size() == 1);
        assertEquals(record.getDoc().getId(), docsAboutToExpire.get(0));
    }

    @Test
    public void testRetentionStartsWithDate() throws Exception {
        // we are deploying a static rule
        RetentionRule rule = service.getRetentionRule("retentionStartsWhenSettingProperty", session);
        assertNotNull(rule);
        assertEquals("retentionStartsWhenSettingProperty", rule.getId());
        assertTrue(2 == rule.getRetentionReminderDays());

        DocumentModel file = session.createDocumentModel("/", "root", "File");
        file = session.createDocument(file);
        service.attachRule(rule.getId(), file);
        session.save();

        Record record = file.getAdapter(Record.class);
        LocalDateTime cutoffDate = LocalDateTime.now();
        cutoffDate = cutoffDate.minusDays(1);
        file.setPropertyValue("record:min_cutoff_at",
                GregorianCalendar.from(ZonedDateTime.of(cutoffDate, ZoneId.systemDefault())));

        file = session.saveDocument(file);
        session.save();

        LocalDate maxRetention = LocalDate.parse(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

        DocumentEventContext context = new DocumentEventContext(session, null, file);
        context.setProperty("DATE_TO_CHECK",
                new SimpleDateFormat("yyyy-MM-dd").parse(maxRetention.plusDays(1).toString()));
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);

        TestUtils.waitForWorkers();
        file = session.getDocument(file.getRef());
        record = file.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        assertTrue(record.getReminderStartDate().after(record.getMinCutoffAt()));
        DocumentModel copy = session.copy(file.getRef(), session.getRootDocument().getRef(), file.getName());
        assertFalse(copy.hasFacet("Record"));

    }

    @Test
    public void testSimpleRetentionAtFirstModification() throws Exception {

        DocumentModel root = session.getRootDocument();
        ACP acp = root.getACP();
        ACL localACL = acp.getOrCreateACL(ACL.LOCAL_ACL);
        localACL.add(new ACE("jdoe", SecurityConstants.EVERYTHING, true));
        root.setACP(acp, true);
        root = session.saveDocument(root);

        try (CloseableCoreSession sessionAsJdoe = settings.openCoreSession("jdoe")) {

            DocumentModel retentionRuleHolder = sessionAsJdoe.createDocumentModel("/", "root", "Folder");
            retentionRuleHolder = sessionAsJdoe.createDocument(retentionRuleHolder);

            DocumentModel file = sessionAsJdoe.createDocumentModel("/", "filee", "File");
            file = sessionAsJdoe.createDocument(file);

            OperationContext ctx = new OperationContext();
            ctx.setInput(retentionRuleHolder);
            ctx.setCoreSession(sessionAsJdoe);
            Map<String, Serializable> params = new HashMap<String, Serializable>();
            params.put("retentionPeriod", "2M3D");
            params.put("beginCondEvent", "documentModified");

            String ruleId = (String) automationService.run(ctx, CreateRetentionRule.ID, params);

            ctx = new OperationContext();
            ctx.setInput(file);
            ctx.setCoreSession(sessionAsJdoe);
            params = new HashMap<String, Serializable>();
            params.put("ruleId", ruleId);

            file = (DocumentModel) automationService.run(ctx, AttachRetentionRule.ID, params);
            file = sessionAsJdoe.getDocument(file.getRef());
            assertTrue(file.hasFacet("Record"));
            Record recordFile = file.getAdapter(Record.class);
            assertEquals("unmanaged", recordFile.getStatus());

            file.setPropertyValue("dc:title", "title");
            file = sessionAsJdoe.saveDocument(file);
            TestUtils.waitForWorkers();

            file = sessionAsJdoe.getDocument(file.getRef());
            assertTrue(file.hasFacet("Record"));
            recordFile = file.getAdapter(Record.class);
            assertEquals("active", recordFile.getStatus());

            DocumentSecurityException e = null;
            try {
                Framework.getService(TrashService.class).trashDocuments(Arrays.asList(new DocumentModel[] { file }));
            } catch (DocumentSecurityException e1) {
                e = e1;

            }
            assertNotNull(e);
        }
    }

    @Test
    public void testRetentionExpireWithDate() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionStartsWhenSettingProperty", session);
        assertNotNull(rule);
        assertEquals("retentionStartsWhenSettingProperty", rule.getId());
        assertTrue(2 == rule.getRetentionReminderDays());

        DocumentModel file = session.createDocumentModel("/", "root", "File");
        file = session.createDocument(file);
        service.attachRule(rule.getId(), file);
        session.save();

        LocalDateTime minCutOffAt = LocalDateTime.now();
        minCutOffAt = minCutOffAt.minusYears(1).minusDays(3);
        file.setPropertyValue("record:min_cutoff_at",
                GregorianCalendar.from(ZonedDateTime.of(minCutOffAt, ZoneId.systemDefault())));

        file = session.saveDocument(file);
        session.save();

        DocumentEventContext context = new DocumentEventContext(session, null, file);
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);

        TestUtils.waitForWorkers();
        file = session.getDocument(file.getRef());
        Record record = file.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("expired", record.getStatus());
    }

    @Test
    public void testClearSingleRule() throws Exception {

        RetentionRule rule = service.getRetentionRule("retentionWithDelay", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);

        session.save();
        TestUtils.waitForWorkers();

        // Check we have everything
        doc = session.getDocument(doc.getRef());
        assertTrue(doc.hasFacet("Record"));

        Record record = doc.getAdapter(Record.class);
        assertTrue(record.hasAtLeastOneRule());

        List<RecordRule> attachedRules = record.getRecordRules();
        assertEquals(1, attachedRules.size());
        assertEquals("retentionWithDelay", attachedRules.get(0).getRuleId());

        // Now, remove the rule
        service.clearRule("retentionWithDelay", doc);
        doc = session.getDocument(doc.getRef());
        // When clearing single rule the facet stays
        assertTrue(doc.hasFacet("Record"));

        record = doc.getAdapter(Record.class);
        assertFalse(record.hasAtLeastOneRule());

    }

    @Test
    public void testBulkClearRules() throws Exception {

        // we are deploying a very simple static rule
        RetentionRule rule = service.getRetentionRule("retentionWithDelay", session);
        assertNotNull(rule);

        int MAX_DOCS = 5;
        DocumentModelList docs = TestUtils.createDocumentsInFolder(session, "/", "folderRetention", MAX_DOCS);

        // Attach the rule using NXQL
        String defaultFilter = " AND ecm:isTrashed = 0 AND ecm:isVersion = 0 AND ecm:isProxy = 0";
        String NXQL_DOCS_IN_FOLDER = "SELECT * FROM Document WHERE ecm:path STARTSWITH '/folderRetention/' "
                + defaultFilter;
        service.attachRule(rule.getId(), NXQL_DOCS_IN_FOLDER, session);
        TestUtils.waitForWorkers();

        // Check it was attached
        docs = session.query("SELECT * From Document WHERE ecm:mixinType = 'Record' " + defaultFilter);
        assertEquals(MAX_DOCS, docs.size());

        // Now, clear all rules and this should remove the "Record" facet
        service.clearRules(NXQL_DOCS_IN_FOLDER);
        TestUtils.waitForWorkers();

        // Check it was removed
        docs = session.query("SELECT * From Document WHERE ecm:mixinType = 'Record' " + defaultFilter);
        assertEquals(0, docs.size());

    }

}
