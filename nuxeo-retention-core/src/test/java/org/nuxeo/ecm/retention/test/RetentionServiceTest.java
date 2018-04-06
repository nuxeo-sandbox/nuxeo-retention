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
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.trash.TrashService;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.RetentionRule;
import org.nuxeo.ecm.retention.rest.AttachRetentionRule;
import org.nuxeo.ecm.retention.rest.CreateRetentionRule;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, AutomationFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.retention.service.nuxeo-retention-service" })
@LocalDeploy("org.nuxeo.ecm.retention.service.nuxeo-retention-service:retention-rules-contrib-test.xml")
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class RetentionServiceTest {

    @Inject
    CoreSession session;

    @Inject
    RetentionService service;

    @Inject
    WorkManager workManager;

    @Inject
    UserManager userManager;

    @Inject
    CoreFeature settings;

    @Inject
    AutomationService automationService;

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
        service.attachRule("myTestRuleId2", "Select * from Folder", session);
        DocumentModelList folders = session.query("Select * from Folder");
        for (DocumentModel f : folders) {
            f.setPropertyValue("dc:title", "blah");
            f = session.saveDocument(f);
        }

        waitForWorkers();
        session.save();
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
        waitForWorkers();
        // modify the document to see if the rule is invoked
        doc.setPropertyValue("dc:title", "Blahhaa");
        doc = session.saveDocument(doc);
        waitForWorkers();
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
        waitForWorkers();
        doc = session.getDocument(doc.getRef());
        record = doc.getAdapter(Record.class);
        assertFalse(doc.isLocked());
        assertEquals("expired", record.getStatus());
    }

    @Test
    public void testStartRetentionOnCreationEvent() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreation", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
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

        CoreSession sessionAsJdoe = settings.openCoreSession("jdoe");
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
        waitForWorkers();

        // check that cutoff date was set to currentDate + 1
        assertTrue(record.getMinCutoffAt().getTime().after(Calendar.getInstance().getTime()));

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

        waitForWorkers();
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
        sessionAsJdoe.close();

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
        String ruleId = service.createOrUpdateDynamicRuleOnDocument(null, null, 0, null, "endAction", null,
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

        waitForWorkers();
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

        waitForWorkers();
        file = session.getDocument(file.getRef());
        record = file.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        context.setProperty("DATE_TO_CHECK",
                new SimpleDateFormat("yyyy-MM-dd").parse(maxRetention.minusDays(2).toString()));
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECK_REMINDER_EVENT, context);

        assertTrue(record.getReminderStartDate().getTime().after(record.getMinCutoffAt().getTime()));
        assertTrue(record.getReminderStartDate().getTime().before(record.getMaxRetentionAt().getTime()));
        List<String> docsAboutToExpire = service.queryDocsAndNotifyRetentionAboutToExpire(
                Date.from(maxRetention.minusDays(2).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()), false);
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

        waitForWorkers();
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

        CoreSession sessionAsJdoe = settings.openCoreSession("jdoe");

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
        waitForWorkers();

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

        sessionAsJdoe.close();

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

        waitForWorkers();
        file = session.getDocument(file.getRef());
        Record record = file.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("expired", record.getStatus());
    }

    @Test
    public void testRetentionWithDisposalDate() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreationWithDisposalDate", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
        assertTrue(doc.isLocked());
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        assertEquals(2099, record.getMaxRetentionAt().get(Calendar.YEAR));
        assertEquals(11, record.getMaxRetentionAt().get(Calendar.MONTH));
        assertEquals(31, record.getMaxRetentionAt().get(Calendar.DATE));
    }

    @Test(expected = NuxeoException.class)
    public void testRetentionWithInvalidDisposalDate() throws Exception {
        service.getRetentionRule("retentionOnCreationWithInvalidDisposalDate", session);
    }

    @Test
    public void testRetentionWithDisposalDateXpath() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreationWithDisposalDateXpath", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, 30);
        doc.setPropertyValue("dc:expired", c.getTime());
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
        assertTrue(doc.isLocked());
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        assertEquals(c.get(Calendar.YEAR), record.getMaxRetentionAt().get(Calendar.YEAR));
        assertEquals(c.get(Calendar.MONTH), record.getMaxRetentionAt().get(Calendar.MONTH));
        assertEquals(c.get(Calendar.DATE), record.getMaxRetentionAt().get(Calendar.DATE));
    }

    @Test()
    public void testRetentionWithInvalidDisposalDateXpath() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreationWithInvalidDisposalDateXpath", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
        assertFalse(doc.isLocked());
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("unmanaged", record.getStatus());
    }

    @Test(expected = NuxeoException.class)
    public void testRetentionWithInvalidDuration() throws Exception {
        service.getRetentionRule("retentionOnCreationWithInvalidDuration", session);
    }

    @Test
    public void testRetentionWithDurationAndDisposalDate() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreationWithDurationAndDisposalDate", session);
        assertNotNull(rule);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 1);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
        assertTrue(doc.isLocked());
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        assertEquals(c.get(Calendar.YEAR), record.getMaxRetentionAt().get(Calendar.YEAR));
        assertEquals(c.get(Calendar.MONTH), record.getMaxRetentionAt().get(Calendar.MONTH));
        assertEquals(c.get(Calendar.DATE), record.getMaxRetentionAt().get(Calendar.DATE));
    }

    @Test
    public void testRetentionWithDurationAndDisposalDateAndDisposalXpath() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreationWithDurationAndDisposalDateAndDisposalXpath",
                session);
        assertNotNull(rule);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 1);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
        assertTrue(doc.isLocked());
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());
        assertEquals(c.get(Calendar.YEAR), record.getMaxRetentionAt().get(Calendar.YEAR));
        assertEquals(c.get(Calendar.MONTH), record.getMaxRetentionAt().get(Calendar.MONTH));
        assertEquals(c.get(Calendar.DATE), record.getMaxRetentionAt().get(Calendar.DATE));
    }

    @Test(expected = DocumentNotFoundException.class)
    public void testWithEndActionDocumentDelete() throws Exception {
        RetentionRule rule = service.getRetentionRule("retentionOnCreationWithEndActionDocumentDelete", session);
        assertNotNull(rule);
        DocumentModel doc = session.createDocumentModel("/", "root", "File");
        doc = session.createDocument(doc);
        service.attachRule(rule.getId(), doc);
        waitForWorkers();
        doc = session.getDocument(doc.getRef());
        Record record = doc.getAdapter(Record.class);
        assertNotNull(record);
        assertEquals("active", record.getStatus());

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, 1);

        DocumentEventContext context = new DocumentEventContext(session, null, doc);
        context.setProperty("DATE_TO_CHECK", c.getTime());
        Framework.getService(EventService.class).fireEvent(RetentionService.RETENTION_CHECKER_EVENT, context);
        waitForWorkers();

        doc = session.getDocument(doc.getRef());
    }

    protected void waitForWorkers() throws InterruptedException {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        final boolean allCompleted = workManager.awaitCompletion(100, TimeUnit.SECONDS);
        assertTrue(allCompleted);
    }

}
