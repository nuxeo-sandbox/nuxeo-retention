/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thibaud Arguillere
 */
package org.nuxeo.ecm.retention;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 10.10
 */
public class TestUtils {

    public static DocumentModelList createDocumentsInFolder(CoreSession session, String path, String folderName,
            int count) throws InterruptedException {
        // Create a folder whose documents should all be put under retention
        DocumentModel folderRetention = session.createDocumentModel(path, folderName, "Folder");
        folderRetention = session.createDocument(folderRetention);

        DocumentModelList docs = new DocumentModelListImpl();
        DocumentModel doc;
        for (int i = 0; i < count; i++) {
            doc = session.createDocumentModel("/folderRetention", "file-" + i, "File");
            doc = session.createDocument(doc);
            docs.add(doc);
        }

        session.save();
        TestUtils.waitForWorkers();

        return docs;
    }

    public static void waitForWorkers() throws InterruptedException {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        WorkManager workManager = Framework.getService(WorkManager.class);

        final boolean allCompleted = workManager.awaitCompletion(100, TimeUnit.SECONDS);
        assertTrue(allCompleted);
    }
}
