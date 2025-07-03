package com.example.stocksearchservice.application.event

import com.example.common.domain.event.EventSupportedEntity
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component

// TODO: annotation mapping 형태로 수정하자. @EventPublishingRepository 등
@Aspect
@Component
class CompleteEntityAspect(
    private val domainEventDispatcher: DomainEventDispatcher,
) {
    // Event 발행의 순서가.. save 이후에 하는것이 맞을까?
    // after에 건다면, save과정에서 에러 발생 시, AOP관련 로직이 동작하는가? 동작하지 않도록 하려면?
//    @Before("execution(* com.example.stocksearchservice.application.repository.*.save(..))") // 표현식 점검
    @Before("@target(com.example.common.domain.event.EventPublishingRepository) && execution(* save(..))")
    fun beforeSave(joinPoint: JoinPoint) {
        val entity = joinPoint.args[0]
        if (entity is EventSupportedEntity) {
            entity.complete()
        }
    }


    // AfterReturning advice to dispatch events after single entity save
    @AfterReturning(
        pointcut = "@target(com.example.common.domain.event.EventPublishingRepository) && execution(* save(..))",
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


    // saveAll용 AOP - Reactive 배치 처리 + 개별 이벤트 발행
    @Around("@target(com.example.common.domain.event.EventPublishingRepository) && execution(* saveAll(..))")
    fun aroundSaveAll(joinPoint: ProceedingJoinPoint): Any? {
        val entities = joinPoint.args[0] as? List<*> ?: return joinPoint.proceed()

        // 1. 각 엔티티의 성공 이벤트 생성
        entities.filterIsInstance<EventSupportedEntity>()
            .forEach { it.complete() }

        return runCatching {
            // 2. 실제 저장 로직 실행
            joinPoint.proceed()
        }.onSuccess { result ->
            // 3. 저장 성공 시에만 이벤트 개별 발행 (비동기)
            entities.filterIsInstance<EventSupportedEntity>()
                .filter { it.events.isNotEmpty() }
                .forEach { entity ->
                    domainEventDispatcher.dispatch(entity.events)
                    entity.events.clear()
                }
        }.onFailure { exception ->
            // TODO: 저장 실패 시 실패 이벤트 발행 기능 추가 필요
            // 예: StrategySaveFailedEvent(entities.map{it.id}, exception.message, timestamp)
            // domainEventDispatcher.dispatch(failureEvents)

            // 성공 이벤트 제거 (저장 실패했으므로)
            entities.filterIsInstance<EventSupportedEntity>()
                .forEach { it.events.clear() }
        }.getOrThrow() // 실패 시 예외 재발생
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
}

