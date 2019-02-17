/*
 *  (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *   Contributors:
 *       Ryan McCue
 *
 */

package org.nuxeo.ecm.retention.adapter;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.adapter.DocumentAdapterFactory;

public class RecordFactory implements DocumentAdapterFactory {

    @Override
    public Record getAdapter(DocumentModel doc, Class<?> itf) {
        if (doc.hasFacet("Record")) {
            return new Record(doc);
        } else {
            return null;
        }
    }
}
