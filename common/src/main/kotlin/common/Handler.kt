package common

// Handler
interface Handler<C, T> {
    fun execute(context: C): T
}

// Context
interface HandlerContext<C>
interface Command<C> : HandlerContext<C>
interface Query<C> : HandlerContext<C>
object UnitQuery : Query<UnitQuery> // TODO: UnitQuery가 아닌 다른 방법으로 Unit을 표현하고 싶다.
//
