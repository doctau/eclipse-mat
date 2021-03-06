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
package org.eclipse.mat.ui.rcp.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.mat.ui.rcp.Messages;
import org.eclipse.mat.ui.rcp.RCPPlugin;
import org.eclipse.mat.util.RegistryReader;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

/**
 * Action needed to create a preference dialog w/o all preference page
 * contributions.
 */
public class OpenPreferenceAction extends Action
{
    private static final Set<String> ALLOWED_IDS = new HashSet<String>(Arrays.asList(new String[] {
                    "org.eclipse.ui.net.NetPreferences", //$NON-NLS-1$
                    "org.eclipse.ui.preferencePages.Workbench", 
                    "org.eclipse.ui.preferencePages.Keys", 
                    "org.eclipse.ui.preferencePages.Views",
                    "org.eclipse.ui.preferencePages.ColorsAndFonts",
                    // "org.eclipse.ui.preferencePages.ContentTypes", 
                    "org.eclipse.update.internal.ui.preferences.MainPreferencePage"})); //$NON-NLS-1$
    private static final String MAT_PREFIX = "org.eclipse.mat."; //$NON-NLS-1$

    PreferenceRegistry reg;

    public OpenPreferenceAction()
    {
        super(Messages.OpenPreferenceAction_Preferences);
    }

    @Override
    public void run()
    {
        if (reg == null)
            reg = new PreferenceRegistry();

        PreferenceManager manager = new PreferenceManager('/');
        // Recreate tree structure
        Map<String, Node>nodes = new LinkedHashMap<String, Node>();
        for (Node node : reg.delegates())
        {
            node.subNode = false;
            for (IPreferenceNode subNode : node.getSubNodes())
                node.remove(subNode.getId());
            nodes.put(node.getId(), node);
        }
        for (Node node : reg.delegates())
        {
            if (node.getCategory() != null && nodes.containsKey(node.getCategory()))
            {
                nodes.get(node.getCategory()).add(node);
                node.subNode = true;
            }
        }
        List<Node> toSort = new ArrayList<Node>();
        for (Node node : nodes.values())
        {
            if (!node.subNode)
                toSort.add(node);
        }
        Collections.sort(toSort, new Comparator<Node>()
        {

            public int compare(Node object1, Node object2)
            {
                return object1.getLabelText().compareTo(object2.getLabelText());
            }

        });
        for (Node node : toSort)
        {
            manager.addToRoot(node);
        }

        PreferenceDialog dialog = new PreferenceDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                        manager);
        dialog.open();
    }

    private static class Node extends PreferenceNode
    {
        private IConfigurationElement configElement;
        private boolean subNode;

        public Node(String id, IConfigurationElement configurationElement)
        {
            super(id);
            this.configElement = configurationElement;
        }

        public String getLabelText()
        {
            return configElement.getAttribute("name"); //$NON-NLS-1$
        }

        public String getCategory()
        {
            return configElement.getAttribute("category"); //$NON-NLS-1$
        }

        public void createPage()
        {
            IWorkbenchPreferencePage page;
            try
            {
                page = (IWorkbenchPreferencePage) configElement.createExecutableExtension("class"); //$NON-NLS-1$
            }
            catch (CoreException e)
            {
                throw new RuntimeException(e);
            }

            page.init(PlatformUI.getWorkbench());
            if (getLabelImage() != null)
            {
                page.setImageDescriptor(getImageDescriptor());
            }
            page.setTitle(getLabelText());
            setPage(page);
        }
    }

    private static class PreferenceRegistry extends RegistryReader<Node>
    {

        public PreferenceRegistry()
        {
            init(RCPPlugin.getDefault().getExtensionTracker(), PlatformUI.PLUGIN_ID + ".preferencePages"); //$NON-NLS-1$
        }

        @Override
        protected Node createDelegate(IConfigurationElement configElement) throws CoreException
        {
            String id = configElement.getAttribute("id"); //$NON-NLS-1$
            return id.startsWith(MAT_PREFIX) || ALLOWED_IDS.contains(id) ? new Node(id, configElement) : null; 
        }

        @Override
        protected void removeDelegate(Node delegate)
        {}

    }
}
