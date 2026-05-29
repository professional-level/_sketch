package com.example.stocksearchservice.config.temporal

import com.example.stocksearchservice.application.temporal.StockSearchTemporalActivities
import com.example.stocksearchservice.application.temporal.StockSearchTemporalWorkflowImpl
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.schedules.ScheduleClient
import io.temporal.client.schedules.ScheduleClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(StockSearchTemporalProperties::class)
@ConditionalOnProperty(prefix = "akra.temporal", name = ["enabled"], havingValue = "true")
class StockSearchTemporalConfiguration {

    @Bean
    fun temporalWorkflowServiceStubs(properties: StockSearchTemporalProperties): WorkflowServiceStubs {
        val options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(properties.target)
            .build()
        return WorkflowServiceStubs.newServiceStubs(options)
    }

    @Bean
    fun temporalWorkflowClient(
        workflowServiceStubs: WorkflowServiceStubs,
        properties: StockSearchTemporalProperties,
    ): WorkflowClient {
        val options = WorkflowClientOptions.newBuilder()
            .setNamespace(properties.namespace)
            .build()
        return WorkflowClient.newInstance(workflowServiceStubs, options)
    }

    @Bean
    fun temporalScheduleClient(
        workflowServiceStubs: WorkflowServiceStubs,
        properties: StockSearchTemporalProperties,
    ): ScheduleClient {
        val options = ScheduleClientOptions.newBuilder()
            .setNamespace(properties.namespace)
            .build()
        return ScheduleClient.newInstance(workflowServiceStubs, options)
    }

    @Bean(destroyMethod = "shutdown")
    fun temporalWorkerFactory(
        workflowClient: WorkflowClient,
        activities: StockSearchTemporalActivities,
        properties: StockSearchTemporalProperties,
    ): WorkerFactory {
        val factory = WorkerFactory.newInstance(workflowClient)
        val worker = factory.newWorker(properties.taskQueue)
        worker.registerWorkflowImplementationTypes(StockSearchTemporalWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(activities)
        factory.start()
        return factory
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "akra.temporal.schedules.top-volume",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun stockSearchTemporalScheduleRegistrar(
        scheduleClient: ScheduleClient,
        properties: StockSearchTemporalProperties,
    ): StockSearchTemporalScheduleRegistrar {
        return StockSearchTemporalScheduleRegistrar(scheduleClient, properties)
    }
}
