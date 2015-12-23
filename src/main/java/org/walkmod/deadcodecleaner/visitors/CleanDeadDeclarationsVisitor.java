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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.SymbolDefinition;
import org.walkmod.javalang.ast.SymbolReference;
import org.walkmod.javalang.ast.body.AnnotationDeclaration;
import org.walkmod.javalang.ast.body.BodyDeclaration;
import org.walkmod.javalang.ast.body.ClassOrInterfaceDeclaration;
import org.walkmod.javalang.ast.body.EnumConstantDeclaration;
import org.walkmod.javalang.ast.body.EnumDeclaration;
import org.walkmod.javalang.ast.body.FieldDeclaration;
import org.walkmod.javalang.ast.body.ModifierSet;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.body.VariableDeclarator;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.expr.VariableDeclarationExpr;
import org.walkmod.javalang.ast.stmt.BlockStmt;
import org.walkmod.javalang.ast.stmt.Statement;
import org.walkmod.javalang.ast.stmt.TypeDeclarationStmt;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.ast.type.Type;
import org.walkmod.javalang.compiler.symbols.RequiresSemanticAnalysis;
import org.walkmod.javalang.javadoclinks.JavadocLinkParser;
import org.walkmod.javalang.javadoclinks.MethodLink;
import org.walkmod.javalang.javadoclinks.ParseException;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;

import com.alibaba.fastjson.JSONArray;

@RequiresSemanticAnalysis
public class CleanDeadDeclarationsVisitor<T> extends VoidVisitorAdapter<T> {

	private Boolean removeUnusedImports = true;

	private Boolean removeUnusedVariables = true;

	private Boolean removeUnusedClasses = true;

	private Boolean removeUnusedInterfaces = true;

	private Boolean removeUnusedAnnotationTypes = true;

	private Boolean removeUnusedEnumerations = true;

	private Boolean removeUnusedMethods = true;

	private Boolean removeUnusedFields = true;

	private Boolean ignoreSerializableMethods = false;

	private Map<String, List<MethodLink>> excludedMethods = new HashMap<String, List<MethodLink>>();

	private UnusedDefinitionsRemover remover = new UnusedDefinitionsRemover(this);

	@Override
	public void visit(CompilationUnit n, T arg) {
		List<ImportDeclaration> imports = n.getImports();
		if (imports != null && removeUnusedImports) {
			Iterator<ImportDeclaration> it = imports.iterator();
			while (it.hasNext()) {
				ImportDeclaration id = it.next();
				id.accept(remover, it);
			}
		}
		List<TypeDeclaration> types = n.getTypes();
		if (types != null) {
			Iterator<TypeDeclaration> it = types.iterator();
			while (it.hasNext()) {
				TypeDeclaration td = it.next();
				if (removeUnusedClasses) {
					td.accept(remover, it);
				} else {
					td.accept(this, arg);
				}
			}
		}
	}
	
	public boolean isExcluded(Method method){
		if(method != null){
			List<MethodLink> candidates = excludedMethods.get(method.getName());
			if(candidates != null){
				Iterator<MethodLink> it = candidates.iterator();
				boolean selected = false;
				while(it.hasNext() && !selected){
					MethodLink next = it.next();
					String className = next.getClassName();
					Class<?> clazz = method.getDeclaringClass();
					while(clazz != null && clazz.isAnonymousClass()){
						clazz = clazz.getSuperclass();
					}
					selected = !"".equals(className) && clazz != null && clazz.getName().equals(className);
					if(selected){
						Class<?>[] params = method.getParameterTypes();
						List<String> args = next.getArguments();
						if(params.length == args.size()){
							Iterator<String> itArgs = args.iterator();
							int i = 0;
							while(itArgs.hasNext() && selected){
								String arg = itArgs.next();
								selected = params[i].getName().equals(arg);
								i++;
							}
						}
						else{
							selected = false;
						}
					}
					return selected;
				}
				
			}
		}
		return false;
	}

	public void setExcludedMethods(JSONArray jsonArray) {
		Iterator<Object> it = jsonArray.iterator();
		while (it.hasNext()) {
			String methodRef = it.next().toString();

			try {
				MethodLink ml = JavadocLinkParser.parse(methodRef);
				List<MethodLink> methods = excludedMethods.get(ml.getName());
				if(methods == null){
					methods = new LinkedList<MethodLink>();
				}
				methods.add(ml);
				excludedMethods.put(ml.getName(), methods);

			} catch (ParseException e) {
				throw new RuntimeException("Error parsing " + methodRef, e);
			}

		}
	}

	public void visit(AnnotationDeclaration n, T arg) {
		analyzeTypeDeclaration(n, arg);
	}

	public void visit(EnumDeclaration n, T arg) {
		analyzeTypeDeclaration(n, arg);
	}

	public void visit(TypeDeclarationStmt n, T arg) {

		TypeDeclaration td = n.getTypeDeclaration();
		td.accept(this, arg);
		List<SymbolReference> usages = td.getUsages();
		if (usages == null || usages.isEmpty()) {
			Node stmt = n.getParentNode();

			if (stmt instanceof BlockStmt) {

				BlockStmt block = (BlockStmt) stmt;
				List<Statement> list = new LinkedList<Statement>(block.getStmts());
				Iterator<Statement> it2 = list.iterator();
				while (it2.hasNext()) {
					if (it2.next() == n) {
						it2.remove();
					}
				}
				block.setStmts(list);

			}
		}
	}

	public void analyzeTypeDeclaration(TypeDeclaration n, T arg) {
		List<BodyDeclaration> members = n.getMembers();
		if (members != null) {
			Iterator<BodyDeclaration> it = members.iterator();

			while (it.hasNext()) {
				BodyDeclaration current = it.next();
				int size = members.size();
				if (current instanceof SymbolDefinition) {
					current.accept(remover, it);

				} else {
					current.accept(this, arg);
				}
				if (members.size() != size) {
					it = members.iterator();
				}

			}
		}
	}

	public void visit(ClassOrInterfaceDeclaration n, T arg) {
		analyzeTypeDeclaration(n, arg);
	}

	public void visit(FieldDeclaration n, T arg) {
		boolean isPrivate = ModifierSet.isPrivate(n.getModifiers());
		List<VariableDeclarator> vars = n.getVariables();
		if (vars != null) {
			Iterator<VariableDeclarator> it = vars.iterator();
			while (it.hasNext()) {
				VariableDeclarator current = it.next();
				if (isPrivate && removeUnusedFields) {
					current.accept(remover, it);
				} else {
					current.accept(this, arg);
				}
			}
			if (vars.isEmpty()) {
				Node parent = n.getParentNode();
				List<BodyDeclaration> list = null;
				if (parent instanceof TypeDeclaration) {
					list = ((TypeDeclaration) parent).getMembers();
				} else if (parent instanceof ObjectCreationExpr) {
					list = ((ObjectCreationExpr) parent).getAnonymousClassBody();
				} else if (parent instanceof EnumConstantDeclaration) {
					list = ((EnumConstantDeclaration) parent).getClassBody();
				}
				Iterator<BodyDeclaration> itB = list.iterator();
				boolean removed = false;
				while (itB.hasNext()) {
					if (itB.next() == n) {
						itB.remove();
						removed = true;
					}
				}
				if (removed) {
					Type sr = n.getType();
					if (sr != null) {
						sr.accept(typeUpdater, arg);
					}
				}
				parent.accept(this, arg);

			}
		}
	}

	public void visit(VariableDeclarationExpr n, T arg) {
		if (removeUnusedVariables) {
			List<VariableDeclarator> vars = n.getVars();
			if (vars != null) {
				Iterator<VariableDeclarator> it = vars.iterator();
				while (it.hasNext()) {
					VariableDeclarator current = it.next();
					current.accept(remover, it);
				}

				if (vars.isEmpty()) {
					Node parentNode = n.getParentNode();
					if (parentNode != null) {
						Node stmt = parentNode.getParentNode();
						if (stmt instanceof BlockStmt) {

							BlockStmt block = (BlockStmt) stmt;
							List<Statement> list = new LinkedList<Statement>(block.getStmts());
							Iterator<Statement> it2 = list.iterator();
							boolean removed = false;
							while (it2.hasNext()) {
								if (it2.next() == parentNode) {
									it2.remove();
									removed = true;
								}
							}
							block.setStmts(list);
							if (removed) {
								Type sr = n.getType();
								if (sr != null) {
									sr.accept(typeUpdater, arg);
								}
							}
						}
					}
				}
			}
		}
	}

	private TypeUpdater<T> typeUpdater = new TypeUpdater<T>(this);

	public TypeUpdater<T> getTypeUpdater() {
		return typeUpdater;
	}

	private class TypeUpdater<T> extends VoidVisitorAdapter<T> {

		CleanDeadDeclarationsVisitor<T> visitor;

		public TypeUpdater(CleanDeadDeclarationsVisitor<T> visitor) {
			this.visitor = visitor;
		}

		@Override
		public void visit(ClassOrInterfaceType n, T ctx) {
			SymbolDefinition def = n.getSymbolDefinition();
			if (def != null) {
				Node parent = ((Node) def).getParentNode();
				List<SymbolReference> usages = def.getUsages();
				Iterator<SymbolReference> it = usages.iterator();
				boolean finish = false;
				while (it.hasNext() && !finish) {
					SymbolReference ref = it.next();
					if (ref == n) {
						it.remove();
						if (parent != null) {
							parent.accept(visitor, ctx);
						}
						finish = true;
					}
				}
			}

		}
	}

	public Boolean getRemoveUnusedImports() {
		return removeUnusedImports;
	}

	public void setRemoveUnusedImports(Boolean removeUnusedImports) {
		this.removeUnusedImports = removeUnusedImports;
	}

	public Boolean getRemoveUnusedVariables() {
		return removeUnusedVariables;
	}

	public void setRemoveUnusedVariables(Boolean removeUnusedVariables) {
		this.removeUnusedVariables = removeUnusedVariables;
	}

	public Boolean getRemoveUnusedTypes() {
		return removeUnusedClasses;
	}

	public void setRemoveUnusedTypes(Boolean removeUnusedTypes) {
		this.removeUnusedClasses = removeUnusedTypes;
	}

	public Boolean getRemoveUnusedClasses() {
		return removeUnusedClasses;
	}

	public void setRemoveUnusedClasses(Boolean removeUnusedClasses) {
		this.removeUnusedClasses = removeUnusedClasses;
	}

	public Boolean getRemoveUnusedInterfaces() {
		return removeUnusedInterfaces;
	}

	public void setRemoveUnusedInterfaces(Boolean removeUnusedInterfaces) {
		this.removeUnusedInterfaces = removeUnusedInterfaces;
	}

	public Boolean getRemoveUnusedAnnotationTypes() {
		return removeUnusedAnnotationTypes;
	}

	public void setRemoveUnusedAnnotationTypes(Boolean removeUnusedAnnotationTypes) {
		this.removeUnusedAnnotationTypes = removeUnusedAnnotationTypes;
	}

	public Boolean getRemoveUnusedMethods() {
		return removeUnusedMethods;
	}

	public void setRemoveUnusedMethods(Boolean removeUnusedMethods) {
		this.removeUnusedMethods = removeUnusedMethods;
	}

	public Boolean getRemoveUnusedFields() {
		return removeUnusedFields;
	}

	public void setRemoveUnusedFields(Boolean removeUnusedFields) {
		this.removeUnusedFields = removeUnusedFields;
	}

	public Boolean getIgnoreSerializableMethods() {
		return ignoreSerializableMethods;
	}

	public void setIgnoreSerializableMethods(Boolean ignoreSerializableMethods) {
		this.ignoreSerializableMethods = ignoreSerializableMethods;
	}

	public Boolean getRemoveUnusedEnumerations() {
		return removeUnusedEnumerations;
	}

	public void setRemoveUnusedEnumerations(Boolean removeUnusedEnumerations) {
		this.removeUnusedEnumerations = removeUnusedEnumerations;
	}

}
