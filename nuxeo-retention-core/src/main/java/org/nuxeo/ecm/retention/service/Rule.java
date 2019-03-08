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

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.Descriptor;

/**
 * 
 * @since 9.2
 */
public interface Rule extends Descriptor {

    RuleCondition getBeginCondition();

    String getBeginDelay();
    
    String getRetentionDuration();
    
    int getRetentionReminderDays();

    
    /*
     * In singular word, we have a single automation chain
     */
    String getBeginAction();

    String getEndAction();
    
    /*
     * In plural words, we have a list of operations
     */
    String[] getBeginActions();

    String[] getEndActions();

    RuleCondition getEndCondition();

    public interface RuleCondition {

        String getExpression();

        String getEvent();

    }

}