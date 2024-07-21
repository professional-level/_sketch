package com.example.sketch.openapi

import com.example.sketch.`interface`.AbstractReactiveRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Entity
@Table(name = "api_token")
class OpenApiToken(
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "access_token")
    val accessToken: String,
    @Column(name = "created_at")
    val createdAt: LocalDateTime,
    @Column(name = "expired_at")
    val expiredAt: LocalDateTime?, // 만료의 경우 null 이면, 만료가 아님
)

@ApplicationScoped
@Repository
class OpenApiTokenRepository : AbstractReactiveRepository<OpenApiToken, Long>()
