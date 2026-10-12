/*
 * Copyright 2026 the Cronbell authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.steelreed.cronbell.spring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

/**
 * Keeps the optional libraries (Quartz, ShedLock, Actuator) in their own internal
 * packages, so that an application without them never loads a class that names them, and
 * keeps internal types out of the public API.
 */
class ArchitectureTests {

	private static final String CONFIGURATION = CronbellAutoConfiguration.class.getName();

	private static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("com.steelreed.cronbell.spring");
	}

	@Test
	void onlyTheQuartzPackageUsesQuartz() {
		onlyUses("org.quartz", "quartz", CONFIGURATION + "$QuartzMonitoring");
	}

	@Test
	void onlyTheShedLockPackageUsesShedLock() {
		onlyUses("net.javacrumbs.shedlock", "shedlock", CONFIGURATION + "$ShedLockMonitoring");
	}

	@Test
	void onlyTheActuatorPackageUsesActuator() {
		onlyUses("org.springframework.boot.actuate", "actuator", CONFIGURATION + "$ActuatorInfo");
	}

	/**
	 * Checks both real dependencies and names of the library written in the class file,
	 * such as {@code @ConditionalOnClass(name = ...)} or a type looked up by name.
	 */
	private static void onlyUses(String library, String integration, String configurationClass) {
		String allowedPackage = "com.steelreed.cronbell.spring.internal." + integration;
		DescribedPredicate<JavaClass> outside = DescribedPredicate.describe(
				"are outside " + allowedPackage + " and " + configurationClass,
				(javaClass) -> !javaClass.getPackageName().equals(allowedPackage)
						&& !javaClass.getPackageName().startsWith(allowedPackage + ".")
						&& !javaClass.getName().equals(configurationClass));
		noClasses().that(outside)
			.should()
			.dependOnClassesThat()
			.resideInAPackage(library + "..")
			.because(library + " is optional")
			.check(classes);
		noClasses().that(outside)
			.should(nameInTheirClassFile(library))
			.because(library + " is optional")
			.check(classes);
	}

	private static ArchCondition<JavaClass> nameInTheirClassFile(String library) {
		Set<String> needles = Set.of(library, library.replace('.', '/'));
		return new ArchCondition<>("name " + library + " in their class file") {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				String content = classFile(javaClass);
				if (needles.stream().anyMatch(content::contains)) {
					events.add(SimpleConditionEvent.satisfied(javaClass, javaClass.getName() + " names " + library));
				}
			}
		};
	}

	private static String classFile(JavaClass javaClass) {
		String resource = "/" + javaClass.getName().replace('.', '/') + ".class";
		try (InputStream in = ArchitectureTests.class.getResourceAsStream(resource)) {
			return (in != null) ? new String(in.readAllBytes(), StandardCharsets.ISO_8859_1) : "";
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot read " + resource, ex);
		}
	}

	@Test
	void publicApiDoesNotExposeInternalTypes() {
		members().that()
			.areDeclaredInClassesThat()
			.resideInAPackage("com.steelreed.cronbell.spring")
			.and()
			.areDeclaredInClassesThat()
			.arePublic()
			.and(DescribedPredicate.describe("are public or protected",
					(JavaMember member) -> member.getModifiers().contains(JavaModifier.PUBLIC)
							|| member.getModifiers().contains(JavaModifier.PROTECTED)))
			.should(notExposeInternalTypes())
			.check(classes);
	}

	private static ArchCondition<JavaMember> notExposeInternalTypes() {
		return new ArchCondition<>("not expose internal types in their signature") {
			@Override
			public void check(JavaMember member, ConditionEvents events) {
				Stream<JavaClass> types = Stream.empty();
				if (member instanceof JavaCodeUnit codeUnit) {
					types = Stream.concat(codeUnit.getReturnType().getAllInvolvedRawTypes().stream(),
							codeUnit.getParameterTypes()
								.stream()
								.flatMap((type) -> type.getAllInvolvedRawTypes().stream()));
				}
				else if (member instanceof JavaField field) {
					types = field.getType().getAllInvolvedRawTypes().stream();
				}
				types.filter((type) -> type.getPackageName().contains(".internal"))
					.forEach((type) -> events.add(SimpleConditionEvent.violated(member,
							member.getFullName() + " exposes " + type.getName())));
			}
		};
	}

	@Test
	void noFieldInjection() {
		NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
	}

	@Test
	void noStandardStreams() {
		NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
	}

	@Test
	void noLegacyDateTypes() {
		noClasses().should()
			.dependOnClassesThat()
			.belongToAnyOf(Date.class, Calendar.class)
			.because("java.time is used throughout")
			.check(classes);
	}

}
