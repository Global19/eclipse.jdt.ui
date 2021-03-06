/*******************************************************************************
 * Copyright (c) 2007, 2013 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import java.util.Hashtable;
import java.util.Map;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.jdt.internal.ui.fix.CodeFormatCleanUp;

/**
 * @since 3.4
 */
public class MultiFormatAction extends CleanUpAction {

	public MultiFormatAction(IWorkbenchSite site) {
		super(site);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.actions.CleanUpAction#createCleanUps(org.eclipse.jdt.core.ICompilationUnit[])
	 */
	@Override
	protected ICleanUp[] getCleanUps(ICompilationUnit[] units) {
		Map<String, String> settings= new Hashtable<>();
		settings.put(CleanUpConstants.FORMAT_SOURCE_CODE, CleanUpOptions.TRUE);

		return new ICleanUp[] {
				new CodeFormatCleanUp(settings)
		};
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.actions.CleanUpAction#getActionName()
	 */
	@Override
	protected String getActionName() {
		return ActionMessages.MultiFormatAction_name;
	}

}
