package com.example.stocksearchservice.domain.event

import com.example.stocksearchservice.application.message.DomainEventDispatcher
import kotlinx.coroutines.reactor.mono
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface DomainEvent {
    val id: UUID // TODO: FriendlyUuid나 다른 최적화 id 적용 필요
    val occurredAt: ZonedDateTime
}

interface EventSupportedEntity {
    val events: MutableList<DomainEvent>
    fun complete()
}

data class StrategiesSavedEvent(
    val stockId: String,
    val savedAt: ZonedDateTime,
    val type: StrategyType,
) : DomainEvent {
    override val id: UUID = UUID.randomUUID()
    override val occurredAt: ZonedDateTime = ZonedDateTime.now()
}

enum class StrategyType {
    FinalPriceBatingStrategyV1,
}

// Aspect적 문제를 해결하기 위한 AOP
// Entity의 complete() 메서드를 save직전에 호출하도록 한다
// AOP의 위치는 결론적으로 domain이 아닌 application 패키지에 있어야 할 듯
@Aspect
@Component
class CompleteEntityAspect(
    private val domainEventDispatcher: DomainEventDispatcher,
) {
    // Event 발행의 순서가.. save 이후에 하는것이 맞을까?
    // after에 건다면, save과정에서 에러 발생 시, AOP관련 로직이 동작하는가? 동작하지 않도록 하려면?
//    @Before("execution(* com.example.stocksearchservice.application.repository.*.save(..))") // 표현식 점검
    @Before("@within(com.example.stocksearchservice.domain.event.EventPublishingRepository) && execution(* save(..))")
    fun beforeSave(joinPoint: JoinPoint) {
        val entity = joinPoint.args[0]
        if (entity is EventSupportedEntity) {
            entity.complete()
        }
    }

//    // TODO: 현재 적용이 되지 않으니 확인 필요, 또한 실질적으로 필요없는 상태 saveAll()이 save()를 호출하므로
// //    @Before("execution(* com.example.stocksearchservice.application.repository.*.saveAll(..))")
//    @Before("@within(com.example.stocksearchservice.domain.event.EventPublishingRepository) && execution(* saveAll(..))")
//    fun beforeSaveAll(joinPoint: JoinPoint) {
//        val entities = joinPoint.args[0]
//        if (entities is Iterable<*>) {
//            entities.forEach { entity ->
//                if (entity is EventSupportedEntity) {
//                    entity.complete()
//                }
//            }
//        }
//    }

    // AfterReturning advice to dispatch events after single entity save
    @AfterReturning(
        pointcut = "@within(com.example.stocksearchservice.domain.event.EventPublishingRepository) && execution(* save(..))",
        returning = "result",
    )
    fun afterSave(joinPoint: JoinPoint, result: Any?) {
//        if (result is EventSupportedEntity) {
//            val events = result.events.toList() // Immutable copy to prevent concurrent modification
//            if (events.isNotEmpty()) {
//                domainEventDispatcher.dispatch(events)
//                result.events.clear()
//            }
//        }
        val entity = joinPoint.args[0]
        if (entity is EventSupportedEntity && entity.events.isNotEmpty()) {
            domainEventDispatcher.dispatch(entity.events)
            entity.events.clear()
        }
    }

//    // AfterReturning advice to dispatch events after saveAll
//    @AfterReturning(
//        pointcut = "execution(* com.example.stocksearchservice.application.repository.*.saveAll(..))",
//        returning = "result",
//    )
//    suspend fun afterSaveAll(joinPoint: JoinPoint, result: Any?) {
//        if (result is Iterable<*>) {
//            val allEvents = mutableListOf<com.example.stocksearchservice.domain.event.DomainEvent>()
//            result.forEach { entity ->
//                if (entity is EventSupportedEntity) {
//                    allEvents.addAll(entity.events)
//                    entity.events.clear()
//                }
//            }
//            if (allEvents.isNotEmpty()) {
//                domainEventDispatcher.dispatch(allEvents)
//            }
//        }
//    }

    //
    // TODO: 추후 reactive 적용
//    @Around("@within(com.example.stocksearchservice.domain.event.EventPublishingRepository) && execution(* save(..))")
//    fun aroundSave(joinPoint: ProceedingJoinPoint): Any? {
//        val result = joinPoint.proceed()
//
//        when (result) {
//            is Mono<*> -> {
//                (result as Mono<EventSupportedEntity>).doOnSuccess { entity ->
//                    if (entity.events.isNotEmpty()) {
//                        logger.info("Dispatching events for entity: ${entity.id}")
//                        domainEventDispatcher.dispatch(entity.events)
//                        entity.events.clear()
//                    }
//                }
//            }
//            is EventSupportedEntity -> {
//                if (result.events.isNotEmpty()) {
//                    logger.info("Dispatching events for entity: ${result.id}")
//                    domainEventDispatcher.dispatch(result.events)
//                    result.events.clear()
//                }
//            }
//            else -> logger.warn("Unexpected return type from save method: ${result?.javaClass}")
//        }
//
//        return result
//    }
}

/*TODO: 추후 비동기 로직 적용 필요*/
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventPublishingRepository

val ProceedingJoinPoint.coroutineArgs: Array<Any?>
    get() = this.args.sliceArray(0 until this.args.size - 1)

suspend fun ProceedingJoinPoint.proceedCoroutine(
    args: Array<Any?> = this.coroutineArgs,
): Any? {
    return suspendCoroutine { continuation ->
        try {
            val result = this.proceed(args + continuation) // 마지막 인자에 continuation 정보 추가
            continuation.resume(result)
        } catch (ex: Throwable) {
            continuation.resumeWithException(ex)
        }
    }
}

fun ProceedingJoinPoint.runCoroutine(
    block: suspend () -> Any?,
): Mono<*> {
    return mono {
        block()
    }
}
