package com.sandeeprathore.reviewagent;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the layering honest as the codebase grows: the review domain stays framework-free, and
 * orchestration never reaches into web or SCM adapters.
 */
@AnalyzeClasses(packages = "com.sandeeprathore.reviewagent", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domainIsFrameworkFree = noClasses().that()
		.resideInAPackage("..reviewagent.review..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "..reviewagent.llm..", "..reviewagent.orchestration..",
				"..reviewagent.api..", "..reviewagent.scm..")
		.because("diff parsing and finding validation must be testable and reusable without Spring or a model");

	@ArchTest
	static final ArchRule orchestrationDoesNotDependOnAdapters = noClasses().that()
		.resideInAPackage("..reviewagent.orchestration..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("..reviewagent.api..", "..reviewagent.scm..", "org.springframework.web..")
		.because("the review workflow must not care whether a change came from GitHub, a local patch or an eval");

}
