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

package org.nuxeo.ecm.retention.service;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.el.ExpressionFactoryImpl;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.operations.document.DeleteDocument;
import org.nuxeo.ecm.automation.core.operations.document.LockDocument;
import org.nuxeo.ecm.automation.core.operations.document.TrashDocument;
import org.nuxeo.ecm.automation.core.operations.document.UnlockDocument;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.actions.ELActionContext;
import org.nuxeo.ecm.platform.el.ExpressionContext;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.retention.actions.AboutToExpireRetentionRuleAction;
import org.nuxeo.ecm.retention.actions.AttachRetentionRuleAction;
import org.nuxeo.ecm.retention.actions.EvaluateRetentionRuleAction;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.RetentionRule;
import org.nuxeo.ecm.retention.work.RetentionRecordCheckerWork;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;

public class RetentionComponent extends DefaultComponent implements RetentionService {

    public static final String RULES_EP = "rules";

    public enum ActionType {
        BEGIN, END
    }

    public static Log log = LogFactory.getLog(RetentionComponent.class);

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (RULES_EP.equals(extensionPoint)) {
            register(RULES_EP, (Descriptor) contribution);
        }
    }

    @Override
    public void attachRule(String ruleId, DocumentModel doc) {
        CoreInstance.doPrivileged(doc.getCoreSession().getRepositoryName(), (CoreSession session) -> {
            if (!doc.hasFacet(RECORD_FACET)) {
                doc.addFacet(RECORD_FACET);
                doc.getContextData().put("facets", RECORD_FACET);
            }
            Record record = doc.getAdapter(Record.class);
            if (record.hasRule(ruleId)) {
                return;
            }

            RetentionRule rule = getRetentionRule(ruleId, session);
            record.addRule(ruleId);
            // start the rule if possible
            Boolean ruleApplies = rule.getBeginCondition() != null
                    ? evaluateConditionExpression(initActionContext(record.getDoc(), session),
                            rule.getBeginCondition().getExpression(), record.getDoc(), null, session)
                    : true;
            if (rule.getBeginCondition().getEvent() == null) {
                if (ruleApplies) {
                    evalRetentionDatesAndStartOrExpireIfApplies(record, rule, new Date(), false, session);
                }
            }
            record.save(session);
        });

    }

    @Override
    public boolean clearRule(String ruleId, DocumentModel doc) {
        final AtomicBoolean cleared = new AtomicBoolean(false);
        CoreInstance.doPrivileged(doc.getCoreSession().getRepositoryName(), (CoreSession session) -> {
            if (!doc.hasFacet(RECORD_FACET)) {
                return;
            }
            Record record = doc.getAdapter(Record.class);
            if (!record.hasRule(ruleId)) {
                return;
            }

            record.removeRule(ruleId);
            record.save(session);
        });
        return cleared.get();
    }

    @Override
    public void clearRules(DocumentModel doc) {
        CoreInstance.doPrivileged(doc.getCoreSession().getRepositoryName(), (CoreSession session) -> {
            if (doc.hasFacet(RECORD_FACET)) {
                doc.removeFacet(RECORD_FACET);
                session.saveDocument(doc);
            }
        });
    }

    private PageProviderService pageProvider() {
        return Framework.getService(PageProviderService.class);
    }

    @Override
    public void attachRule(String ruleId, String query, CoreSession session) {
        // Construct query
        BulkCommand command = new BulkCommand.Builder(AttachRetentionRuleAction.ACTION_NAME, query).param(
                AttachRetentionRuleAction.PARAM_RULE_ID, ruleId).user("Administrator").build();

        // Submit command
        BulkService bulkService = Framework.getService(BulkService.class);
        String commandId = bulkService.submit(command);

        try {
            boolean complete = false;
            while (!complete) {
                // Await end of computation
                complete = bulkService.await(commandId, Duration.ofMinutes(1));
            }

        } catch (InterruptedException iex) {
            // ignored
        }

        // Get status
        BulkStatus status = bulkService.getStatus(commandId);
        switch (status.getState()) {
        case COMPLETED:
            log.debug("Bulk attach completed: " + status);
            break;
        case ABORTED:
            log.warn("Retention bulk attach aborted: " + status);
            break;
        case UNKNOWN:
            log.error("Unknown status for bulk attach: " + status);
            break;
        default:
            // continue
        }
    }

    @Override
    public void evalRules(Record record, Set<String> events, Date dateToCheck, CoreSession session) {
        if (record == null) {
            return; // nothing to do
        }
        String retentionStatus = record.getStatus();
        if (RETENTION_STATE.expired.name().equals(retentionStatus)) {
            // retention expired, nothing to do, should remove the facet?
            return;

        }
        // no need to init it for every rule, its the same
        ELActionContext actionContext = initActionContext(record.getDoc(), session);
        List<Record.RecordRule> recordRules = record.getRecordRules();
        for (Record.RecordRule rr : recordRules) {
            // for every rule on the document;
            // if cutoff start and disposalDate have been set, and the retention is active only need to check if the
            // disposal date has been reached
            if (RETENTION_STATE.active.name().equalsIgnoreCase(record.getStatus()) && rr.getCutoffStart() != null
                    && rr.getDisposalDate() != null) {
                // disposal date has passed
                Calendar maxCutOff = Calendar.getInstance();
                maxCutOff.setTime(dateToCheck);

                if (rr.getDisposalDate().compareTo(maxCutOff) <= 0) {
                    // record is still active? if yes execute endAction and set to expired
                    endRetention(record, getRetentionRule(rr.getRuleId(), session), session);
                    return; // no need to check other rules? the first one that applies sets the document to
                            // expired?
                }
                continue; // if the dates are already set, no need to inspect the condition
            }

            RetentionRule rule = getRetentionRule(rr.getRuleId(), session);
            Boolean ruleApplies = evaluateConditionExpression(actionContext, rule.getBeginCondition().getExpression(),
                    record.getDoc(), dateToCheck, session);
            // if either there is an event to match or there is no event
            if ((ruleApplies && events != null && events.contains(rule.getBeginCondition().getEvent()))
                    || (ruleApplies && StringUtils.isBlank(rule.getBeginCondition().getEvent()))) {
                evalRetentionDatesAndStartOrExpireIfApplies(record, rule, dateToCheck, true, session);
            }

        }
    }

    @Override
    public void evalRules(Map<String, Set<String>> docsToCheckAndEvents, Date dateToCheck) {
        RetentionRecordCheckerWork work = new RetentionRecordCheckerWork(docsToCheckAndEvents, dateToCheck);
        if (docsToCheckAndEvents.isEmpty()) {
            return;
        }
        Framework.getService(WorkManager.class).schedule(work, WorkManager.Scheduling.ENQUEUE);
    }

    @Override
    public void queryDocsAndEvalRulesForDate(Date dateToCheck) {
        // docs in active retention that might have been expired
        evalRules(dateToCheck, "active_records");
        // docs not in active retention because of the initial delay
        evalRules(dateToCheck, "unmanaged_records");
    }

    @Override
    public List<String> queryDocsAboutToExpire(Date dateToCheck) {
        List<String> docIds = new ArrayList<String>();

        new UnrestrictedSessionRunner(Framework.getService(RepositoryManager.class).getDefaultRepositoryName()) {

            @Override
            public void run() {
                Map<String, Serializable> props = new HashMap<String, Serializable>();

                props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
                Object[] params = { new SimpleDateFormat("yyyy-MM-dd").format(dateToCheck) };

                @SuppressWarnings("unchecked")
                PageProvider<Map<String, Serializable>> pp = (PageProvider<Map<String, Serializable>>) pageProvider().getPageProvider(
                        "active_records_reminder", null, batchSize, 0L, props, params);

                long offset = 0;
                List<Map<String, Serializable>> nextDocumentsToBeChecked;
                long maxResult = pp.getPageSize();
                do {
                    pp.setCurrentPageOffset(offset);
                    pp.refresh();
                    nextDocumentsToBeChecked = pp.getCurrentPage();
                    if (nextDocumentsToBeChecked.isEmpty()) {
                        break;
                    }

                    for (Map<String, Serializable> result : nextDocumentsToBeChecked) {
                        docIds.add((String) result.get("ecm:uuid"));
                    }
                    offset += maxResult;
                } while (nextDocumentsToBeChecked.size() == maxResult && pp.isNextPageAvailable());
            }
        }.runUnrestricted();

        return docIds;

    }

    @Override
    public void notifyRetentionAboutToExpire(Date dateToCheck) {
        final Date dateRef = dateToCheck != null ? dateToCheck : new Date();

        new UnrestrictedSessionRunner(Framework.getService(RepositoryManager.class).getDefaultRepositoryName()) {
            @Override
            public void run() {

                // Construct query
                String query = formatQuery("active_records_reminder", dateRef, "yyyy-MM-dd");
                BulkCommand command = new BulkCommand.Builder(AboutToExpireRetentionRuleAction.ACTION_NAME, query).user(
                        "Administrator").build();

                // Submit command
                BulkService bulkService = Framework.getService(BulkService.class);
                String commandId = bulkService.submit(command);

                try {
                    boolean complete = false;
                    while (!complete) {
                        // Await end of computation
                        complete = bulkService.await(commandId, Duration.ofMinutes(1));
                    }

                } catch (InterruptedException iex) {
                    // ignored
                }

                // Get status
                BulkStatus status = bulkService.getStatus(commandId);
                switch (status.getState()) {
                case COMPLETED:
                    log.debug("Command completed: " + status);
                    break;
                case ABORTED:
                    log.warn("Retention bulk evaluation aborted: " + status);
                    break;
                case UNKNOWN:
                    log.error("Unknown status for command: " + status);
                    break;
                default:
                    // continue
                }
            }
        }.runUnrestricted();
    }

    protected void evalRules(Date dateToCheck, String providerName) {
        final Date dateRef = dateToCheck != null ? dateToCheck : new Date();

        new UnrestrictedSessionRunner(Framework.getService(RepositoryManager.class).getDefaultRepositoryName()) {
            @Override
            public void run() {

                // Construct query
                String query = formatQuery(providerName, dateRef, "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                BulkCommand command = new BulkCommand.Builder(EvaluateRetentionRuleAction.ACTION_NAME,
                        query).param(EvaluateRetentionRuleAction.PARAM_DATE_TO_CHECK_MS, dateRef.getTime())
                              .user("Administrator")
                              .build();

                // Submit command
                BulkService bulkService = Framework.getService(BulkService.class);
                String commandId = bulkService.submit(command);

                try {
                    boolean complete = false;
                    while (!complete) {
                        // Await end of computation
                        complete = bulkService.await(commandId, Duration.ofMinutes(1));
                    }

                } catch (InterruptedException iex) {
                    // ignored
                }

                // Get status
                BulkStatus status = bulkService.getStatus(commandId);
                switch (status.getState()) {
                case COMPLETED:
                    log.debug("Command completed: " + status);
                    break;
                case ABORTED:
                    log.warn("Retention bulk evaluation aborted: " + status);
                    break;
                case UNKNOWN:
                    log.error("Unknown status for command: " + status);
                    break;
                default:
                    // continue
                }
            }
        }.runUnrestricted();

    }

    protected String formatQuery(String providerName, Date dateToCheck, String format) {
        PageProviderDefinition def = pageProvider().getPageProviderDefinition(providerName);
        String pattern = def.getPattern();
        return pattern.replace("?", "'" + new SimpleDateFormat(format).format(dateToCheck) + "'");
    }

    @Override
    public void endRetention(Record record, RetentionRule rule, CoreSession session) {
        executeRuleAction(ActionType.END, rule, record.getDoc(), session);
        notifyEvent(session, RETENTION_EXPIRED_EVENT, record.getDoc());
        record.setStatus(RETENTION_STATE.expired.name());
        record.save(session);
    }

    protected void evalRetentionDatesAndStartOrExpireIfApplies(Record record, RetentionRule rule, Date cDate,
            boolean save, CoreSession session) {
        Date minCutoffDate = record.getMinCutoffAt() == null ? new Date() : record.getMinCutoffAt().getTime();
        LocalDateTime cutoffDate = LocalDateTime.ofInstant(minCutoffDate.toInstant(), ZoneId.systemDefault());
        LocalDateTime startReminderDate = null;
        if (!rule.getBeginDealyAsPeriod().isZero()) {
            Period delayPeriod = rule.getBeginDealyAsPeriod();
            cutoffDate = cutoffDate.plusYears(delayPeriod.getYears())
                                   .plusMonths(delayPeriod.getMonths())
                                   .plusDays(delayPeriod.getDays());
        }

        Period retentionPeriod = rule.getRetentionDurationAsPeriod();
        LocalDateTime disposalDate = cutoffDate.plusYears(retentionPeriod.getYears())
                                               .plusMonths(retentionPeriod.getMonths())
                                               .plusDays(retentionPeriod.getDays());

        if (rule.getRetentionReminderDays() > 0) {
            startReminderDate = disposalDate.minusDays(rule.getRetentionReminderDays());
            // the reminder starts at the disposalDate - retentionReminder days
        }

        record.setRuleDatesAndUpdateGlobalRetentionDetails(rule.getId(),
                GregorianCalendar.from(ZonedDateTime.of(cutoffDate, ZoneId.systemDefault())),
                GregorianCalendar.from(ZonedDateTime.of(disposalDate, ZoneId.systemDefault())),
                startReminderDate != null
                        ? GregorianCalendar.from(ZonedDateTime.of(startReminderDate, ZoneId.systemDefault()))
                        : null);
        if (save) {
            record.save(session);
        }
        // if there is no delay or the delay period has already expired
        if (rule.getBeginDealyAsPeriod().isZero() || record.getMinCutoffAt().getTime().before(cDate)) {
            // start retention if still active otherwise stop it
            if (record.getMaxRetentionAt() == null || record.getMaxRetentionAt().getTime().after(cDate)) {
                startRetention(record, rule, true, session);
            } else {
                endRetention(record, rule, session);
            }
        }
    }

    @Override
    public void startRetention(Record record, RetentionRule rule, boolean save, CoreSession session) {
        executeRuleAction(ActionType.BEGIN, rule, record.getDoc(), session);
        if (!RETENTION_STATE.active.name().equals(record.getStatus())) {
            record.setStatus(RETENTION_STATE.active.name());
            notifyEvent(session, RETENTION_ACTIVE_EVENT, record.getDoc());
            if (save) {
                record.save(session);
            }
        }
    }

    @Override
    public RetentionRule getRetentionRule(String ruleId, CoreSession session) throws NuxeoException {
        if (ruleId == null) {
            throw new NuxeoException("Can not attch null rule");
        }
        RetentionRuleDescriptor staticRule = getDescriptor(RULES_EP, ruleId);
        if (staticRule != null) {
            return new RetentionRule(staticRule);
        }
        DocumentModel dynamicRuleDoc;
        // trying to fetch a dynamic rule
        try {
            dynamicRuleDoc = session.getDocument(new IdRef(ruleId));
        } catch (DocumentNotFoundException e) {
            log.error("Can not find dynamic rule with ID: " + ruleId);
            throw new NuxeoException(e);
        }
        return new RetentionRule(dynamicRuleDoc);

    }

    @Override
    public String createOrUpdateDynamicRuleOnDocument(String beginDelayPeriod, String retentionPeriod,
            int retentionReminder, String beginAction, String endAction, String beginCondExpression,
            String beginCondEvent, String endCondExpression, DocumentModel doc, CoreSession session) {
        if (!doc.hasFacet(RETENTION_RULE_FACET)) {
            doc.addFacet(RETENTION_RULE_FACET); // else is just updating an existing rule
        }
        doc.setPropertyValue(RetentionRule.RULE_ID_PROPERTY, doc.getId());
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_DELAY_PERIOD_PROPERTY, beginDelayPeriod); // should validate with
                                                                                                // a
        // regexp?
        doc.setPropertyValue(RetentionRule.RULE_RETENTION_DURATION_PERIOD_PROPERTY, retentionPeriod);
        doc.setPropertyValue(RetentionRule.RULE_RETENTION_REMINDER_PROPERTY, retentionReminder);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_ACTION_PROPERTY, beginAction);
        doc.setPropertyValue(RetentionRule.RULE_END_ACTION_PROPERTY, endAction);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_EXPRESSION_TYPE_PROPERTY, beginCondExpression);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_EVENT_PROPERTY, beginCondEvent);
        doc.setPropertyValue(RetentionRule.RULE_END_CONDITION_EXPRESSION_PROPERTY, endCondExpression);

        doc = session.saveDocument(doc);
        return doc.getId();

    }

    protected ELActionContext initActionContext(DocumentModel doc, CoreSession session) {
        // handles only filters that can be resolved in this context
        ELActionContext ctx = new ELActionContext(new ExpressionContext(), new ExpressionFactoryImpl());
        ctx.setDocumentManager(session);
        ctx.setCurrentPrincipal((NuxeoPrincipal) session.getPrincipal());
        ctx.setCurrentDocument(doc);
        return ctx;
    }

    protected Boolean evaluateConditionExpression(ELActionContext ctx, String expression, DocumentModel doc,
            Date dateToCheck, CoreSession session) {
        Calendar now = Calendar.getInstance();
        if (dateToCheck != null) {
            now.setTime(dateToCheck);
        }

        if (StringUtils.isEmpty(expression)) {
            return true;
        }
        ctx.putLocalVariable("currentDate", now);
        return ctx.checkCondition(expression);
    }

    protected OperationContext getExecutionContext(DocumentModel doc, CoreSession session) {
        OperationContext context = new OperationContext(session);
        context.put("document", doc);
        context.setCommit(false); // no session save at end
        context.setInput(doc);
        return context;
    }

    protected void executeRuleAction(ActionType actionType, RetentionRule rule, DocumentModel doc,
            CoreSession session) {
        String actionID = actionType == ActionType.BEGIN ? rule.getBeginAction() : rule.getEndAction();
        boolean hasSingleAction = StringUtils.isNotBlank(actionID);
        String[] actionIDs = actionType == ActionType.BEGIN ? rule.getBeginActions() : rule.getEndActions();
        boolean hasSeveralActions = actionIDs != null && actionIDs.length > 0;

        // We should not have both one single action (automation chain) and a list of operations
        if (hasSingleAction && hasSeveralActions) {
            throw new NuxeoException(
                    "Cannot have a rule with both single and multiple actions to run (rule id " + rule.getId());
        }

        if (!hasSingleAction && !hasSeveralActions) {
            return;
        }

        AutomationService automationService = Framework.getService(AutomationService.class);
        if (hasSingleAction) {
            // get base context
            OperationContext context = getExecutionContext(doc, session);
            try {
                automationService.run(context, actionID);
            } catch (OperationException e) {
                throw new NuxeoException("Error running chain: " + actionID, e);
            }
        } else {
            for(String operationId : actionIDs) {
                // Do not lock document if already locked, and unlock if already unlocked (triggers an error)
                // Also, if it's time to delete, unlock it first, etc.
                // (more generally, be ready to handle specific operations and context)
                switch(operationId) {
                case LockDocument.ID:
                    if(doc.isLocked()) {
                        continue;
                    }
                    break;
                    
                case UnlockDocument.ID:
                    if(!doc.isLocked()) {
                        continue;
                    }
                    break;
                    
                case DeleteDocument.ID:
                case TrashDocument.ID:
                    if(doc.isLocked()) {
                        session.removeLock(doc.getRef());
                        doc = session.getDocument(doc.getRef());
                    }
                    break;
                }
                OperationContext context = getExecutionContext(doc, session);
                try {
                    automationService.run(context, operationId);
                } catch (OperationException e) {
                    throw new NuxeoException("Error running operation: " + operationId, e);
                }
            }
        }

    }

    protected void executeRuleAction(String actionId, DocumentModel doc, CoreSession session) {
        if (StringUtils.isEmpty(actionId)) {
            return;
        }
        // get base context
        OperationContext context = getExecutionContext(doc, session);
        AutomationService automationService = Framework.getService(AutomationService.class);
        try {
            automationService.run(context, actionId);
        } catch (OperationException e) {
            throw new NuxeoException("Error running chain: " + actionId, e);
        }

    }

    protected void notifyEvent(CoreSession session, String eventId, DocumentModel doc) throws NuxeoException {
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        ctx.setCategory(RETENTION_CATEGORY_EVENT);
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, session.getRepositoryName());
        ctx.setProperty(CoreEventConstants.SESSION_ID, session.getSessionId());
        Event event = ctx.newEvent(eventId);
        Framework.getService(EventService.class).fireEvent(event);
    }
}
