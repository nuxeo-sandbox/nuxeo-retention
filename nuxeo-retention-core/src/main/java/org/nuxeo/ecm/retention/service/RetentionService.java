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

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

public interface RetentionService {

    public static final String RECORD_FACET = "Record";

    public static final Long batchSize = 10L;// maybe configurable through a property

    /**
     * Attaches the given rule to the document using an unrestricted session. 
     * 
     * @param ruleId
     * @param doc
     * @param session
     * @since 9.2
     */
    void attachRule(String ruleId, DocumentModel doc, CoreSession session);

    /**
     * Performs the give query using an unrestricted session and attaches the given rule to the documents
     * 
     * @param ruleId
     * @param query
     * @param session
     * @since 9.2
     */
    void attachRule(String ruleId, String query, CoreSession session);

}
