/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.bindings.TriggerSequence;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.contentassist.TextContentAssistInvocationContext;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

import org.eclipse.jdt.internal.corext.Assert;

/**
 * A content assist processor that aggregates the proposals of the
 * {@link org.eclipse.jface.text.contentassist.ICompletionProposalComputer}s contributed via the
 * <code>org.eclipse.jdt.ui.javaCompletionProposalComputer</code> extension point.
 * <p>
 * Subclasses may extend:
 * <ul>
 * <li><code>createContext</code> to modify the context object passed to the computers</li>
 * <li><code>createProgressMonitor</code> to change the way progress is reported</li>
 * <li><code>filterAndSort</code> to add sorting and filtering</li>
 * <li><code>getContextInformationValidator</code> to add context validation (needed if any
 * contexts are provided)</li>
 * <li><code>getErrorMessage</code> to change error reporting</li>
 * </ul>
 * </p>
 * 
 * @since 3.2
 */
public class ContentAssistProcessor implements IContentAssistProcessor {

	private static final Comparator<CompletionProposalCategory> ORDER_COMPARATOR= new Comparator<CompletionProposalCategory>() {
		public int compare(CompletionProposalCategory d1, CompletionProposalCategory d2) {
			return d1.getSortOrder() - d2.getSortOrder();
		}
	};
	
	private final List<CompletionProposalCategory> fCategories;
	private final String fPartition;
	private final ContentAssistant fAssistant;
	
	private char[] fCompletionAutoActivationCharacters;
	
	/* cycling stuff */
	private int fRepetition= -1;
	private List<List<CompletionProposalCategory>> fCategoryIteration= null;
	private String fIterationGesture= null;
	
	public ContentAssistProcessor(ContentAssistant assistant, String partition) {
		Assert.isNotNull(partition);
		Assert.isNotNull(assistant);
		fPartition= partition;
		fCategories= CompletionProposalComputerRegistry.getDefault().getProposalCategories();
		fAssistant= assistant;
		fAssistant.addCompletionListener(new ICompletionListener() {
			
			/*
			 * @see org.eclipse.jface.text.contentassist.ICompletionListener#assistSessionStarted(org.eclipse.jface.text.contentassist.ContentAssistEvent)
			 */
			public void assistSessionStarted(ContentAssistEvent event) {
				if (event.processor != ContentAssistProcessor.this)
					return;
				
				fCategoryIteration= getCategoryIteration();
				fRepetition= 0;
				fIterationGesture= getIterationGesture();
				if (fCategoryIteration.size() == 1) {
					event.assistant.setCyclingMode(false);
					event.assistant.setShowEmptyList(false);
				} else {
					event.assistant.setCyclingMode(true);
					event.assistant.setMessage(createIterationMessage());
					event.assistant.setShowEmptyList(true);
				}
			}
			
			/*
			 * @see org.eclipse.jface.text.contentassist.ICompletionListener#assistSessionEnded(org.eclipse.jface.text.contentassist.ContentAssistEvent)
			 */
			public void assistSessionEnded(ContentAssistEvent event) {
				if (event.processor != ContentAssistProcessor.this)
					return;
				
				fCategoryIteration= null;
				fRepetition= -1;
				fIterationGesture= null;
				event.assistant.setShowEmptyList(false);
				event.assistant.setCyclingMode(false);
			}
			
		});
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeCompletionProposals(org.eclipse.jface.text.ITextViewer, int)
	 */
	public final ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IProgressMonitor monitor= createProgressMonitor();
		monitor.beginTask(JavaTextMessages.ContentAssistProcessor_computing_proposals, fCategories.size() + 1);

		TextContentAssistInvocationContext context= createContext(viewer, offset);
		
		monitor.subTask(JavaTextMessages.ContentAssistProcessor_collecting_proposals);
		List<ICompletionProposal> proposals= collectProposals(viewer, offset, monitor, context);

		monitor.subTask(JavaTextMessages.ContentAssistProcessor_sorting_proposals);
		List<ICompletionProposal> filtered= filterAndSortProposals(proposals, context, monitor);
		
		ICompletionProposal[] result= filtered.toArray(new ICompletionProposal[filtered.size()]);
		monitor.done();
		return result;
	}

	private List<ICompletionProposal> collectProposals(ITextViewer viewer, int offset, IProgressMonitor monitor, TextContentAssistInvocationContext context) {
		List<ICompletionProposal> proposals= new ArrayList<ICompletionProposal>();
		List<CompletionProposalCategory> providers= getCategories();
		for (CompletionProposalCategory cat : providers)
			proposals.addAll(cat.computeCompletionProposals(context, fPartition, new SubProgressMonitor(monitor, 1)));
		
		return proposals;
	}

	/**
	 * Filters and sorts the proposals. The passed list may be modified and returned, or a new list
	 * may be created and returned.
	 * 
	 * @param proposals the list of collected proposals
	 * @param context the content assit context
	 * @param monitor a progress monitor
	 * @return the list of filtered and sorted proposals, ready for display
	 */
	protected List<ICompletionProposal> filterAndSortProposals(List<ICompletionProposal> proposals, TextContentAssistInvocationContext context, IProgressMonitor monitor) {
		return proposals;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeContextInformation(org.eclipse.jface.text.ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		IProgressMonitor monitor= createProgressMonitor();
		monitor.beginTask(JavaTextMessages.ContentAssistProcessor_computing_contexts, fCategories.size() + 1);
		
		monitor.subTask(JavaTextMessages.ContentAssistProcessor_collecting_contexts);
		List<IContextInformation> proposals= collectContextInformation(viewer, offset, monitor);

		monitor.subTask(JavaTextMessages.ContentAssistProcessor_sorting_contexts);
		List<IContextInformation> filtered= filterAndSortContextInformation(proposals, monitor);
		
		IContextInformation[] result= filtered.toArray(new IContextInformation[filtered.size()]);
		monitor.done();
		return result;
	}

	private List<IContextInformation> collectContextInformation(ITextViewer viewer, int offset, IProgressMonitor monitor) {
		List<IContextInformation> proposals= new ArrayList<IContextInformation>();
		TextContentAssistInvocationContext context= createContext(viewer, offset);
		
		List<CompletionProposalCategory> providers= getCategories();
		for (CompletionProposalCategory cat : providers)
			proposals.addAll(cat.computeContextInformation(context, fPartition, new SubProgressMonitor(monitor, 1)));
		
		return proposals;
	}

	/**
	 * Filters and sorts the context information objects. The passed list may be modified and
	 * returned, or a new list may be created and returned.
	 * 
	 * @param contexts the list of collected proposals
	 * @param monitor a progress monitor
	 * @return the list of filtered and sorted proposals, ready for display
	 */
	protected List<IContextInformation> filterAndSortContextInformation(List<IContextInformation> contexts, IProgressMonitor monitor) {
		return contexts;
	}

	/**
	 * Sets this processor's set of characters triggering the activation of the
	 * completion proposal computation.
	 *
	 * @param activationSet the activation set
	 */
	public final void setCompletionProposalAutoActivationCharacters(char[] activationSet) {
		fCompletionAutoActivationCharacters= activationSet;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public final char[] getCompletionProposalAutoActivationCharacters() {
		return fCompletionAutoActivationCharacters;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/**
	 * Creates a progress monitor.
	 * <p>
	 * The default implementation creates a
	 * <code>NullProgressMonitor</code>.
	 * </p>
	 * 
	 * @return a progress monitor
	 */
	protected IProgressMonitor createProgressMonitor() {
		return new NullProgressMonitor();
	}

	/**
	 * Creates the context that is passed to the completion proposal
	 * computers.
	 * 
	 * @param viewer the viewer that content assist is invoked on
	 * @param offset the content assist offset
	 * @return the context to be passed to the computers
	 */
	protected TextContentAssistInvocationContext createContext(ITextViewer viewer, int offset) {
		return new TextContentAssistInvocationContext(viewer, offset);
	}

	private List<CompletionProposalCategory> getCategories() {
		if (fCategoryIteration == null)
			return fCategories;
		
		int iteration= fRepetition % fCategoryIteration.size();
		fAssistant.setMessage(createIterationMessage());
		fAssistant.setEmptyMessage(createEmptyMessage());
		fRepetition++;
		
		return fCategoryIteration.get(iteration);
	}

	private List<List<CompletionProposalCategory>> getCategoryIteration() {
		List<List<CompletionProposalCategory>> sequence= new ArrayList<List<CompletionProposalCategory>>();
		sequence.add(getDefaultCategories());
		for (CompletionProposalCategory cat : getSeparateCategories())
			sequence.add(Collections.singletonList(cat));
		return sequence;
	}

	private List<CompletionProposalCategory> getDefaultCategories() {
		// default mix - enable all included computers
		List<CompletionProposalCategory> included= new ArrayList<CompletionProposalCategory>();
		for (CompletionProposalCategory category : fCategories)
			if (category.isIncluded() && category.hasComputers(fPartition))
				included.add(category);
		return included;
	}

	private List<CompletionProposalCategory> getSeparateCategories() {
		ArrayList<CompletionProposalCategory> sorted= new ArrayList<CompletionProposalCategory>();
		for (CompletionProposalCategory category : fCategories)
			if (category.isSeparateCommand() && category.hasComputers(fPartition))
				sorted.add(category);
		Collections.sort(sorted, ORDER_COMPARATOR);
		return sorted;
	}
	
	private String createEmptyMessage() {
		final MessageFormat format= new MessageFormat(JavaTextMessages.ContentAssistProcessor_empty_message);
		Object[] args= {getCategoryLabel(fRepetition)};
		String message= format.format(args);
		return message;
	}
	
	private String createIterationMessage() {
		final MessageFormat format= new MessageFormat(JavaTextMessages.ContentAssistProcessor_toggle_affordance_update_message);
		String current= getCategoryLabel(fRepetition);
		String next= getCategoryLabel(fRepetition + 1);
		Object[] args= { current, fIterationGesture, next };
		String message= format.format(args);
		return message;
	}
	
	private String getCategoryLabel(int repetition) {
		int iteration= repetition % fCategoryIteration.size();
		if (iteration == 0)
			return JavaTextMessages.ContentAssistProcessor_defaultProposalCategory;
		return toString(fCategoryIteration.get(iteration).get(0));
	}
	
	private String toString(CompletionProposalCategory category) {
		return category.getName().replaceAll("&", ""); //$NON-NLS-1$ //$NON-NLS-2$;
	}

	private String getIterationGesture() {
		final IBindingService bindingSvc= (IBindingService) PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		TriggerSequence[] triggers= bindingSvc.getActiveBindingsFor(getContentAssistCommand());
		return triggers.length > 0 ? 
				  MessageFormat.format(JavaTextMessages.ContentAssistProcessor_toggle_affordance_press_gesture, new Object[] { triggers[0].format() })
				: JavaTextMessages.ContentAssistProcessor_toggle_affordance_click_gesture;
	}

	private ParameterizedCommand getContentAssistCommand() {
		final ICommandService commandSvc= (ICommandService) PlatformUI.getWorkbench().getAdapter(ICommandService.class);
		final Command command= commandSvc.getCommand(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
		ParameterizedCommand pCmd= new ParameterizedCommand(command, null);
		return pCmd;
	}
	
}
