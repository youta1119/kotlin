/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("compiler/fir/resolve/testData/resolve")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class FirResolveTestCaseGenerated extends AbstractFirResolveTestCase {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
    }

    public void testAllFilesPresentInResolve() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true, "stdlib", "cfg");
    }

    @TestMetadata("cast.kt")
    public void testCast() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/cast.kt");
    }

    @TestMetadata("companion.kt")
    public void testCompanion() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/companion.kt");
    }

    @TestMetadata("copy.kt")
    public void testCopy() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/copy.kt");
    }

    @TestMetadata("derivedClass.kt")
    public void testDerivedClass() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/derivedClass.kt");
    }

    @TestMetadata("enum.kt")
    public void testEnum() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/enum.kt");
    }

    @TestMetadata("extension.kt")
    public void testExtension() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/extension.kt");
    }

    @TestMetadata("F.kt")
    public void testF() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/F.kt");
    }

    @TestMetadata("fakeRecursiveSupertype.kt")
    public void testFakeRecursiveSupertype() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/fakeRecursiveSupertype.kt");
    }

    @TestMetadata("fakeRecursiveTypealias.kt")
    public void testFakeRecursiveTypealias() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/fakeRecursiveTypealias.kt");
    }

    @TestMetadata("fib.kt")
    public void testFib() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/fib.kt");
    }

    @TestMetadata("ft.kt")
    public void testFt() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/ft.kt");
    }

    @TestMetadata("functionTypes.kt")
    public void testFunctionTypes() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/functionTypes.kt");
    }

    @TestMetadata("genericFunctions.kt")
    public void testGenericFunctions() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/genericFunctions.kt");
    }

    @TestMetadata("intersectionTypes.kt")
    public void testIntersectionTypes() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/intersectionTypes.kt");
    }

    @TestMetadata("nestedClass.kt")
    public void testNestedClass() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/nestedClass.kt");
    }

    @TestMetadata("NestedOfAliasedType.kt")
    public void testNestedOfAliasedType() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/NestedOfAliasedType.kt");
    }

    @TestMetadata("nestedReturnType.kt")
    public void testNestedReturnType() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/nestedReturnType.kt");
    }

    @TestMetadata("NestedSuperType.kt")
    public void testNestedSuperType() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/NestedSuperType.kt");
    }

    @TestMetadata("recursiveCallOnWhenWithSealedClass.kt")
    public void testRecursiveCallOnWhenWithSealedClass() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/recursiveCallOnWhenWithSealedClass.kt");
    }

    @TestMetadata("simpleClass.kt")
    public void testSimpleClass() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/simpleClass.kt");
    }

    @TestMetadata("simpleTypeAlias.kt")
    public void testSimpleTypeAlias() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/simpleTypeAlias.kt");
    }

    @TestMetadata("treeSet.kt")
    public void testTreeSet() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/treeSet.kt");
    }

    @TestMetadata("tryInference.kt")
    public void testTryInference() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/tryInference.kt");
    }

    @TestMetadata("TwoDeclarationsInSameFile.kt")
    public void testTwoDeclarationsInSameFile() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/TwoDeclarationsInSameFile.kt");
    }

    @TestMetadata("typeAliasWithGeneric.kt")
    public void testTypeAliasWithGeneric() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/typeAliasWithGeneric.kt");
    }

    @TestMetadata("typeAliasWithTypeArguments.kt")
    public void testTypeAliasWithTypeArguments() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/typeAliasWithTypeArguments.kt");
    }

    @TestMetadata("typeFromGetter.kt")
    public void testTypeFromGetter() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/typeFromGetter.kt");
    }

    @TestMetadata("typeParameterInPropertyReceiver.kt")
    public void testTypeParameterInPropertyReceiver() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/typeParameterInPropertyReceiver.kt");
    }

    @TestMetadata("typeParameterVsNested.kt")
    public void testTypeParameterVsNested() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/typeParameterVsNested.kt");
    }

    @TestMetadata("whenInference.kt")
    public void testWhenInference() throws Exception {
        runTest("compiler/fir/resolve/testData/resolve/whenInference.kt");
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/arguments")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Arguments extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInArguments() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/arguments"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("default.kt")
        public void testDefault() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/arguments/default.kt");
        }

        @TestMetadata("lambda.kt")
        public void testLambda() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/arguments/lambda.kt");
        }

        @TestMetadata("simple.kt")
        public void testSimple() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/arguments/simple.kt");
        }

        @TestMetadata("vararg.kt")
        public void testVararg() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/arguments/vararg.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/builtins")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Builtins extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInBuiltins() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/builtins"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("lists.kt")
        public void testLists() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/builtins/lists.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/dfa")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Dfa extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInDfa() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/dfa"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("booleanOperators.kt")
        public void testBooleanOperators() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/dfa/booleanOperators.kt");
        }

        @TestMetadata("casts.kt")
        public void testCasts() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/dfa/casts.kt");
        }

        @TestMetadata("equalsAndIdentity.kt")
        public void testEqualsAndIdentity() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/dfa/equalsAndIdentity.kt");
        }

        @TestMetadata("nullability.kt")
        public void testNullability() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/dfa/nullability.kt");
        }

        @TestMetadata("returns.kt")
        public void testReturns() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/dfa/returns.kt");
        }

        @TestMetadata("simpleIf.kt")
        public void testSimpleIf() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/dfa/simpleIf.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/expresssions")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Expresssions extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        @TestMetadata("access.kt")
        public void testAccess() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/access.kt");
        }

        public void testAllFilesPresentInExpresssions() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/expresssions"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("checkArguments.kt")
        public void testCheckArguments() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/checkArguments.kt");
        }

        @TestMetadata("companion.kt")
        public void testCompanion() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/companion.kt");
        }

        @TestMetadata("constructor.kt")
        public void testConstructor() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/constructor.kt");
        }

        @TestMetadata("dispatchReceiver.kt")
        public void testDispatchReceiver() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/dispatchReceiver.kt");
        }

        @TestMetadata("lambda.kt")
        public void testLambda() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/lambda.kt");
        }

        @TestMetadata("localImplicitBodies.kt")
        public void testLocalImplicitBodies() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/localImplicitBodies.kt");
        }

        @TestMetadata("memberExtension.kt")
        public void testMemberExtension() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/memberExtension.kt");
        }

        @TestMetadata("objectVsProperty.kt")
        public void testObjectVsProperty() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/objectVsProperty.kt");
        }

        @TestMetadata("objects.kt")
        public void testObjects() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/objects.kt");
        }

        @TestMetadata("qualifiedExpressions.kt")
        public void testQualifiedExpressions() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/qualifiedExpressions.kt");
        }

        @TestMetadata("receiverConsistency.kt")
        public void testReceiverConsistency() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/receiverConsistency.kt");
        }

        @TestMetadata("simple.kt")
        public void testSimple() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/simple.kt");
        }

        @TestMetadata("this.kt")
        public void testThis() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/this.kt");
        }

        @TestMetadata("typeAliasConstructor.kt")
        public void testTypeAliasConstructor() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/typeAliasConstructor.kt");
        }

        @TestMetadata("vararg.kt")
        public void testVararg() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/vararg.kt");
        }

        @TestMetadata("when.kt")
        public void testWhen() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/expresssions/when.kt");
        }

        @TestMetadata("compiler/fir/resolve/testData/resolve/expresssions/inference")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class Inference extends AbstractFirResolveTestCase {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInInference() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/expresssions/inference"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
            }

            @TestMetadata("id.kt")
            public void testId() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/inference/id.kt");
            }

            @TestMetadata("typeParameters.kt")
            public void testTypeParameters() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/inference/typeParameters.kt");
            }

            @TestMetadata("typeParameters2.kt")
            public void testTypeParameters2() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/inference/typeParameters2.kt");
            }
        }

        @TestMetadata("compiler/fir/resolve/testData/resolve/expresssions/invoke")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class Invoke extends AbstractFirResolveTestCase {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInInvoke() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/expresssions/invoke"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
            }

            @TestMetadata("explicitReceiver.kt")
            public void testExplicitReceiver() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/explicitReceiver.kt");
            }

            @TestMetadata("explicitReceiver2.kt")
            public void testExplicitReceiver2() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/explicitReceiver2.kt");
            }

            @TestMetadata("extension.kt")
            public void testExtension() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/extension.kt");
            }

            @TestMetadata("farInvokeExtension.kt")
            public void testFarInvokeExtension() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/farInvokeExtension.kt");
            }

            @TestMetadata("implicitTypeOrder.kt")
            public void testImplicitTypeOrder() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/implicitTypeOrder.kt");
            }

            @TestMetadata("propertyFromParameter.kt")
            public void testPropertyFromParameter() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/propertyFromParameter.kt");
            }

            @TestMetadata("simple.kt")
            public void testSimple() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/simple.kt");
            }

            @TestMetadata("threeReceivers.kt")
            public void testThreeReceivers() throws Exception {
                runTest("compiler/fir/resolve/testData/resolve/expresssions/invoke/threeReceivers.kt");
            }
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/fromBuilder")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class FromBuilder extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInFromBuilder() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/fromBuilder"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("complexTypes.kt")
        public void testComplexTypes() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/fromBuilder/complexTypes.kt");
        }

        @TestMetadata("enums.kt")
        public void testEnums() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/fromBuilder/enums.kt");
        }

        @TestMetadata("noPrimaryConstructor.kt")
        public void testNoPrimaryConstructor() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/fromBuilder/noPrimaryConstructor.kt");
        }

        @TestMetadata("simpleClass.kt")
        public void testSimpleClass() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/fromBuilder/simpleClass.kt");
        }

        @TestMetadata("typeParameters.kt")
        public void testTypeParameters() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/fromBuilder/typeParameters.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/multifile")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Multifile extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInMultifile() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/multifile"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("Annotations.kt")
        public void testAnnotations() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/Annotations.kt");
        }

        @TestMetadata("ByteArray.kt")
        public void testByteArray() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/ByteArray.kt");
        }

        @TestMetadata("importFromObject.kt")
        public void testImportFromObject() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/importFromObject.kt");
        }

        @TestMetadata("NestedSuperType.kt")
        public void testNestedSuperType() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/NestedSuperType.kt");
        }

        @TestMetadata("sealedStarImport.kt")
        public void testSealedStarImport() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/sealedStarImport.kt");
        }

        @TestMetadata("simpleAliasedImport.kt")
        public void testSimpleAliasedImport() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/simpleAliasedImport.kt");
        }

        @TestMetadata("simpleImport.kt")
        public void testSimpleImport() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/simpleImport.kt");
        }

        @TestMetadata("simpleImportNested.kt")
        public void testSimpleImportNested() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/simpleImportNested.kt");
        }

        @TestMetadata("simpleImportOuter.kt")
        public void testSimpleImportOuter() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/simpleImportOuter.kt");
        }

        @TestMetadata("simpleStarImport.kt")
        public void testSimpleStarImport() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/simpleStarImport.kt");
        }

        @TestMetadata("TypeAliasExpansion.kt")
        public void testTypeAliasExpansion() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/multifile/TypeAliasExpansion.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/nested")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Nested extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInNested() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/nested"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("inner.kt")
        public void testInner() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/nested/inner.kt");
        }

        @TestMetadata("simple.kt")
        public void testSimple() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/nested/simple.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/overrides")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Overrides extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInOverrides() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/overrides"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("generics.kt")
        public void testGenerics() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/overrides/generics.kt");
        }

        @TestMetadata("simple.kt")
        public void testSimple() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/overrides/simple.kt");
        }

        @TestMetadata("simpleFakeOverride.kt")
        public void testSimpleFakeOverride() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/overrides/simpleFakeOverride.kt");
        }

        @TestMetadata("three.kt")
        public void testThree() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/overrides/three.kt");
        }
    }

    @TestMetadata("compiler/fir/resolve/testData/resolve/references")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class References extends AbstractFirResolveTestCase {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInReferences() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/fir/resolve/testData/resolve/references"), Pattern.compile("^([^.]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("simple.kt")
        public void testSimple() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/references/simple.kt");
        }

        @TestMetadata("superMember.kt")
        public void testSuperMember() throws Exception {
            runTest("compiler/fir/resolve/testData/resolve/references/superMember.kt");
        }
    }
}
