package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.swt.custom.LineBackgroundEvent;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;

import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.jdt.core.dom.CatchClause;

import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;



public class LinePainter implements IPainter, LineBackgroundListener {
	
	private StyledText fTextWidget;
	private Color fHighlightColor;
	private boolean fIsActive= false;
	
	private int fLastOffset = -1;
	private int fCurrentOffset = -1;
	private int fLineNumber= -1;
	private int[] fLine= { -1, -1 };
	
	
	public LinePainter(ISourceViewer sourceViewer) {
		fTextWidget= sourceViewer.getTextWidget();
	}
	
	public void setHighlightColor(Color highlightColor) {
		fHighlightColor= highlightColor;
	}
	
	/*
	 * @see LineBackgroundListener#lineGetBackground(LineBackgroundEvent)
	 */
	public void lineGetBackground(LineBackgroundEvent event) {
		/* Don't use cached line information because of batched redrawing events. */
		
		if (fTextWidget != null) {
			
			int caret= fTextWidget.getCaretOffset();
			int length= event.lineText.length();
			
			if (event.lineOffset <= caret && caret <= event.lineOffset + length && fIsActive )
				event.lineBackground= fHighlightColor;
			else
				event.lineBackground= fTextWidget.getBackground();
		}
	}
	
	private boolean isSameLine() {
		StyledTextContent content= fTextWidget.getContent();
		
		int offset= fTextWidget.getCaretOffset();
		int length= content.getCharCount();
		if (offset > length)
			offset= length;
		
		fLineNumber= content.getLineAtOffset(offset);
		fCurrentOffset= content.getOffsetAtLine(fLineNumber);
		if (fLastOffset != fCurrentOffset) {
			fLastOffset= fCurrentOffset;
			return false;
		}
		
		return true;
	}
		
	private void updateHighlightLine() {
		StyledTextContent content= fTextWidget.getContent();
		fLine[0]= fCurrentOffset;
		try {
			fLine[1]= content.getOffsetAtLine(fLineNumber + 1);
		} catch (IllegalArgumentException x) {
			fLine[1]= -1;
		}
	}
	
	private void clearHighlightLine() {
		if (fLine[0] <=  fTextWidget.getCharCount() )
			drawHighlightLine();
	}
	
	private void drawHighlightLine() {
		if (fLine[1] >= fTextWidget.getCharCount()) 
			fLine[1]= -1;
		
		if (fLine[1] == -1) {
			
			Point upperLeft= fTextWidget.getLocationAtOffset(fLine[0]);
			int width= fTextWidget.getClientArea().width;
			int height= fTextWidget.getLineHeight();			
			fTextWidget.redraw(upperLeft.x, upperLeft.y, width, height, false);
			
		} else {
			fTextWidget.redrawRange(fLine[0], fLine[1] - fLine[0], true);
		}
	}
	
	/*
	 * @see IPainter#deactivate(boolean)
	 */
	public void deactivate(boolean redraw) {
		if (fIsActive) {
			fIsActive= false;
			fTextWidget.removeLineBackgroundListener(this);
			if (redraw)
				drawHighlightLine();
		}
	}
	
	/*
	 * @see IPainter#dispose()
	 */
	public void dispose() {
		fTextWidget= null;
	}
	
	/*
	 * @see IPainter#paint(int)
	 */
	public void paint(int reason) {
		
		if (!fIsActive) {
			fIsActive= true;
			fTextWidget.addLineBackgroundListener(this);
		}
		
		if (!isSameLine() ) {
			
			if (fLine[0] != -1 )
				clearHighlightLine();
			
			updateHighlightLine();
			
			drawHighlightLine();
		}
	}
	
	/*
	 * @see IPainter#setPositionManager(IPositionManager)
	 */
	public void setPositionManager(IPositionManager manager) {
	}
}