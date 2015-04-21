package org.walkmod.deadcodecleaner.visitors;

import java.util.Iterator;
import java.util.List;

import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.SymbolDefinition;
import org.walkmod.javalang.ast.body.AnnotationDeclaration;
import org.walkmod.javalang.ast.body.BodyDeclaration;
import org.walkmod.javalang.ast.body.ClassOrInterfaceDeclaration;
import org.walkmod.javalang.ast.body.EnumDeclaration;
import org.walkmod.javalang.ast.body.FieldDeclaration;
import org.walkmod.javalang.ast.body.ModifierSet;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.body.VariableDeclarator;
import org.walkmod.javalang.ast.expr.VariableDeclarationExpr;
import org.walkmod.javalang.ast.stmt.BlockStmt;
import org.walkmod.javalang.compiler.symbols.RequiresSemanticAnalysis;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;

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

	private UnusedDefinitionsRemover remover = new UnusedDefinitionsRemover(
			this);

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

	public void visit(AnnotationDeclaration n, T arg) {
		analyzeTypeDeclaration(n, arg);
	}

	public void visit(EnumDeclaration n, T arg) {
		analyzeTypeDeclaration(n, arg);
	}

	public void analyzeTypeDeclaration(TypeDeclaration n, T arg) {
		List<BodyDeclaration> members = n.getMembers();
		if (members != null) {
			Iterator<BodyDeclaration> it = members.iterator();
			while (it.hasNext()) {
				BodyDeclaration current = it.next();
				if (current instanceof SymbolDefinition) {

					current.accept(remover, it);

				} else {
					current.accept(this, arg);
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
				n.getParentNode().getMembers().remove(n);
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
							if (vars.isEmpty()) {
								BlockStmt block = (BlockStmt) stmt;
								block.getStmts().remove(parentNode);
							}
						}
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

	public void setRemoveUnusedAnnotationTypes(
			Boolean removeUnusedAnnotationTypes) {
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

	public Boolean getRemoveUnusedEnumerations() {
		return removeUnusedEnumerations;
	}

	public void setRemoveUnusedEnumerations(Boolean removeUnusedEnumerations) {
		this.removeUnusedEnumerations = removeUnusedEnumerations;
	}

}
