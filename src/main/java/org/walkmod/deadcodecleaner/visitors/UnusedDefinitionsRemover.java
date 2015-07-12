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
import org.walkmod.javalang.ast.expr.AnnotationExpr;
import org.walkmod.javalang.ast.expr.ArrayInitializerExpr;
import org.walkmod.javalang.ast.expr.ClassExpr;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.expr.MarkerAnnotationExpr;
import org.walkmod.javalang.ast.expr.MemberValuePair;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.NormalAnnotationExpr;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.expr.SingleMemberAnnotationExpr;
import org.walkmod.javalang.ast.expr.StringLiteralExpr;
import org.walkmod.javalang.ast.expr.VariableDeclarationExpr;
import org.walkmod.javalang.ast.type.Type;
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
				HasSupressWarning warning = new HasSupressWarning();
				Boolean containsSupressWarnings = n.accept(warning, null);
				if (containsSupressWarnings == null) {
					containsSupressWarnings = false;
				}
				if (!containsSupressWarnings) {
					it.remove();
					removed = true;
					removeOrphanBodyReferences(n);
				}
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
				boolean belongsToSerializableOrExternalizable = belongsToClass(
						n, Serializable.class);
				boolean containsAnSerializableMethod = false;
				HasSupressWarning warning = new HasSupressWarning();
				Boolean containsSupressWarnings = n.accept(warning, null);
				if (containsSupressWarnings == null) {
					containsSupressWarnings = false;
				}
				if (belongsToSerializableOrExternalizable) {
					String name = n.getName();
					List<Parameter> params = n.getParameters();
					if ((name.equals("readResolve")
							|| name.equals("readObjectNoData") || name
								.equals("writeReplace"))
							&& (params == null || params.isEmpty())) {
						containsAnSerializableMethod = true;
					} else if (name.equals("readObject") && params != null
							&& params.size() == 1) {
						SymbolData sd = params.get(0).getSymbolData();
						if (sd != null) {
							containsAnSerializableMethod = sd.getClazz()
									.equals(ObjectInputStream.class);
						}
					} else if (name.equals("writeObject") && params != null
							&& params.size() == 1) {
						SymbolData sd = params.get(0).getSymbolData();
						if (sd != null) {
							containsAnSerializableMethod = sd.getClazz()
									.equals(ObjectOutputStream.class);
						}
					}
				} else {
					belongsToSerializableOrExternalizable = belongsToClass(n,
							Externalizable.class);
					String name = n.getName();
					List<Parameter> params = n.getParameters();
					if ((name.equals("readResolve") || name
							.equals("writeReplace"))
							&& (params == null || params.isEmpty())) {
						containsAnSerializableMethod = true;
					}
				}
				boolean canBeRemoved = !(belongsToSerializableOrExternalizable && containsAnSerializableMethod);
				if (canBeRemoved && !containsSupressWarnings) {
					it.remove();
					removed = true;
					removeOrphanBodyReferences(n);
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
				belongsToSerializable = clazz.isAssignableFrom(sd.getClazz());
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
				boolean belongsToSerializable = belongsToClass(n,
						Serializable.class);
				boolean hasSerialVersionUID = false;

				boolean hasRemovableVars = true;
				HasSupressWarning warning = new HasSupressWarning();
				Boolean containsSupressWarnings = n.accept(warning, null);
				if (containsSupressWarnings == null) {
					containsSupressWarnings = false;
				}
				List<VariableDeclarator> vds = n.getVariables();
				if (vds != null) {
					Iterator<VariableDeclarator> itV = vds.iterator();

					while (itV.hasNext() && !hasSerialVersionUID
							&& hasRemovableVars) {
						VariableDeclarator vd = itV.next();

						hasSerialVersionUID = vd.getId().getName()
								.equals("serialVersionUID");
						if (!hasSerialVersionUID) {
							Set<Node> ctx = new HashSet<Node>();
							VoidVisitorAdapter<Set<Node>> v = new VoidVisitorAdapter<Set<Node>>() {
								@Override
								public void visit(MethodCallExpr n,
										Set<Node> ctx) {
									ctx.add(n);
								}

								@Override
								public void visit(ObjectCreationExpr n,
										Set<Node> ctx) {
									ctx.add(n);
								}

								@Override
								public void visit(ClassExpr n, Set<Node> ctx) {
									ctx.add(n);
								}

							};
							Expression expr = vd.getInit();
							if (expr != null) {
								expr.accept(v, ctx);
							}
							hasRemovableVars = ctx.isEmpty();
						}

					}
				}
				boolean canBeRemoved = !(belongsToSerializable
						&& hasSerialVersionUID) && hasRemovableVars;
				if (canBeRemoved && !containsSupressWarnings) {
					it.remove();
					removeOrphanBodyReferences(n);
					removed = true;
					Type sr = n.getType();
					if (sr != null) {
						sr.accept(siblingsVisitor.getTypeUpdater(), null);
					}
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

	class HasSupressWarning extends GenericVisitorAdapter<Boolean, Object> {

		public HasSupressWarning() {

		}

		@Override
		public Boolean visit(SingleMemberAnnotationExpr n, Object ctx) {
			SymbolData sd = n.getSymbolData();
			if (sd != null) {
				if (SuppressWarnings.class.isAssignableFrom(sd.getClazz())) {
					Boolean hasTheUnUnusedValue = n.getMemberValue().accept(
							this, ctx);
					if (hasTheUnUnusedValue != null) {
						return hasTheUnUnusedValue;
					} else {
						return false;
					}
				}
			}
			return false;
		}

		@Override
		public Boolean visit(NormalAnnotationExpr n, Object ctx) {
			SymbolData sd = n.getSymbolData();
			if (sd != null) {
				if (SuppressWarnings.class.isAssignableFrom(sd.getClazz())) {
					Boolean hasTheUnUnusedValue = false;
					List<MemberValuePair> list = n.getPairs();
					if (list != null) {
						Iterator<MemberValuePair> it = list.iterator();
						while (it.hasNext() && !hasTheUnUnusedValue) {
							MemberValuePair m = it.next();
							Boolean aux = m.accept(this, ctx);
							if (aux != null) {
								hasTheUnUnusedValue = aux;
							}
						}
					}

					return hasTheUnUnusedValue;

				}
			}
			return false;
		}

		@Override
		public Boolean visit(MarkerAnnotationExpr n, Object ctx) {
			return false;
		}

		@Override
		public Boolean visit(StringLiteralExpr n, Object ctx) {
			return n.getValue().equals("unused");
		}

		@Override
		public Boolean visit(ArrayInitializerExpr n, Object arg) {
			Boolean containsSupressWarnings = false;
			if (n.getValues() != null) {
				Iterator<Expression> it = n.getValues().iterator();
				while (it.hasNext() && !containsSupressWarnings) {
					Boolean aux = it.next().accept(this, arg);
					if (aux != null) {
						containsSupressWarnings = aux;
					}
				}
			}
			return containsSupressWarnings;
		}

		public Boolean visit(VariableDeclarator n, Object ctx) {
			return n.getParentNode().accept(this, ctx);
		}

		@Override
		public Boolean visit(VariableDeclarationExpr n, Object ctx) {
			Boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					if (annotation != null) {
						Boolean aux = annotation.accept(this, null);
						if (aux != null) {
							containsSupressWarnings = aux;
						}

					}
				}
			}
			return containsSupressWarnings;
		}

		@Override
		public Boolean visit(FieldDeclaration n, Object ctx) {
			boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					containsSupressWarnings = annotation.accept(this, null);
				}
			}
			return containsSupressWarnings;
		}

		@Override
		public Boolean visit(MethodDeclaration n, Object ctx) {
			boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					containsSupressWarnings = annotation.accept(this, null);
				}
			}
			return containsSupressWarnings;
		}

		@Override
		public Boolean visit(ClassOrInterfaceDeclaration n, Object ctx) {
			boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					containsSupressWarnings = annotation.accept(this, null);
				}
			}
			return containsSupressWarnings;
		}

		@Override
		public Boolean visit(EnumDeclaration n, Object ctx) {
			boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					containsSupressWarnings = annotation.accept(this, null);
				}
			}
			return containsSupressWarnings;
		}

		@Override
		public Boolean visit(AnnotationDeclaration n, Object ctx) {
			boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					containsSupressWarnings = annotation.accept(this, null);
				}
			}
			return containsSupressWarnings;
		}

		@Override
		public Boolean visit(EmptyTypeDeclaration n, Object ctx) {
			boolean containsSupressWarnings = false;
			List<AnnotationExpr> ann = n.getAnnotations();
			if (ann != null) {
				Iterator<AnnotationExpr> itAnnotations = ann.iterator();
				while (itAnnotations.hasNext() && !containsSupressWarnings) {
					AnnotationExpr annotation = itAnnotations.next();
					containsSupressWarnings = annotation.accept(this, null);
				}
			}
			return containsSupressWarnings;
		}
	}

	@Override
	public Boolean visit(VariableDeclarator n, Iterator<? extends Node> it) {
		boolean removed = false;
		List<SymbolReference> usages = n.getUsages();
		if (siblingsVisitor.getRemoveUnusedVariables() && usages == null
				|| usages.isEmpty()) {
			Node parent = n.getParentNode();
			boolean belongsToSerializable = false;
			Boolean containsSupressWarnings = false;
			if (parent instanceof FieldDeclaration) {
				belongsToSerializable = belongsToClass(
						(FieldDeclaration) parent, Serializable.class);
			}
			HasSupressWarning warning = new HasSupressWarning();
			containsSupressWarnings = n.accept(warning, null);
			if (containsSupressWarnings == null) {
				containsSupressWarnings = false;
			}
			boolean canBeRemoved = !(belongsToSerializable && n.getId()
					.getName().equals("serialVersionUID"));
			if (canBeRemoved && n.getInit() != null) {
				Set<Node> ctx = new HashSet<Node>();
				VoidVisitorAdapter<Set<Node>> v = new VoidVisitorAdapter<Set<Node>>() {
					@Override
					public void visit(MethodCallExpr n, Set<Node> ctx) {
						ctx.add(n);
					}

					@Override
					public void visit(ObjectCreationExpr n, Set<Node> ctx) {
						ctx.add(n);
					}

					@Override
					public void visit(ClassExpr n, Set<Node> ctx) {
						ctx.add(n);
					}

				};
				n.getInit().accept(v, ctx);
				canBeRemoved = ctx.isEmpty();
			}
			if (canBeRemoved && !containsSupressWarnings) {
				it.remove();
				removed = true;
				removeOrphanBodyReferences(n);
			}
		} else {
			n.accept(siblingsVisitor, null);
		}
		return removed;
	}

	public void removeOrphanBodyReferences(SymbolDefinition n) {

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
