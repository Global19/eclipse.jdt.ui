/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
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
package org.eclipse.jdt.ui.examples;

import java.util.HashMap;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.core.text.TextSearchMatchAccess;
import org.eclipse.search.core.text.TextSearchRequestor;
import org.eclipse.search.ui.text.FileTextSearchScope;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

import org.eclipse.jdt.core.IType;


/*
   A rename participant that updates type references in '*.special' files.


 	<extension point="org.eclipse.ltk.core.refactoring.renameParticipants">
	  <renameParticipant
	  	id="org.eclipse.jdt.ui.examples.MyRenameTypeParticipant"
	  	name="Rename participant for *.special files"
	  	class="org.eclipse.jdt.ui.examples.MyRenameTypeParticipant">
	  	<enablement>
	  	  <with variable="affectedNatures">
	  	    <iterate operator="or">
	  	      <equals value="org.eclipse.jdt.core.javanature"/>
	  	    </iterate>
	  	  </with>
	  	  <with variable="element">
		  	 <instanceof value="org.eclipse.jdt.core.IType"/>
	  	  </with>
	  	</enablement>
	  </renameParticipant>
	</extension>

 */


public class MyRenameTypeParticipant extends RenameParticipant {

	private IType fType;

	@Override
	protected boolean initialize(Object element) {
		fType= (IType) element;
		return true;
	}

	@Override
	public String getName() {
		return "My special file participant";  //$NON-NLS-1$
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) {
		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException {

		final HashMap<IFile, TextFileChange> changes= new HashMap<>();
		final String newName= getArguments().getNewName();

		// use the text search engine to find matches in my special files
		// in a real world implementation, clients would use their own, more precise search engine

		IResource[] roots= { fType.getJavaProject().getProject() };  // limit to the current project
		String[] fileNamePatterns= { "*.special" }; //$NON-NLS-1$ // all files with file suffix 'special'
		FileTextSearchScope scope= FileTextSearchScope.newSearchScope(roots , fileNamePatterns, false);
		Pattern pattern= Pattern.compile(fType.getElementName()); // only find the simple name of the type

		TextSearchRequestor collector= new TextSearchRequestor() {
			@Override
			public boolean acceptPatternMatch(TextSearchMatchAccess matchAccess) throws CoreException {
				IFile file= matchAccess.getFile();
				TextFileChange change= changes.get(file);
				if (change == null) {
					TextChange textChange= getTextChange(file); // an other participant already modified that file?
					if (textChange != null) {
						return false; // don't try to merge changes
					}
					change= new TextFileChange(file.getName(), file);
					change.setEdit(new MultiTextEdit());
					changes.put(file, change);
				}
				ReplaceEdit edit= new ReplaceEdit(matchAccess.getMatchOffset(), matchAccess.getMatchLength(), newName);
				change.addEdit(edit);
				change.addTextEditGroup(new TextEditGroup("Update type reference", edit)); //$NON-NLS-1$
				return true;
			}
		};
		TextSearchEngine.create().search(scope, collector, pattern, pm);

		if (changes.isEmpty())
			return null;

		CompositeChange result= new CompositeChange("My special file updates"); //$NON-NLS-1$
		for (TextFileChange textFileChange : changes.values()) {
			result.add(textFileChange);
		}
		return result;
	}
}