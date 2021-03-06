/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.views;

import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;

public class SnapshotDetailsView extends ContentOutline
{
    @Override
    protected boolean isImportant(IWorkbenchPart part)
    {
        return (part instanceof MultiPaneEditor) || (part instanceof SnapshotHistoryView);
    }
}
