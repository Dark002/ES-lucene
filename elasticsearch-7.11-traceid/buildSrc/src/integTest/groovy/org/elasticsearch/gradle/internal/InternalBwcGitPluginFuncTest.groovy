/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal

import org.elasticsearch.gradle.fixtures.AbstractGitAwareGradleFuncTest
import org.gradle.testkit.runner.TaskOutcome

class InternalBwcGitPluginFuncTest extends AbstractGitAwareGradleFuncTest {

    def setup() {
        internalBuild()
        buildFile << """
            import org.elasticsearch.gradle.Version;
            apply plugin: org.elasticsearch.gradle.internal.InternalBwcGitPlugin

            bwcGitConfig {
                 bwcVersion = project.provider { Version.fromString("8.1.0") }
                 bwcBranch = project.provider { "8.0" }
                 checkoutDir = project.provider{file("build/checkout")}
            }
        """
    }

    def "current repository can be cloned"() {
        when:
        def result = gradleRunner("createClone", '--stacktrace').build()
        then:
        result.task(":createClone").outcome == TaskOutcome.SUCCESS
        file("cloned/build/checkout/build.gradle").exists()
        file("cloned/build/checkout/settings.gradle").exists()
    }

    def "can resolve checkout folder as project artifact"() {
        given:
        settingsFile << "include ':consumer'"
        file("cloned/consumer/build.gradle") << """
            configurations {
                consumeCheckout
            }
            dependencies {
                consumeCheckout project(path:":", configuration: "checkout")
            }

            tasks.register("register") {
                dependsOn configurations.consumeCheckout
                doLast {
                    configurations.consumeCheckout.files.each {
                        println "checkoutDir artifact: " + it
                    }
                }
            }
        """
        when:
        def result = gradleRunner(":consumer:register", '--stacktrace', "-DtestRemoteRepo=" + remoteGitRepo,
                "-Dbwc.remote=origin").build()
        then:
        result.task(":checkoutBwcBranch").outcome == TaskOutcome.SUCCESS
        result.task(":consumer:register").outcome == TaskOutcome.SUCCESS
        normalized(result.output).contains("/cloned/build/checkout")
    }
}
