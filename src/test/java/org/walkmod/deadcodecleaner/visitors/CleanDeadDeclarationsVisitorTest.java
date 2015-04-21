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

import og.walkmod.javalang.test.SemanticTest;

import org.junit.Assert;
import org.junit.Test;
import org.walkmod.deadcodecleaner.visitors.CleanDeadDeclarationsVisitor;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.body.MethodDeclaration;


public class CleanDeadDeclarationsVisitorTest extends SemanticTest {

	@Test
	public void testRemoveUnusedMethods() throws Exception {

		CompilationUnit cu = compile("public class Foo { private void bar(){} }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getTypes().get(0).getMembers().isEmpty());
	}

	@Test
	public void testRemoveUnusedMethods1() throws Exception {

		CompilationUnit cu = compile("public class Foo { private void bar(){} private String getName() { return \"name\";}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getTypes().get(0).getMembers().isEmpty());
	}

	@Test
	public void testRemoveUnusedMethods2() throws Exception {

		CompilationUnit cu = compile("public class Foo { private void bar(){} public String getName() { return \"name\";}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertEquals(1, cu.getTypes().get(0).getMembers().size());
	}

	@Test
	public void testRemoveUnusedMethods3() throws Exception {
		CompilationUnit cu = compile("public class Foo { private void bar(){} public String getName() { bar(); return \"name\";}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertEquals(2, cu.getTypes().get(0).getMembers().size());
	}

	@Test
	public void testRemoveUnusedMethods4() throws Exception {
		CompilationUnit cu = compile("public class Foo { private void bar(String s){} public String getName() { bar(null); return \"name\";}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertEquals(2, cu.getTypes().get(0).getMembers().size());
	}
	
	@Test
	public void testRemoveRecursiveMethods() throws Exception{
		CompilationUnit cu = compile("public class Foo { private void bar(){ foo(); } private void foo(){ zzz(); } private void zzz(){}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertEquals(0, cu.getTypes().get(0).getMembers().size());
	}

	@Test
	public void testRemoveUnusedFields() throws Exception {
		CompilationUnit cu = compile("public class Foo { private String bar; }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getTypes().get(0).getMembers().isEmpty());
	}

	@Test
	public void testRemoveUnusedFields1() throws Exception {
		CompilationUnit cu = compile("public class Foo { private String bar; public String getBar(){ return bar; }}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertEquals(2, cu.getTypes().get(0).getMembers().size());
	}

	@Test
	public void testRemoveUnusedTypes() throws Exception {
		CompilationUnit cu = compile("public class Foo { private class Bar{} }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getTypes().get(0).getMembers().isEmpty());
	}

	@Test
	public void testRemoveUnusedVariables() throws Exception {
		CompilationUnit cu = compile("public class Foo { public void bar(){ int i;} }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		Assert.assertTrue(md.getBody().getStmts().isEmpty());

	}

	@Test
	public void testRemoveUnusedImports() throws Exception {
		CompilationUnit cu = compile("import java.util.List; public class Foo {  }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getImports().isEmpty());
	}
	

	@Test
	public void testStaticImportsWithWildcard() throws Exception {
		CompilationUnit cu = compile("import static java.lang.Math.*; public class HelloWorld { private double compute = PI; private double foo() { return (PI * pow(2.5,2));} }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getTypes().get(0).getMembers().isEmpty());
		Assert.assertTrue(!cu.getImports().isEmpty());
	}

	@Test
	public void testStaticImportsWithSpecificMember() throws Exception {
		CompilationUnit cu = compile("import static java.lang.Math.PI; public class HelloWorld { public double compute = PI; private double foo() { return (PI * 2);} }");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(!cu.getImports().isEmpty());
	}

	@Test
	public void testImportsOfAnnotations() throws Exception {
		CompilationUnit cu = compile("import javax.annotation.Generated; @Generated(value=\"WALKMOD\") public class Foo {}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(!cu.getImports().isEmpty());
		cu = compile("import javax.annotation.Generated; public class Foo {}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getImports().isEmpty());
	}

	@Test
	public void testImportsOfJavadocTypes() throws Exception {
		String javadoc = " Returns an ordering that compares objects according to the order "
				+ "in which they appear in the\n"
				+ "given list. Only objects present in the list (according to {@link Object#equals}) may be\n"
				+ "compared. This comparator imposes a \"partial ordering\" over the type {@code T}. Subsequent\n"
				+ "changes to the {@code valuesInOrder} list will have no effect on the returned comparator. Null\n"
				+ "values in the list are not supported.\n\n"
				+ "<p>\n"
				+ "The returned comparator throws an {@link ClassCastException} when it receives an input\n"
				+ "parameter that isn't among the provided values.\n\n"
				+ "<p>\n*"
				+ "The generated comparator is serializable if all the provided values are serializable.\n"
				+

				" @param valuesInOrder the values that the returned comparator will be able to compare, in the\n"
				+ "order the comparator should induce\n"
				+ " @return the comparator described above\n"
				+ " @throws NullPointerException if any of the provided values is null\n"
				+ " @throws IllegalArgumentException if {@code valuesInOrder} contains any duplicate values\n"
				+ " (according to {@link Object#equals})\n*";

		String code = "public class Foo { /**" + javadoc
				+ "/ public void foo(){}}";
		compile(code);

		javadoc = "This class provides a skeletal implementation of the {@code Cache} interface to minimize the\n"
				+ " effort required to implement this interface.\n\n"
				+ " <p>\n"
				+ " To implement a cache, the programmer needs only to extend this class and provide an\n"
				+ " implementation for the {@link #get(Object)} and {@link #getIfPresent} methods.\n"
				+ " {@link #getUnchecked}, {@link #get(Object, Callable)}, and {@link #getAll} are implemented in\n"
				+ " terms of {@code get}; {@link #getAllPresent} is implemented in terms of {@code getIfPresent};\n"
				+ " {@link #putAll} is implemented in terms of {@link #put}, {@link #invalidateAll(Iterable)} is\n"
				+ " implemented in terms of {@link #invalidate}. The method {@link #cleanUp} is a no-op. All other\n"
				+ " methods throw an {@link UnsupportedOperationException}.";

		code = "import java.util.concurrent.Callable; public class Foo {/**"
				+ javadoc + "*/ public void foo(){}}";
		CompilationUnit cu = compile(code);
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(!cu.getImports().isEmpty());

		javadoc = "Returns a comparator that compares two arrays of unsigned {@code int} values lexicographically.\n"
				+ " That is, it compares, using {@link #compare(int, int)}), the first pair of values that follow\n"
				+ " any common prefix, or when one array is a prefix of the other, treats the shorter array as the\n"
				+ " lesser. For example, {@code [] < [1] < [1, 2] < [2] < [1 << 31]}.\n"
				+

				" <p>\n"
				+ " The returned comparator is inconsistent with {@link Object#equals(Object)} (since arrays\n"
				+ " support only identity equality), but it is consistent with {@link Arrays#equals(int[], int[])}.\n"
				+

				" @see <a href=\"http://en.wikipedia.org/wiki/Lexicographical_order\"> Lexicographical order\n"
				+ "      article at Wikipedia</a>\n";
		code = "import java.util.Arrays; public class Foo {/**" + javadoc
				+ "*/ public void foo(){}}";
		cu = compile(code);
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(!cu.getImports().isEmpty());
	}
	
	@Test
	public void testRemoveRecursiveFields() throws Exception{
		CompilationUnit cu = compile("public class Foo{ private String name =\"Rachel\"; private String surname=name+\" Pau\";}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(cu.getTypes().get(0).getMembers().isEmpty());
	}

	@Test
	public void testFieldDependencies() throws Exception{
		CompilationUnit cu = compile("public class Foo{ private String name =\"Rachel\"; public String surname=name+\" Pau\";}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		Assert.assertTrue(!cu.getTypes().get(0).getMembers().isEmpty());
	}
	
	@Test
	public void testRemoveRecursiveVariables() throws Exception{
		CompilationUnit cu = compile("public class Foo{ public void bar() { int x = 0; int y = x; }}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		MethodDeclaration md = (MethodDeclaration)cu.getTypes().get(0).getMembers().get(0);
		Assert.assertTrue(md.getBody().getStmts().isEmpty());
	}
	
	@Test
	public void testVariableDependencies() throws Exception{
		CompilationUnit cu = compile("public class Foo{ public int val; public void bar() { int x = 0; int y = x; val = y;}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		MethodDeclaration md = (MethodDeclaration)cu.getTypes().get(0).getMembers().get(1);
		Assert.assertEquals(3,md.getBody().getStmts().size());
		
		cu = compile("public class Foo{ public int val; public void bar() { int x = 0; int y = x; val = x;}}");
		cu.accept(new CleanDeadDeclarationsVisitor<Object>(), null);
		md = (MethodDeclaration)cu.getTypes().get(0).getMembers().get(1);
		Assert.assertEquals(2,md.getBody().getStmts().size());
	}
	
	@Test
	public void testSettings() throws Exception{
		CompilationUnit cu = compile("import java.util.List; public class Foo{ private int val; private void bar() { int x = 0; int y = x;}}");
		CleanDeadDeclarationsVisitor<Object> visitor = new CleanDeadDeclarationsVisitor<Object>();
		visitor.setRemoveUnusedImports(false);
		visitor.setRemoveUnusedFields(false);
		visitor.setRemoveUnusedMethods(false);
		visitor.setRemoveUnusedVariables(false);
		cu.accept(visitor, null);
		Assert.assertTrue(!cu.getImports().isEmpty());
		Assert.assertEquals(2, cu.getTypes().get(0).getMembers().size());
		MethodDeclaration md = (MethodDeclaration)cu.getTypes().get(0).getMembers().get(1);
		Assert.assertEquals(2,md.getBody().getStmts().size());
	}
}
