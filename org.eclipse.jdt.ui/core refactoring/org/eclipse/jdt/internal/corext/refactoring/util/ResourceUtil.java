package org.eclipse.jdt.internal.corext.refactoring.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;

public class ResourceUtil {
	
	private ResourceUtil(){
	}
	
	public static IFile[] getFiles(ICompilationUnit[] cus) throws JavaModelException{
		List files= new ArrayList(cus.length);
		for (int i= 0; i < cus.length; i++) {
			IResource resource= ResourceUtil.getResource(cus[i]);
			if (resource.getType() == IResource.FILE)
				files.add(resource);
		}
		return (IFile[]) files.toArray(new IFile[files.size()]);
	}

	public static IFile getFile(ICompilationUnit cu) throws JavaModelException{
		IResource resource= ResourceUtil.getResource(cu);
		if (resource.getType() == IResource.FILE)
			return (IFile)resource;
		else
			return null;
	}

	//----- other ------------------------------
			
	/**
	 * Finds an <code>IResource</code> for a given <code>ICompilationUnit</code>.
	 * If the parameter is a working copy then the <code>IResource</code> for
	 * the original element is returned.
	 */
	public static IResource getResource(ICompilationUnit cu) throws JavaModelException{
		return JavaModelUtil.toOriginal(cu).getResource();
	}


	/**
	 * Returns the <code>IResource</code> that the given <code>IMember</code> is defined in.
	 * @see #getResource
	 */
	public static IResource getResource(IMember member) throws JavaModelException{
		Assert.isTrue(!member.isBinary());
		return getResource(member.getCompilationUnit());
	}

	public static IResource getResource(Object o){
		if (o instanceof IResource)
			return (IResource)o;
		if (o instanceof IJavaElement)
			return getResource((IJavaElement)o);
		return null;	
	}

	private static IResource getResource(IJavaElement element){
		try {
			if (element.getElementType() == IJavaElement.COMPILATION_UNIT) 
				return getResource((ICompilationUnit) element);
			else if (element instanceof IOpenable) 
				return element.getResource();
			else	
				return null;	
		} catch (JavaModelException e) {
			if (e.isDoesNotExist())
				return null;
			JavaPlugin.log(e);
			return null;	
		}
	}
}
