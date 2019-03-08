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
 *     Thibaud Arguillere
 */

package org.nuxeo.ecm.retention.adapter;

import java.io.Serializable;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.retention.service.RetentionRuleConditionDescriptor;
import org.nuxeo.ecm.retention.service.RetentionRuleDescriptor;
import org.nuxeo.ecm.retention.service.Rule;

public class RetentionRule implements Rule {

    public static final Log log = LogFactory.getLog(RetentionRule.class);

    public static final String RULE_ID_PROPERTY = "rule:ruleId";

    public static final String RULE_BEGIN_CONDITION_PROPERTY = "rule:beginCondition";

    public static final String RULE_BEGIN_CONDITION_EXPRESSION_TYPE_PROPERTY = "rule:beginCondition/expression";

    public static final String RULE_BEGIN_CONDITION_EVENT_PROPERTY = "rule:beginCondition/event";

    public static final String RULE_END_CONDITION_PROPERTY = "rule:endCondition";

    public static final String RULE_BEGIN_DELAY_PERIOD_PROPERTY = "rule:beginDelayPeriod";

    public static final String RULE_RETENTION_DURATION_PERIOD_PROPERTY = "rule:retentionDurationPeriod";

    public static final String RULE_RETENTION_REMINDER_PROPERTY = "rule:retentionReminderInDays";

    public static final String RULE_BEGIN_ACTION_PROPERTY = "rule:beginAction";

    public static final String RULE_BEGIN_ACTIONS_PROPERTY = "rule:beginActions";

    public static final String RULE_END_ACTION_PROPERTY = "rule:endAction";

    public static final String RULE_END_ACTIONS_PROPERTY = "rule:endActions";

    public static final String RULE_END_CONDITION_EXPRESSION_PROPERTY = "rule:endCondition/expression";

    protected String id;

    protected RetentionRuleCondition beginCondition;

    protected Period beginDelay;

    protected Period retentionDuration;

    protected int retentionReminder;

    protected String beginAction;

    protected String[] beginActions;

    protected String endAction;

    protected String[] endActions;

    protected RetentionRuleCondition endCondition;

    public RetentionRule(RetentionRuleDescriptor ruleDescriptor) {
        this.id = ruleDescriptor.getId();
        this.beginCondition = new RetentionRuleCondition(ruleDescriptor.getBeginCondition());
        this.beginDelay = parsePeriod(ruleDescriptor.getBeginDelay());
        this.retentionDuration = parsePeriod(ruleDescriptor.getRetentionDuration());
        this.retentionReminder = ruleDescriptor.getRetentionReminderDays();
        this.beginAction = ruleDescriptor.getBeginAction();
        this.beginActions = ruleDescriptor.getBeginActions();
        this.endAction = ruleDescriptor.getEndAction();
        this.endActions = ruleDescriptor.getEndActions();
        this.endCondition = new RetentionRuleCondition(ruleDescriptor.getEndCondition());

        this.checkRuleLooksValid();

    }

    @SuppressWarnings("unchecked")
    public RetentionRule(DocumentModel doc) {
        this.id = (String) doc.getId();
        this.beginCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_BEGIN_CONDITION_PROPERTY));
        this.endCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_END_CONDITION_PROPERTY));
        this.beginDelay = parsePeriod((String) doc.getPropertyValue(RULE_BEGIN_DELAY_PERIOD_PROPERTY));
        this.retentionDuration = parsePeriod((String) doc.getPropertyValue(RULE_RETENTION_DURATION_PERIOD_PROPERTY));
        Long retReminder = (Long) doc.getPropertyValue(RULE_RETENTION_REMINDER_PROPERTY);
        this.retentionReminder = retReminder == null ? 0 : retReminder.intValue();
        this.beginAction = (String) doc.getPropertyValue(RULE_BEGIN_ACTION_PROPERTY);
        this.beginActions = (String[]) doc.getPropertyValue(RULE_BEGIN_ACTIONS_PROPERTY);
        this.endAction = (String) doc.getPropertyValue(RULE_END_ACTION_PROPERTY);
        this.endActions = (String[]) doc.getPropertyValue(RULE_END_ACTIONS_PROPERTY);
        
        this.checkRuleLooksValid();

    }

    /**
     * We don't allow for both a single beginAction and several beginActions.
     * (mainly because no order between each of them have been implemented)
     * 
     * @since 10.10
     */
    protected void checkRuleLooksValid() {
        if(StringUtils.isNotBlank(beginAction) && beginActions != null && beginActions.length > 0) {
            throw new NuxeoException("Rule Validation error with rule ID " + id + ": It is not possible to set both a single beginAction and 1-n beginActions");
        }
        if(StringUtils.isNotBlank(endAction) && endActions != null && endActions.length > 0) {
            throw new NuxeoException("Rule Validation error with rule ID " + id + ": It is not possible to set both a single endAction and 1-n endActions");
        }
        
        // . . . other validation (say: we do have an end date, ...)
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public RetentionRuleCondition getBeginCondition() {
        return beginCondition;
    }

    @Override
    public String getBeginDelay() {
        return beginDelay.toString();
    }

    public Period getBeginDealyAsPeriod() {
        return beginDelay;
    }

    @Override
    public String getBeginAction() {
        return beginAction;
    }

    @Override
    public String[] getBeginActions() {
        return beginActions;
    }

    @Override
    public String getEndAction() {
        return endAction;
    }

    @Override
    public String[] getEndActions() {
        return endActions;
    }

    @Override
    public RetentionRuleCondition getEndCondition() {
        return endCondition;
    }

    @Override
    public String getRetentionDuration() {
        return retentionDuration.toString();
    }

    public Period getRetentionDurationAsPeriod() {
        return retentionDuration;
    }

    @Override
    public int getRetentionReminderDays() {
        return retentionReminder;
    }

    // ToDo - validate regexp?
    protected Period parsePeriod(String retentionPeriod) {
        if (StringUtils.isBlank(retentionPeriod)) {
            return Period.ZERO;
        }
        // this has to match a format like 1Y2M3D
        retentionPeriod = retentionPeriod.startsWith("P") ? retentionPeriod : "P" + retentionPeriod;
        try {
            return Period.parse(retentionPeriod);
        } catch (DateTimeParseException e) {
            throw new NuxeoException("Invalid retention duration: " + retentionPeriod, e);
        }
    }

    public class RetentionRuleCondition implements RuleCondition {

        public RetentionRuleCondition(RetentionRuleConditionDescriptor conditionDesc) {
            if (conditionDesc != null) {
                this.expression = conditionDesc.getExpression();
                this.event = conditionDesc.getEvent();
            }
        }

        public RetentionRuleCondition(Map<String, Serializable> conditionProperty) {
            this.expression = (String) conditionProperty.get("expression");
            this.event = (String) conditionProperty.get("event");
        }

        protected String expression = null;

        protected String event = null;

        @Override
        public String getEvent() {
            return event;
        }

        @Override
        public String getExpression() {
            return expression;
        }

    }

}
