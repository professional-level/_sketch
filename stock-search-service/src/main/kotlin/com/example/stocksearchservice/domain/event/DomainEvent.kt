package com.example.stocksearchservice.domain.event

import com.example.common.domain.event.DomainEvent
import org.aspectj.lang.ProceedingJoinPoint
import java.time.ZonedDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class StrategyCreatedEvent(
    val stockId: String,
    val stockName: String,
    val savedAt: ZonedDateTime,
    val type: StrategyType,
) : DomainEvent()

enum class StrategyType {
    FinalPriceBatingV1,
}

// Aspect적 문제를 해결하기 위한 AOP
// Entity의 complete() 메서드를 save직전에 호출하도록 한다
// AOP의 위치는 결론적으로 domain이 아닌 application 패키지에 있어야 할 듯

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

// fun ProceedingJoinPoint.runCoroutine(
//    block: suspend () -> Any?,
// ): Mono<*> {
//    return mono {
//        block()
//    }
// }
