package com.example.strategyexecutionservice.config.temporal

import com.example.strategyexecutionservice.application.temporal.StrategyExecutionTemporalActivities
import com.example.strategyexecutionservice.application.temporal.StrategyExecutionTemporalWorkflowImpl
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(StrategyExecutionTemporalProperties::class)
@ConditionalOnProperty(prefix = "akra.temporal", name = ["enabled"], havingValue = "true")
class StrategyExecutionTemporalConfiguration {

    @Bean
    fun strategyExecutionTemporalWorkflowServiceStubs(
        properties: StrategyExecutionTemporalProperties,
    ): WorkflowServiceStubs {
        val options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(properties.target)
            .build()
        return WorkflowServiceStubs.newServiceStubs(options)
    }

    @Bean
    fun strategyExecutionTemporalWorkflowClient(
        strategyExecutionTemporalWorkflowServiceStubs: WorkflowServiceStubs,
        properties: StrategyExecutionTemporalProperties,
    ): WorkflowClient {
        val options = WorkflowClientOptions.newBuilder()
            .setNamespace(properties.namespace)
            .build()
        return WorkflowClient.newInstance(strategyExecutionTemporalWorkflowServiceStubs, options)
    }

    @Bean(destroyMethod = "shutdown")
    fun strategyExecutionTemporalWorkerFactory(
        strategyExecutionTemporalWorkflowClient: WorkflowClient,
        activities: StrategyExecutionTemporalActivities,
        properties: StrategyExecutionTemporalProperties,
    ): WorkerFactory {
        val factory = WorkerFactory.newInstance(strategyExecutionTemporalWorkflowClient)
        val worker = factory.newWorker(properties.taskQueue)
        worker.registerWorkflowImplementationTypes(StrategyExecutionTemporalWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(activities)
        factory.start()
        return factory
    }
}
