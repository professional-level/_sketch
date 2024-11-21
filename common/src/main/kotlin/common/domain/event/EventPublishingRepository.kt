package com.example.common.domain.event

/*TODO: 추후 비동기 로직 적용 필요*/
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventPublishingRepository
