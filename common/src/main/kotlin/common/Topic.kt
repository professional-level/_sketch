package common


// TODO: Topic과 MessageTopic이 나눠지는 현상 수정
object Topic {
    const val TOPIC1 = "topic1"
    const val TOPIC2 = "topic2"
    const val STRATEGY_SAVED = "strategy-saved"
    const val STRATEGY_EXECUTION_START_REQUESTED = "strategy-execution-start-requested"
    const val ORDER_INTENT_CREATED = "order-intent-created"
    const val ORDER_SUBMITTED = "order-submitted"
    const val ORDER_FILLED = "order-filled"
    const val ORDER_REJECTED = "order-rejected"
    const val INVALID_EVENT = "invalid-event"
    const val PURCHASE_SUCCESS = "purchase-success"
}

enum class MessageTopic(val topicName: String) {
    TOPIC1(Topic.TOPIC1),
    TOPIC2(Topic.TOPIC2),
    STRATEGY_SAVED(Topic.STRATEGY_SAVED),
    STRATEGY_EXECUTION_START_REQUESTED(Topic.STRATEGY_EXECUTION_START_REQUESTED),
    ORDER_INTENT_CREATED(Topic.ORDER_INTENT_CREATED),
    ORDER_SUBMITTED(Topic.ORDER_SUBMITTED),
    ORDER_FILLED(Topic.ORDER_FILLED),
    ORDER_REJECTED(Topic.ORDER_REJECTED),
    INVALID_EVENT(Topic.INVALID_EVENT),
    PURCHASE_SUCCESS(Topic.PURCHASE_SUCCESS),
    ;

    companion object {
        private fun from(topic: String): MessageTopic? {
            return values().find { it.topicName == topic }
        }

        fun fromOrThrow(topic: String): MessageTopic {
            return from(topic) ?: throw IllegalArgumentException("Unknown topic: $topic")
        }
    }
}

object ConsumerGroupId {
    const val PURCHASE_SERVICE = "purchase"
    const val SEARCH_SERVICE = "search"
    const val STRATEGY_EXECUTION_SERVICE = "strategy-execution"
}
