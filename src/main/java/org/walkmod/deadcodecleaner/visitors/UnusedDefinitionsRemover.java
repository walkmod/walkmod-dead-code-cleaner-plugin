package org.walkmod.deadcodecleaner.visitors;

import java.util.Iterator;
import java.util.List;

import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.SymbolDefinition;
import org.walkmod.javalang.ast.SymbolReference;
import org.walkmod.javalang.ast.body.AnnotationDeclaration;
import org.walkmod.javalang.ast.body.ClassOrInterfaceDeclaration;
import org.walkmod.javalang.ast.body.EmptyTypeDeclaration;
import org.walkmod.javalang.ast.body.EnumDeclaration;
import org.walkmod.javalang.ast.body.FieldDeclaration;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalang.ast.body.ModifierSet;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.body.VariableDeclarator;
import org.walkmod.javalang.visitors.GenericVisitorAdapter;

public class UnusedDefinitionsRemover extends
		GenericVisitorAdapter<Boolean, Iterator<? extends Node>> {

	private CleanDeadDeclarationsVisitor<?> siblingsVisitor;

	public UnusedDefinitionsRemover(
			CleanDeadDeclarationsVisitor<?> siblingsVisitor) {
		this.siblingsVisitor = siblingsVisitor;
	}

	public Boolean visitTypeDeclaration(TypeDeclaration n,
			Iterator<? extends Node> it) {
		boolean removed = false;
		if (ModifierSet.isPrivate(n.getModifiers())) {
			List<SymbolReference> usages = n.getUsages();
			if (usages == null || usages.isEmpty()) {

				it.remove();
				removed = true;
				removeOrphanBodyReferences(it, n);
			} else {
				n.accept(siblingsVisitor, null);
			}
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}

	public Boolean visit(ClassOrInterfaceDeclaration n,
			Iterator<? extends Node> it) {
		if ((siblingsVisitor.getRemoveUnusedClasses() && !n.isInterface())
				|| siblingsVisitor.getRemoveUnusedInterfaces()
				&& n.isInterface()) {
			return visitTypeDeclaration(n, it);
		} else {
			n.accept(siblingsVisitor, null);
			return false;
		}
	}

	public Boolean visit(EnumDeclaration n, Iterator<? extends Node> it) {
		if(siblingsVisitor.getRemoveUnusedEnumerations()){
			return visitTypeDeclaration(n, it);
		}
		else{
			n.accept(siblingsVisitor, null);
			return false;
		}
	}

	public Boolean visit(AnnotationDeclaration n, Iterator<? extends Node> it) {
		if(siblingsVisitor.getRemoveUnusedAnnotationTypes()){
			return visitTypeDeclaration(n, it);
		}
		else{
			n.accept(siblingsVisitor, null);
			return false;
		}
	}

	public Boolean visit(EmptyTypeDeclaration n, Iterator<? extends Node> it) {
		return false;
	}

	public Boolean visit(MethodDeclaration n, Iterator<? extends Node> it) {
		boolean removed = false;
		if (siblingsVisitor.getRemoveUnusedMethods()
				&& ModifierSet.isPrivate(n.getModifiers())) {
			List<SymbolReference> usages = n.getUsages();
			if (usages == null || usages.isEmpty()) {
				it.remove();
				removed = true;
				removeOrphanBodyReferences(it, n);
			} else {
				n.accept(siblingsVisitor, null);
			}
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}
	

	@Override
	public Boolean visit(FieldDeclaration n, Iterator<? extends Node> it) {
		boolean removed = false;
		if (siblingsVisitor.getRemoveUnusedFields()
				&& ModifierSet.isPrivate(n.getModifiers())) {
			List<SymbolReference> usages = n.getUsages();
			if (usages == null || usages.isEmpty()) {
				it.remove();
				removeOrphanBodyReferences(it, n);
				removed = true;
			} else {
				n.accept(siblingsVisitor, null);
			}
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}

	@Override
	public Boolean visit(ImportDeclaration n, Iterator<? extends Node> it) {
		boolean removed = false;
		List<SymbolReference> usages = n.getUsages();
		if (siblingsVisitor.getRemoveUnusedImports() && usages == null
				|| usages.isEmpty()) {
			it.remove();
			removed = true;
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}

	@Override
	public Boolean visit(VariableDeclarator n, Iterator<? extends Node> it) {
		boolean removed = false;
		List<SymbolReference> usages = n.getUsages();
		if (siblingsVisitor.getRemoveUnusedVariables() && usages == null
				|| usages.isEmpty()) {
			it.remove();
			removed = true;
			removeOrphanBodyReferences(it, n);
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}

	public void removeOrphanBodyReferences(Iterator<? extends Node> it,
			SymbolDefinition n) {

		List<SymbolReference> references = n.getBodyReferences();
		if (references != null) {
			Iterator<SymbolReference> refsIt = references.iterator();
			while (refsIt.hasNext()) {
				SymbolReference sr = refsIt.next();
				// if the definition has produced the reference, we update the
				// body references list
				if (((Node) n).contains((Node) sr)) {
					refsIt.remove();
					SymbolDefinition def = sr.getSymbolDefinition();
					def.getUsages().remove(sr);
					((Node) def).getParentNode().accept(siblingsVisitor, null);
					return;
				}
			}
		}
	}

}
