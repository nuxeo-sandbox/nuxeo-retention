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

package org.nuxeo.ecm.retention.adapter;

import java.io.Serializable;
import java.util.Map;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.scripting.Expression;
import org.nuxeo.ecm.automation.core.scripting.Scripting;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.retention.service.RetentionRuleConditionDescriptor;
import org.nuxeo.ecm.retention.service.RetentionRuleDescriptor;
import org.nuxeo.ecm.retention.service.Rule;

public class RetentionRule implements Rule {

    public static final String RULE_ID_PROPERTY = "rule:ruleId";

    public static final String RULE_BEGIN_CONDITION_PROPERTY = "rule:beginCondition";

    public static final String RULE_BEGIN_CONDITION_EXPRESSION_TYPE_PROPERTY = "rule:beginCondition/expression";

    public static final String RULE_BEGIN_CONDITION_EVENT_PROPERTY = "rule:beginCondition/event";

    public static final String RULE_END_CONDITION_PROPERTY = "rule:endCondition";

    public static final String RULE_BEGIN_DELAY_PROPERTY = "rule:beginDelayInMillis";

    public static final String RULE_RETENTION_DURATION_PROPERTY = "rule:retentionDurationInMillis";

    public static final String RULE_RETENTION_REMINDER_PROPERTY = "rule:retentionReminderInDays";

    public static final String RULE_BEGIN_ACTION_PROPERTY = "rule:beginAction";

    public static final String RULE_END_ACTION_PROPERTY = "rule:endAction";

    public static final String RULE_END_CONDITION_EXPRESSION_PROPERTY = "rule:endCondition/expression";

    protected String id;

    protected RetentionRuleCondition beginCondition;

    protected Long beginDelay;

    protected Long retentionDuration;

    protected int retentionReminder;

    protected String beginAction;

    protected String endAction;

    protected RetentionRuleCondition endCondition;

    public RetentionRule(RetentionRuleDescriptor ruleDescriptor) {
        this.id = ruleDescriptor.getId();
        this.beginCondition = new RetentionRuleCondition(ruleDescriptor.getBeginCondition());
        this.beginDelay = ruleDescriptor.getBeginDelayInMillis();
        this.retentionDuration = ruleDescriptor.getRetentionDurationInMillis();
        this.retentionReminder = ruleDescriptor.getRetentionReminderDays();
        this.beginAction = ruleDescriptor.getBeginAction();
        this.endAction = ruleDescriptor.getEndAction();
        this.endCondition = new RetentionRuleCondition(ruleDescriptor.getEndCondition());
    }

    @SuppressWarnings("unchecked")
    public RetentionRule(DocumentModel doc) {
        this.id = (String) doc.getPropertyValue(RULE_ID_PROPERTY);
        this.beginCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_BEGIN_CONDITION_PROPERTY));
        this.endCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_END_CONDITION_PROPERTY));
        this.beginDelay = (Long) doc.getPropertyValue(RULE_BEGIN_DELAY_PROPERTY);
        this.retentionDuration = (Long) doc.getPropertyValue(RULE_RETENTION_DURATION_PROPERTY);
        this.retentionReminder = ((Long) doc.getPropertyValue(RULE_RETENTION_REMINDER_PROPERTY)).intValue();
        this.beginAction = (String) doc.getPropertyValue(RULE_BEGIN_ACTION_PROPERTY);
        this.endAction = (String) doc.getPropertyValue(RULE_END_ACTION_PROPERTY);

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
    public Long getBeginDelayInMillis() {
        return beginDelay;
    }

    @Override
    public String getBeginAction() {
        return beginAction;
    }

    @Override
    public String getEndAction() {
        return endAction;
    }

    @Override
    public RetentionRuleCondition getEndCondition() {
        return endCondition;
    }

    @Override
    public Long getRetentionDurationInMillis() {
        return retentionDuration;
    }

    @Override
    public int getRetentionReminderDays() {
        return retentionReminder;
    }


    public class RetentionRuleCondition implements RuleCondition {

        public RetentionRuleCondition(RetentionRuleConditionDescriptor conditionDesc) {
            this.expression = conditionDesc.getExpression();
            this.event = conditionDesc.getEvent();
        }

        public RetentionRuleCondition(Map<String, Serializable> conditionProperty) {
            this.expression = (String) conditionProperty.get("expression");
            this.event = (String) conditionProperty.get("event");
        }

        protected String expression;

        protected String event;

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
