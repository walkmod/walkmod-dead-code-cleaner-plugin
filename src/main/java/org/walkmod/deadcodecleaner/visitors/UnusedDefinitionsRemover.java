/*
 Copyright (C) 2015 Raquel Pau and Albert Coroleu.
 
Walkmod is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Walkmod is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with Walkmod.  If not, see <http://www.gnu.org/licenses/>.*/
package org.walkmod.deadcodecleaner.visitors;

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.SymbolData;
import org.walkmod.javalang.ast.SymbolDataAware;
import org.walkmod.javalang.ast.SymbolDefinition;
import org.walkmod.javalang.ast.SymbolReference;
import org.walkmod.javalang.ast.body.AnnotationDeclaration;
import org.walkmod.javalang.ast.body.BodyDeclaration;
import org.walkmod.javalang.ast.body.ClassOrInterfaceDeclaration;
import org.walkmod.javalang.ast.body.EmptyTypeDeclaration;
import org.walkmod.javalang.ast.body.EnumDeclaration;
import org.walkmod.javalang.ast.body.FieldDeclaration;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalang.ast.body.ModifierSet;
import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.body.VariableDeclarator;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.visitors.GenericVisitorAdapter;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;

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
		if (siblingsVisitor.getRemoveUnusedEnumerations()) {
			return visitTypeDeclaration(n, it);
		} else {
			n.accept(siblingsVisitor, null);
			return false;
		}
	}

	public Boolean visit(AnnotationDeclaration n, Iterator<? extends Node> it) {
		if (siblingsVisitor.getRemoveUnusedAnnotationTypes()) {
			return visitTypeDeclaration(n, it);
		} else {
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
				boolean belongsToSerializableOrExternalizable = belongsToClass(n, Serializable.class);
				boolean containsAnSerializableMethod = false;
				if (belongsToSerializableOrExternalizable) {
					String name = n.getName();
					List<Parameter> params = n.getParameters();
					if ((name.equals("readResolve") || name
							.equals("readObjectNoData") || name.equals("writeReplace"))
							&& (params == null || params.isEmpty())) {
						containsAnSerializableMethod = true;
					} else if (name.equals("readObject") && params != null
							&& params.size() == 1) {
						SymbolData sd = params.get(0).getSymbolData();
						if (sd != null) {
							containsAnSerializableMethod = sd.getClazz()
									.equals(ObjectInputStream.class);
						}
					}
					else if(name.equals("writeObject")&& params != null
							&& params.size() == 1){
						SymbolData sd = params.get(0).getSymbolData();
						if (sd != null) {
							containsAnSerializableMethod = sd.getClazz()
									.equals(ObjectOutputStream.class);
						}
					}
				}
				else{
					belongsToSerializableOrExternalizable = belongsToClass(n, Externalizable.class);
					String name = n.getName();
					List<Parameter> params = n.getParameters();
					if ((name.equals("readResolve") || name.equals("writeReplace"))
							&& (params == null || params.isEmpty())) {
						containsAnSerializableMethod = true;
					}
				}
				boolean canBeRemoved = !(belongsToSerializableOrExternalizable && containsAnSerializableMethod);
				if (canBeRemoved) {
					it.remove();
					removed = true;
					removeOrphanBodyReferences(it, n);
				}
			} else {
				n.accept(siblingsVisitor, null);
			}
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}

	private boolean belongsToClass(BodyDeclaration n, Class<?> clazz) {
		boolean belongsToSerializable = false;
		Node grandparent = n.getParentNode();
		if (grandparent instanceof SymbolDataAware<?>) {
			SymbolData sd = ((SymbolDataAware<?>) grandparent).getSymbolData();
			if (sd != null) {
				belongsToSerializable = clazz.isAssignableFrom(sd
						.getClazz());
			}
		}
		return belongsToSerializable;
	}

	@Override
	public Boolean visit(FieldDeclaration n, Iterator<? extends Node> it) {
		boolean removed = false;
		if (siblingsVisitor.getRemoveUnusedFields()
				&& ModifierSet.isPrivate(n.getModifiers())) {
			List<SymbolReference> usages = n.getUsages();
			if (usages == null || usages.isEmpty()) {
				boolean belongsToSerializable = belongsToClass(n, Serializable.class);
				boolean hasSerialVersionUID = false;
				List<VariableDeclarator> vds = n.getVariables();
				if (vds != null) {
					Iterator<VariableDeclarator> itV = vds.iterator();

					while (itV.hasNext() && !hasSerialVersionUID) {
						hasSerialVersionUID = itV.next().getId().getName()
								.equals("serialVersionUID");
					}
				}
				boolean canBeRemoved = !(belongsToSerializable && hasSerialVersionUID);
				if (canBeRemoved) {
					it.remove();
					removeOrphanBodyReferences(it, n);
					removed = true;
				} else {
					n.accept(siblingsVisitor, null);
				}
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
			Node parent = n.getParentNode();
			boolean belongsToSerializable = false;
			if (parent instanceof FieldDeclaration) {
				belongsToSerializable = belongsToClass((FieldDeclaration) parent, Serializable.class);
			}

			boolean canBeRemoved = !(belongsToSerializable && n.getId()
					.getName().equals("serialVersionUID"));
			if (canBeRemoved && n.getInit() != null) {
				Set<MethodCallExpr> ctx = new HashSet<MethodCallExpr>();
				VoidVisitorAdapter<Set<MethodCallExpr>> v = new VoidVisitorAdapter<Set<MethodCallExpr>>() {
					@Override
					public void visit(MethodCallExpr n, Set<MethodCallExpr> ctx) {
						ctx.add(n);
					}
				};
				n.getInit().accept(v, ctx);
				canBeRemoved = ctx.isEmpty();
			}
			if (canBeRemoved) {
				it.remove();
				removed = true;
				removeOrphanBodyReferences(it, n);
			}
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
					refsIt = references.iterator();
				}
			}
		}
	}

}
