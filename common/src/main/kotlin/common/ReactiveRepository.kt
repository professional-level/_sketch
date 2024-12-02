package common
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.hibernate.reactive.mutiny.Mutiny
import java.lang.reflect.ParameterizedType

interface ReactiveRepository<T, ID> {
    suspend fun findById(id: ID): Uni<T?>
    suspend fun save(entity: T): Uni<T>
    suspend fun findAll(): Uni<List<T>>
    suspend fun deleteById(id: ID): Uni<Void>
}

abstract class AbstractReactiveRepository<T, ID> : ReactiveRepository<T, ID> {

    @Inject
    lateinit var sessionFactory: Mutiny.SessionFactory

    protected val entityType: Class<T> by lazy {
        (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>
    }

    override suspend fun findById(id: ID): Uni<T?> {
        return sessionFactory.withSession { session ->
            session.find(entityType, id)
        }
    }

    override suspend fun save(entity: T): Uni<T> {
        return sessionFactory.withSession { session ->
            session.persist(entity).flatMap {
                session.flush()
            }.map { entity }
        }
    }

    override suspend fun findAll(): Uni<List<T>> {
        return sessionFactory.withSession { session ->
            session.createQuery("from ${entityType.simpleName}", entityType)
                .resultList
        }
    }

    override suspend fun deleteById(id: ID): Uni<Void> {
        return sessionFactory.withSession { session ->
            session.find(entityType, id).flatMap { entity ->
                if (entity != null) {
                    session.remove(entity).flatMap {
                        session.flush()
                    }
                } else {
                    Uni.createFrom().voidItem()
                }
            }
        }
    }
}

//@ApplicationScoped
//@Repository
//class BookReactiveRepository : AbstractReactiveRepository<Book, Long>()
