/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import org.eclipse.jdt.ui.tests.dialogs.DialogsTest;
import org.eclipse.jdt.ui.tests.dialogs.PreferencesTest;
import org.eclipse.jdt.ui.tests.dialogs.WizardsTest;

/**
 * Test all areas of the UI.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
	PreferencesTest.class,
	WizardsTest.class,
	DialogsTest.class
	})
public class UIInteractiveSuite {
}
